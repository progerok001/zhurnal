package ru.zhurnal.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.zhurnal.app.data.ApiResult
import ru.zhurnal.app.data.AuditEntryDto
import ru.zhurnal.app.data.ZhurnalRepository

class AuditViewModel(
    private val repository: ZhurnalRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val entries: List<AuditEntryDto> = emptyList(),
        val total: Int = 0,
        val hasMore: Boolean = false,
        val filter: String = "all",
        val query: String = "",
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        load()
    }

    fun load(refresh: Boolean = true) {
        val current = _state.value
        if (current.loadingMore) return
        _state.update {
            it.copy(
                loading = refresh && it.entries.isEmpty(),
                refreshing = refresh && it.entries.isNotEmpty(),
                error = null,
            )
        }
        viewModelScope.launch {
            when (
                val result = repository.audit(
                    limit = PAGE_SIZE,
                    offset = 0,
                    entityType = current.filter,
                    query = current.query,
                )
            ) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        entries = result.data.entries,
                        total = result.data.total,
                        hasMore = result.data.hasMore,
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

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || !current.hasMore) return
        _state.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            when (
                val result = repository.audit(
                    limit = PAGE_SIZE,
                    offset = current.entries.size,
                    entityType = current.filter,
                    query = current.query,
                )
            ) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loadingMore = false,
                        entries = it.entries + result.data.entries,
                        total = result.data.total,
                        hasMore = result.data.hasMore,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loadingMore = false, error = result.message)
                }
            }
        }
    }

    fun setFilter(filter: String) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter) }
        load()
    }

    fun search(text: String) {
        _state.update { it.copy(query = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            load()
        }
    }

    companion object {
        private const val PAGE_SIZE = 50

        fun factory(repository: ZhurnalRepository) = viewModelFactory {
            initializer { AuditViewModel(repository) }
        }
    }
}
