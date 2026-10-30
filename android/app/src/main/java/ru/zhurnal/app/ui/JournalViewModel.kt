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
import ru.zhurnal.app.data.JournalResponse
import ru.zhurnal.app.data.SummaryResponse
import ru.zhurnal.app.data.ZhurnalRepository

class JournalViewModel(
    private val repository: ZhurnalRepository,
    private val groupId: Int,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val journal: JournalResponse? = null,
        val error: String? = null,
        val summary: SummaryResponse? = null,
        val summaryLoading: Boolean = false,
        val summaryError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var selectedSubjectId: Int? = null

    init {
        load()
    }

    fun load(subjectId: Int? = selectedSubjectId, silent: Boolean = false) {
        val hasData = _state.value.journal != null
        if (!silent || !hasData) {
            _state.update {
                it.copy(
                    loading = !hasData,
                    refreshing = hasData,
                    error = null,
                )
            }
        }

        viewModelScope.launch {
            when (val result = repository.journal(groupId, subjectId)) {
                is ApiResult.Success -> {
                    selectedSubjectId = result.data.subject?.id
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            journal = result.data,
                            error = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = result.message,
                        )
                    }
                }
            }
        }
    }

    fun selectSubject(subjectId: Int) {
        if (selectedSubjectId == subjectId) return
        selectedSubjectId = subjectId
        _state.update { it.copy(summary = null, summaryError = null) }
        load(subjectId = subjectId)
    }

    fun loadSummary() {
        if (_state.value.summaryLoading) return
        _state.update { it.copy(summaryLoading = true, summaryError = null) }
        viewModelScope.launch {
            when (val result = repository.summary(groupId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        summary = result.data,
                        summaryLoading = false,
                        summaryError = null,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        summaryLoading = false,
                        summaryError = result.message,
                    )
                }
            }
        }
    }

    fun saveRecord(
        lessonId: Int,
        studentId: Int,
        grade: Int?,
        absent: Boolean,
        reason: String?,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = repository.putRecord(lessonId, studentId, grade, absent, reason)) {
                is ApiResult.Success -> {
                    onDone(null)
                    load(silent = true)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun addLesson(
        subjectId: Int,
        date: String,
        pairNumber: Int,
        topic: String?,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = repository.createLesson(subjectId, date, pairNumber, topic)) {
                is ApiResult.Success -> {
                    onDone(null)
                    load(silent = true)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun updateLesson(
        lessonId: Int,
        date: String,
        pairNumber: Int,
        topic: String?,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = repository.updateLesson(lessonId, date, pairNumber, topic)) {
                is ApiResult.Success -> {
                    onDone(null)
                    load(silent = true)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun deleteLesson(lessonId: Int, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.deleteLesson(lessonId)) {
                is ApiResult.Success -> {
                    onDone(null)
                    load(silent = true)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    companion object {
        fun factory(repository: ZhurnalRepository, groupId: Int) = viewModelFactory {
            initializer { JournalViewModel(repository, groupId) }
        }
    }
}
