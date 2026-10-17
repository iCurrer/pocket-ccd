package com.pocketccd.prototype.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel(initialState: AppUiState = AppUiState()) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    fun onEvent(event: AppEvent) {
        _state.value = reduce(_state.value, event)
    }
}
