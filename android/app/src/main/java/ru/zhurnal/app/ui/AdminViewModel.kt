package ru.zhurnal.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.zhurnal.app.data.ApiResult
import ru.zhurnal.app.data.GroupDto
import ru.zhurnal.app.data.SubjectDto
import ru.zhurnal.app.data.UserDto
import ru.zhurnal.app.data.ZhurnalRepository

class AdminViewModel(
    private val repository: ZhurnalRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val groups: List<GroupDto> = emptyList(),
        val subjects: List<SubjectDto> = emptyList(),
        val users: List<UserDto> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll(silent: Boolean = false) {
        _state.update {
            it.copy(
                loading = !silent && it.groups.isEmpty() && it.users.isEmpty(),
                refreshing = silent,
                error = null,
            )
        }
        viewModelScope.launch {
            val groupsDeferred = async { repository.groups() }
            val subjectsDeferred = async { repository.subjects() }
            val usersDeferred = async { repository.users() }

            val groupsResult = groupsDeferred.await()
            val subjectsResult = subjectsDeferred.await()
            val usersResult = usersDeferred.await()

            val failure = listOf(groupsResult, subjectsResult, usersResult)
                .filterIsInstance<ApiResult.Failure>()
                .firstOrNull()

            if (failure != null) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = failure.message,
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = null,
                    groups = groupsResult.dataOrNull().orEmpty(),
                    subjects = subjectsResult.dataOrNull().orEmpty(),
                    users = usersResult.dataOrNull().orEmpty(),
                )
            }
        }
    }

    fun createGroup(name: String, code: String?, avatar: String?, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.createGroup(name, code, avatar)) {
                is ApiResult.Success -> {
                    loadAll(silent = true)
                    onDone(null)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun updateGroup(
        id: Int,
        name: String,
        code: String?,
        avatar: String?,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = repository.updateGroup(id, name, code, avatar)) {
                is ApiResult.Success -> {
                    loadAll(silent = true)
                    onDone(null)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun deleteGroup(id: Int, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.deleteGroup(id)) {
                is ApiResult.Success -> {
                    loadAll(silent = true)
                    onDone(null)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun createSubject(
        name: String,
        groupId: Int,
        teacherId: Int?,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = repository.createSubject(name, groupId, teacherId)) {
                is ApiResult.Success -> {
                    loadAll(silent = true)
                    onDone(null)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun updateSubject(
        id: Int,
        name: String,
        teacherId: Int?,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = repository.updateSubject(id, name, teacherId)) {
                is ApiResult.Success -> {
                    loadAll(silent = true)
                    onDone(null)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun deleteSubject(id: Int, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.deleteSubject(id)) {
                is ApiResult.Success -> {
                    loadAll(silent = true)
                    onDone(null)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun createUser(
        fullName: String,
        login: String,
        password: String,
        role: String,
        groupId: Int?,
        avatar: String?,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (
                val result = repository.createUser(
                    fullName = fullName,
                    login = login,
                    password = password,
                    role = role,
                    groupId = groupId,
                    avatar = avatar,
                )
            ) {
                is ApiResult.Success -> {
                    loadAll(silent = true)
                    onDone(null)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun setUserAvatar(id: Int, avatar: String?, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.updateUser(id = id, avatar = avatar ?: "")) {
                is ApiResult.Success -> {
                    loadAll(silent = true)
                    onDone(null)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun setUserActive(id: Int, active: Boolean, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.updateUser(id = id, isActive = active)) {
                is ApiResult.Success -> {
                    loadAll(silent = true)
                    onDone(null)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun resetPassword(id: Int, password: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.updateUser(id = id, password = password)) {
                is ApiResult.Success -> {
                    loadAll(silent = true)
                    onDone(null)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun deleteUser(id: Int, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.deleteUser(id)) {
                is ApiResult.Success -> {
                    loadAll(silent = true)
                    onDone(null)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    companion object {
        fun factory(repository: ZhurnalRepository) = viewModelFactory {
            initializer { AdminViewModel(repository) }
        }
    }
}
