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
import ru.zhurnal.app.data.SubstitutionResponse
import ru.zhurnal.app.data.ZhurnalRepository

class SubstitutionsViewModel(
    private val repository: ZhurnalRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val data: SubstitutionResponse? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        val hasData = _state.value.data != null
        _state.update {
            it.copy(
                loading = !refresh && !hasData,
                refreshing = refresh || hasData,
                error = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.substitutions(refresh = refresh)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        data = result.data,
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
            initializer { SubstitutionsViewModel(repository) }
        }
    }
}
