package com.gymlog.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.Factory
import com.gymlog.app.data.GymRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns reloadable application data independently from Compose composition. */
internal class GymLogViewModel(private val repository: GymRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(repository.loadUiStateResult())
    val uiState: StateFlow<Result<AppUiState>> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = repository.loadUiStateResult()
    }

    fun createFreshDatabase() {
        _uiState.value = runCatching {
            repository.createFreshDatabase()
            repository.loadUiState()
        }
    }

    fun replace(state: Result<AppUiState>) {
        _uiState.value = state
    }

    companion object {
        fun factory(repository: GymRepository): Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GymLogViewModel(repository) as T
        }
    }
}
