package ru.zhurnal.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.zhurnal.app.BuildConfig
import ru.zhurnal.app.ZhurnalApplication
import ru.zhurnal.app.data.ApiResult
import ru.zhurnal.app.data.AppUpdateDto
import ru.zhurnal.app.data.StoredSession
import ru.zhurnal.app.data.ZhurnalRepository
import ru.zhurnal.app.update.UpdateManager
import ru.zhurnal.app.widget.WidgetUpdater

sealed interface SessionState {
    data object Loading : SessionState
    data class Ready(val session: StoredSession) : SessionState
}

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

data class UpdateUiState(
    val checking: Boolean = false,
    val update: AppUpdateDto? = null,
    val downloading: Boolean = false,
    val progress: Float? = null,
    val error: String? = null,
    val needInstallPermission: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ZhurnalApplication).container
    private val store = container.sessionStore
    val repository: ZhurnalRepository = container.repository

    private val sessionChecked = MutableStateFlow(false)

    val session: StateFlow<SessionState> = combine(store.session, sessionChecked) { stored, checked ->
        // Пока сохранённая сессия не проверена на сервере, не показываем главный экран.
        if (checked || !stored.isAuthorized) SessionState.Ready(stored) else SessionState.Loading
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    private val _auth = MutableStateFlow(AuthUiState())
    val auth: StateFlow<AuthUiState> = _auth.asStateFlow()

    private val _update = MutableStateFlow(UpdateUiState())
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = store.current()
            var updateBlocks = false
            if (!stored.baseUrl.isNullOrBlank()) {
                _update.value = UpdateUiState(checking = true)
                updateBlocks = applyUpdate(repository.appUpdate())
            }
            if (!updateBlocks && stored.isAuthorized) {
                // Токен мог остаться от удалённого аккаунта или из резервной копии.
                when (val result = repository.refreshMe()) {
                    is ApiResult.Success -> Unit
                    is ApiResult.Failure -> if (result.code == 401) {
                        store.clearAuth()
                        WidgetUpdater.updateAll(getApplication())
                        _auth.value = AuthUiState(
                            error = result.message.ifBlank { "Сессия недействительна, войдите заново" },
                        )
                    }
                }
            }
            sessionChecked.value = true
        }
    }

    /** Возвращает true, если найдено обязательное обновление. */
    private fun applyUpdate(result: ApiResult<AppUpdateDto>): Boolean {
        return when (result) {
            is ApiResult.Success -> {
                if (result.data.versionCode > BuildConfig.VERSION_CODE) {
                    _update.value = UpdateUiState(update = result.data)
                    true
                } else {
                    _update.value = UpdateUiState()
                    false
                }
            }
            is ApiResult.Failure -> {
                // Если сервер не отдал сведения об обновлении, не блокируем вход.
                _update.value = UpdateUiState()
                false
            }
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            val stored = store.current()
            if (stored.baseUrl.isNullOrBlank()) {
                _update.value = UpdateUiState()
                return@launch
            }
            _update.value = UpdateUiState(checking = true)
            applyUpdate(repository.appUpdate())
        }
    }

    fun installUpdate() {
        val pending = _update.value.update ?: return
        if (_update.value.downloading) return
        viewModelScope.launch {
            _update.value = _update.value.copy(
                downloading = true,
                progress = 0f,
                error = null,
                needInstallPermission = false,
            )
            val url = repository.updateDownloadUrl(pending.apkUrl)
            if (url.isNullOrBlank()) {
                _update.value = _update.value.copy(
                    downloading = false,
                    progress = null,
                    error = "Не удалось определить адрес файла обновления",
                )
                return@launch
            }

            val downloaded = UpdateManager.download(
                getApplication(),
                url,
                pending.versionCode,
            ) { progress ->
                _update.value = _update.value.copy(progress = progress)
            }

            downloaded.onSuccess { file ->
                _update.value = _update.value.copy(downloading = false, progress = 1f)
                val installed = UpdateManager.install(getApplication(), file)
                if (installed.isFailure) {
                    _update.value = _update.value.copy(
                        error = installed.exceptionOrNull()?.message
                            ?: "Не удалось запустить установку обновления",
                        needInstallPermission = !UpdateManager.canInstall(getApplication()),
                    )
                }
            }.onFailure { error ->
                _update.value = _update.value.copy(
                    downloading = false,
                    progress = null,
                    error = error.message ?: "Не удалось скачать обновление",
                )
            }
        }
    }

    fun openInstallSettings() {
        UpdateManager.openInstallSettings(getApplication())
    }

    fun login(login: String, password: String) {
        if (login.isBlank() || password.isBlank()) {
            _auth.value = AuthUiState(error = "Введите логин и пароль")
            return
        }
        _auth.value = AuthUiState(loading = true)
        viewModelScope.launch {
            when (val result = repository.login(login, password)) {
                is ApiResult.Success -> {
                    _auth.value = AuthUiState()
                    WidgetUpdater.updateAll(getApplication())
                }
                is ApiResult.Failure -> _auth.value = AuthUiState(error = result.message)
            }
        }
    }

    fun register(fullName: String, login: String, password: String, groupCode: String) {
        when {
            fullName.trim().length < 2 -> {
                _auth.value = AuthUiState(error = "Укажите фамилию и имя")
                return
            }
            groupCode.trim().isEmpty() -> {
                _auth.value = AuthUiState(error = "Введите код группы")
                return
            }
            !Regex("^[a-zA-Z0-9._-]{3,32}$").matches(login.trim()) -> {
                _auth.value = AuthUiState(
                    error = "Логин: 3–32 символа, латиница, цифры, точка, дефис или подчёркивание",
                )
                return
            }
            password.length < 6 -> {
                _auth.value = AuthUiState(error = "Пароль должен быть не короче 6 символов")
                return
            }
        }
        _auth.value = AuthUiState(loading = true)
        viewModelScope.launch {
            when (val result = repository.register(fullName, login, password, groupCode)) {
                is ApiResult.Success -> _auth.value = AuthUiState()
                is ApiResult.Failure -> _auth.value = AuthUiState(error = result.message)
            }
        }
    }

    fun clearAuthError() {
        _auth.value = _auth.value.copy(error = null)
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun testAndSaveServer(rawUrl: String, onResult: (String?) -> Unit) {
        val normalized = repository.normalizeBaseUrl(rawUrl)
        if (normalized == null) {
            onResult("Некорректный адрес. Пример: https://school.portmap.io:12345")
            return
        }
        viewModelScope.launch {
            when (val result = repository.testConnection(normalized)) {
                is ApiResult.Success -> {
                    store.saveServer(normalized)
                    store.clearAuth()
                    WidgetUpdater.updateAll(getApplication())
                    checkForUpdate()
                    onResult(null)
                }
                is ApiResult.Failure -> onResult(result.message)
            }
        }
    }

    fun saveServerUnchecked(rawUrl: String, onResult: (String?) -> Unit) {
        val normalized = repository.normalizeBaseUrl(rawUrl)
        if (normalized == null) {
            onResult("Некорректный адрес. Пример: https://school.portmap.io:12345")
            return
        }
        viewModelScope.launch {
            store.saveServer(normalized)
            store.clearAuth()
            WidgetUpdater.updateAll(getApplication())
            checkForUpdate()
            onResult(null)
        }
    }

    fun clearServer() {
        viewModelScope.launch {
            store.clearServerAndAuth()
            _update.value = UpdateUiState()
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun updateAvatar(avatar: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.updateMyAvatar(avatar)) {
                is ApiResult.Success -> onResult(null)
                is ApiResult.Failure -> onResult(result.message)
            }
        }
    }

    fun changePassword(current: String, new: String, onResult: (String?) -> Unit) {        when {
            current.isBlank() -> {
                onResult("Введите текущий пароль")
                return
            }
            new.length < 6 -> {
                onResult("Новый пароль должен быть не короче 6 символов")
                return
            }
        }
        viewModelScope.launch {
            when (val result = repository.changePassword(current, new)) {
                is ApiResult.Success -> onResult(null)
                is ApiResult.Failure -> onResult(result.message)
            }
        }
    }
}
