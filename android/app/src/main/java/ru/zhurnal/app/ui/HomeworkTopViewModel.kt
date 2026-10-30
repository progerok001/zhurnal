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
import ru.zhurnal.app.data.HomeworkTopResponse
import ru.zhurnal.app.data.ZhurnalRepository

class HomeworkTopViewModel(
    private val repository: ZhurnalRepository,
    private val groupId: Int,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val period: String = "week",
        val data: HomeworkTopResponse? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setPeriod(period: String) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, data = null, loading = true, error = null) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.homeworkTop(groupId, _state.value.period)) {
                is ApiResult.Success -> _state.update {
                    it.copy(data = result.data, loading = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    companion object {
        fun factory(repository: ZhurnalRepository, groupId: Int) = viewModelFactory {
            initializer { HomeworkTopViewModel(repository, groupId) }
        }
    }
}
