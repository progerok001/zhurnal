package ru.zhurnal.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.zhurnal.app.data.ApiResult
import ru.zhurnal.app.data.HomeworkResponse
import ru.zhurnal.app.data.ZhurnalRepository

class HomeworkViewModel(
    private val repository: ZhurnalRepository,
    private val groupId: Int,
    private val subjectId: Int,
    private val lessonDate: String? = null,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val data: HomeworkResponse? = null,
        val error: String? = null,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = it.data == null,
                    refreshing = refresh && it.data != null,
                    error = null,
                )
            }
            when (val result = repository.homework(groupId, subjectId, lessonDate)) {
                is ApiResult.Success -> _state.update {
                    it.copy(data = result.data, loading = false, refreshing = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.message)
                }
            }
        }
    }

    fun add(text: String, dueDate: String?, onDone: (String?) -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val result = repository.createHomework(
                groupId,
                subjectId,
                text,
                normalizeDate(dueDate),
                lessonDate,
            )
            _state.update { it.copy(saving = false) }
            when (result) {
                is ApiResult.Success -> {
                    onDone(null)
                    load(refresh = true)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun update(id: Int, text: String, dueDate: String?, onDone: (String?) -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val result = repository.updateHomework(id, text, normalizeDate(dueDate))
            _state.update { it.copy(saving = false) }
            when (result) {
                is ApiResult.Success -> {
                    onDone(null)
                    load(refresh = true)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun delete(id: Int, onDone: (String?) -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val result = repository.deleteHomework(id)
            _state.update { it.copy(saving = false) }
            when (result) {
                is ApiResult.Success -> {
                    onDone(null)
                    load(refresh = true)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    private fun normalizeDate(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        fun factory(
            repository: ZhurnalRepository,
            groupId: Int,
            subjectId: Int,
            lessonDate: String? = null,
        ) = viewModelFactory {
            initializer { HomeworkViewModel(repository, groupId, subjectId, lessonDate) }
        }
    }
}
