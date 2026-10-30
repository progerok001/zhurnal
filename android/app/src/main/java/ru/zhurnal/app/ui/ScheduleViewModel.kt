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
import ru.zhurnal.app.data.ScheduleEntryDto
import ru.zhurnal.app.data.ScheduleGroupDto
import ru.zhurnal.app.data.ScheduleResponse
import ru.zhurnal.app.data.ScheduleTimeEntryRequest
import ru.zhurnal.app.data.SubjectDto
import ru.zhurnal.app.data.ZhurnalRepository
import java.time.LocalDate

class ScheduleViewModel(
    private val repository: ZhurnalRepository,
    private val preferredGroupId: Int?,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val groups: List<ScheduleGroupDto> = emptyList(),
        val selectedGroupId: Int? = null,
        val weekStart: LocalDate = mondayOf(),
        val week: String = weekTypeFor(),
        val data: ScheduleResponse? = null,
        val subjects: List<SubjectDto> = emptyList(),
        val error: String? = null,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadGroups()
    }

    private fun loadGroups() {
        viewModelScope.launch {
            when (val result = repository.scheduleGroups()) {
                is ApiResult.Success -> {
                    val groups = result.data.sortedBy { if (it.id == preferredGroupId) 0 else 1 }
                    val own = groups.firstOrNull { it.id == preferredGroupId }
                    val selected = own?.id ?: groups.firstOrNull()?.id
                    _state.update {
                        it.copy(
                            groups = groups,
                            selectedGroupId = selected,
                            loading = selected != null,
                        )
                    }
                    if (selected != null) {
                        loadSchedule(selected, _state.value.week)
                    } else {
                        _state.update { it.copy(loading = false) }
                    }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun selectGroup(groupId: Int) {
        if (_state.value.selectedGroupId == groupId) return
        _state.update {
            it.copy(
                selectedGroupId = groupId,
                data = null,
                subjects = emptyList(),
                loading = true,
                error = null,
            )
        }
        loadSchedule(groupId, _state.value.week)
    }

    /** Переход на следующую (+) или прошлую (−) учебную неделю. */
    fun shiftWeek(weeks: Long) {
        val monday = _state.value.weekStart.plusWeeks(weeks)
        goToWeek(monday)
    }

    /** Вернуться к текущей неделе. */
    fun goToToday() {
        goToWeek(mondayOf())
    }

    private fun goToWeek(monday: LocalDate) {
        val week = weekTypeFor(monday)
        if (monday == _state.value.weekStart && week == _state.value.week) return
        _state.update {
            it.copy(
                weekStart = monday,
                week = week,
                data = null,
                loading = true,
                error = null,
            )
        }
        _state.value.selectedGroupId?.let { loadSchedule(it, week) }
    }

    fun refresh() {
        _state.value.selectedGroupId?.let { loadSchedule(it, _state.value.week, refresh = true) }
    }

    private fun loadSchedule(
        groupId: Int,
        week: String,
        refresh: Boolean = false,
        weekStart: LocalDate = _state.value.weekStart,
    ) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = it.data == null,
                    refreshing = refresh || it.data != null,
                    error = null,
                )
            }

            val result = repository.schedule(groupId, week, weekStart.toString())
            when (result) {
                is ApiResult.Success -> {
                    val data = result.data
                    _state.update {
                        it.copy(
                            data = data,
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                    loadSubjectsIfNeeded(groupId, data.canEdit)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = result.message,
                    )
                }
            }
        }
    }

    private fun loadSubjectsIfNeeded(groupId: Int, canEdit: Boolean) {
        if (!canEdit || _state.value.subjects.isNotEmpty()) return
        viewModelScope.launch {
            when (val result = repository.subjects(groupId)) {
                is ApiResult.Success -> _state.update { it.copy(subjects = result.data) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun saveEntry(
        existing: ScheduleEntryDto?,
        subjectId: Int,
        day: Int,
        pairNumber: Int,
        weekType: String,
        room: String?,
        color: String?,
        note: String?,
        secondSubjectId: Int?,
        secondRoom: String?,
        onDone: (String?) -> Unit,
    ) {
        val groupId = _state.value.selectedGroupId
        if (groupId == null) {
            onDone("Сначала выберите группу")
            return
        }
        if (_state.value.saving) return

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val result = if (existing == null) {
                repository.createSchedule(
                    groupId = groupId,
                    subjectId = subjectId,
                    day = day,
                    pairNumber = pairNumber,
                    weekType = weekType,
                    room = room,
                    color = color,
                    note = note,
                    secondSubjectId = secondSubjectId,
                    secondRoom = secondRoom,
                )
            } else {
                repository.updateSchedule(
                    id = existing.id,
                    subjectId = subjectId,
                    day = day,
                    pairNumber = pairNumber,
                    weekType = weekType,
                    room = room,
                    color = color,
                    note = note,
                    secondSubjectId = secondSubjectId,
                    secondRoom = secondRoom,
                )
            }
            _state.update { it.copy(saving = false) }

            when (result) {
                is ApiResult.Success -> {
                    onDone(null)
                    _state.update { it.copy(subjects = emptyList()) }
                    loadSchedule(groupId, _state.value.week, refresh = true)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun deleteEntry(entryId: Int, onDone: (String?) -> Unit) {
        val groupId = _state.value.selectedGroupId ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val result = repository.deleteSchedule(entryId)
            _state.update { it.copy(saving = false) }
            when (result) {
                is ApiResult.Success -> {
                    onDone(null)
                    loadSchedule(groupId, _state.value.week, refresh = true)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    fun saveTimes(day: Int, entries: List<ScheduleTimeEntryRequest>, onDone: (String?) -> Unit) {
        val groupId = _state.value.selectedGroupId
        if (groupId == null) {
            onDone("Сначала выберите группу")
            return
        }
        if (_state.value.saving) return

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val result = repository.saveScheduleTimes(groupId, day, entries)
            _state.update { it.copy(saving = false) }
            when (result) {
                is ApiResult.Success -> {
                    onDone(null)
                    loadSchedule(groupId, _state.value.week, refresh = true)
                }
                is ApiResult.Failure -> onDone(result.message)
            }
        }
    }

    companion object {
        fun factory(
            repository: ZhurnalRepository,
            preferredGroupId: Int?,
        ) = viewModelFactory {
            initializer { ScheduleViewModel(repository, preferredGroupId) }
        }
    }
}
