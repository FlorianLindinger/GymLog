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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Writes leave the main thread but must still apply in tap order (for example a weight change
    // followed immediately by logging sets), so they share one lock.
    private val writeLock = Mutex()

    /** Runs a database write on the IO dispatcher, serialized with every other write. */
    suspend fun <T> write(action: () -> T): T = writeLock.withLock { withContext(Dispatchers.IO) { action() } }

    fun mutate(action: () -> Unit) = mutate(action) {}

    /** Writes in the background, refreshes, then hands the result back on the main thread. */
    fun <T> mutate(action: () -> T, onDone: (T) -> Unit) {
        viewModelScope.launch {
            val result = write(action)
            refresh()
            onDone(result)
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
