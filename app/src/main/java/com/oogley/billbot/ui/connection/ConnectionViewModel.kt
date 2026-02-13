package com.oogley.billbot.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oogley.billbot.data.gateway.ConnectionState
import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val gateway: GatewayClient,
    private val preferences: UserPreferences
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = gateway.connectionState

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _savedUrl = MutableStateFlow("")
    val savedUrl: StateFlow<String> = _savedUrl.asStateFlow()

    companion object {
        private const val DEFAULT_TOKEN = "local-dev-token"
    }

    init {
        // Forward gateway errors to UI
        viewModelScope.launch {
            gateway.lastError.collect { gatewayError ->
                if (gatewayError != null) _error.value = gatewayError
            }
        }

        // Load saved URL and auto-connect
        viewModelScope.launch {
            _savedUrl.value = preferences.gatewayUrl.first()

            val autoConnect = preferences.autoConnect.first()
            if (autoConnect && _savedUrl.value.isNotEmpty()) {
                connect(_savedUrl.value)
            }
        }
    }

    fun connect(url: String) {
        viewModelScope.launch {
            _error.value = null
            preferences.setGatewayUrl(url)
            _savedUrl.value = url

            try {
                gateway.connect(url, DEFAULT_TOKEN)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun disconnect() {
        _error.value = null
        gateway.disconnect()
    }
}
