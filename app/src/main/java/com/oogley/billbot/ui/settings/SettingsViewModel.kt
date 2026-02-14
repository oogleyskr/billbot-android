package com.oogley.billbot.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oogley.billbot.data.config.ConfigField
import com.oogley.billbot.data.config.ConfigSchemaParser
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
import kotlinx.serialization.json.*
import javax.inject.Inject

data class SettingsUiState(
    val config: JsonElement? = null,
    val schema: JsonElement? = null,
    val configFields: List<ConfigField> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val serverVersion: String = "",
    val gatewayUrl: String = "",
    val isConnected: Boolean = false,
    val isDirty: Boolean = false,
    val searchQuery: String = "",
    val baseHash: String? = null,
    val saveSuccess: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val backgroundService: Boolean = false,
    val isAdmin: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val gateway: GatewayClient,
    private val preferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Track changed fields as path -> value
    private val pendingChanges = mutableMapOf<String, Any>()

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
        viewModelScope.launch {
            preferences.notificationsEnabled.collect { enabled ->
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferences.backgroundService.collect { enabled ->
                _uiState.update { it.copy(backgroundService = enabled) }
            }
        }
        viewModelScope.launch {
            gateway.grantedScopes.collect { scopes ->
                _uiState.update { it.copy(isAdmin = "operator.admin" in scopes || scopes.isEmpty()) }
            }
        }
    }

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val config = settingsRepo.getConfig()
                val schema = settingsRepo.getSchema()

                val fields = if (schema != null && config != null) {
                    ConfigSchemaParser.parse(schema, config)
                } else emptyList()

                val hash = config?.jsonObject?.get("_hash")?.jsonPrimitive?.contentOrNull

                _uiState.update { it.copy(
                    config = config,
                    schema = schema,
                    configFields = fields,
                    baseHash = hash,
                    isLoading = false,
                    error = null,
                    isDirty = false
                )}
                pendingChanges.clear()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onFieldChanged(path: String, value: Any) {
        pendingChanges[path] = value
        _uiState.update { it.copy(isDirty = pendingChanges.isNotEmpty()) }
    }

    fun onSearchChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun savePatch() {
        if (pendingChanges.isEmpty()) return
        viewModelScope.launch {
            try {
                val patch = buildJsonObject {
                    pendingChanges.forEach { (path, value) ->
                        // Build nested path: "a.b.c" -> { a: { b: { c: value } } }
                        val parts = path.split(".")
                        if (parts.size == 1) {
                            putValue(parts[0], value)
                        } else {
                            // For nested paths, we build a flat key as the gateway handles dotted paths
                            putValue(path, value)
                        }
                    }
                }
                settingsRepo.applyConfig(_uiState.value.baseHash, patch)
                _uiState.update { it.copy(saveSuccess = true, isDirty = false) }
                pendingChanges.clear()
                // Reload config to get fresh state
                loadConfig()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Save failed: ${e.message}") }
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setNotificationsEnabled(enabled) }
    }

    fun setBackgroundService(enabled: Boolean) {
        viewModelScope.launch { preferences.setBackgroundService(enabled) }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
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

    private fun JsonObjectBuilder.putValue(key: String, value: Any) {
        when (value) {
            is String -> put(key, value)
            is Boolean -> put(key, value)
            is Double -> {
                if (value == value.toLong().toDouble()) {
                    put(key, value.toLong())
                } else {
                    put(key, value)
                }
            }
            is Int -> put(key, value)
            is Long -> put(key, value)
            else -> put(key, value.toString())
        }
    }
}
