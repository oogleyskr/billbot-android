package com.oogley.billbot.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oogley.billbot.data.gateway.ConnectionState
import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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

    private val _savedToken = MutableStateFlow("")
    val savedToken: StateFlow<String> = _savedToken.asStateFlow()

    init {
        viewModelScope.launch {
            _savedUrl.value = preferences.gatewayUrl.first()
            _savedToken.value = preferences.authToken.first()

            // Auto-connect if we have saved credentials
            val autoConnect = preferences.autoConnect.first()
            if (autoConnect && _savedUrl.value.isNotEmpty()) {
                connect(_savedUrl.value, _savedToken.value)
            }
        }
    }

    fun connect(url: String, token: String) {
        viewModelScope.launch {
            _error.value = null
            preferences.setGatewayUrl(url)
            preferences.setAuthToken(token)
            _savedUrl.value = url
            _savedToken.value = token

            try {
                gateway.connect(url, token)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun disconnect() {
        gateway.disconnect()
    }
}
