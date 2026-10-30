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
import ru.zhurnal.app.data.GroupDto
import ru.zhurnal.app.data.ZhurnalRepository

class GroupsViewModel(
    private val repository: ZhurnalRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val groups: List<GroupDto> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        _state.update { current ->
            current.copy(
                loading = current.groups.isEmpty() && !refresh,
                refreshing = refresh,
                error = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.groups()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        groups = result.data,
                        error = null,
                    )
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

    companion object {
        fun factory(repository: ZhurnalRepository) = viewModelFactory {
            initializer { GroupsViewModel(repository) }
        }
    }
}
