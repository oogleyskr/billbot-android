package com.oogley.billbot.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oogley.billbot.data.gateway.ConnectionState
import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.preferences.UserPreferences
import com.oogley.billbot.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

data class SettingsUiState(
    val config: JsonElement? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val serverVersion: String = "",
    val gatewayUrl: String = "",
    val isConnected: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val gateway: GatewayClient,
    private val preferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gateway.connectionState.collect { state ->
                _uiState.update { it.copy(isConnected = state == ConnectionState.CONNECTED) }
                if (state == ConnectionState.CONNECTED) {
                    loadConfig()
                }
            }
        }
        viewModelScope.launch {
            preferences.gatewayUrl.collect { url ->
                _uiState.update { it.copy(gatewayUrl = url) }
            }
        }
    }

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val config = settingsRepo.getConfig()
                _uiState.update { it.copy(config = config, isLoading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateSetting(path: String, value: String) {
        viewModelScope.launch {
            try {
                settingsRepo.patchConfig(path, JsonPrimitive(value))
                loadConfig() // Reload to confirm
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun disconnect() {
        gateway.disconnect()
    }
}
