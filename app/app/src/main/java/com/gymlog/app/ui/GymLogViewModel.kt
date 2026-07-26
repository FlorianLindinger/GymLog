package com.gymlog.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.Factory
import androidx.lifecycle.viewModelScope
import com.gymlog.app.data.GymRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class GymLogLoadState(
    val result: Result<AppUiState>? = null,
    val refreshing: Boolean = result == null,
)

/** Owns reloadable application data independently from Compose composition. */
internal class GymLogViewModel(private val repository: GymRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(GymLogLoadState())
    val uiState: StateFlow<GymLogLoadState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() = load { repository.loadUiStateResult() }

    fun createFreshDatabase() = load {
        runCatching {
            repository.createFreshDatabase()
            repository.loadUiState()
        }
    }

    fun replace(state: Result<AppUiState>) {
        _uiState.value = GymLogLoadState(state)
    }

    fun mutate(action: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { action() }
            refresh()
        }
    }

    private fun load(block: () -> Result<AppUiState>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(refreshing = true)
            _uiState.value = GymLogLoadState(withContext(Dispatchers.IO) { block() })
        }
    }

    companion object {
        fun factory(repository: GymRepository): Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GymLogViewModel(repository) as T
        }
    }
}
