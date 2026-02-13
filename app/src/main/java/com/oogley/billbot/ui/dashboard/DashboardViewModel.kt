package com.oogley.billbot.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oogley.billbot.data.gateway.ConnectionState
import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.gateway.model.InfrastructureSnapshot
import com.oogley.billbot.data.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which view the user is looking at on the Dashboard screen. */
enum class DashboardView { DEVICES, METRICS }

/**
 * UI state for the Dashboard screen.
 *
 * [snapshot] - Latest infrastructure snapshot from the gateway (polled every 10s).
 * [currentView] - Toggle between DEVICES (per-device cards) and METRICS (grouped by metric type).
 */
data class DashboardUiState(
    val snapshot: InfrastructureSnapshot? = null,
    val currentView: DashboardView = DashboardView.DEVICES,
    val isLoading: Boolean = true,
    val error: String? = null,
    val lastUpdated: Long = 0
)

/**
 * ViewModel for the Dashboard screen.
 *
 * Polls the gateway's `infrastructure` RPC every 10 seconds when connected.
 * Data covers all hardware: DGX Spark, RTX 3090, Radeon VII (Memory Cortex),
 * providers, tunnels, multimodal services, and system metrics (WSL2/DGX).
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepo: DashboardRepository,
    private val gateway: GatewayClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            gateway.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    startPolling()
                } else {
                    stopPolling()
                }
            }
        }
    }

    private fun startPolling() {
        stopPolling()
        pollJob = viewModelScope.launch {
            dashboardRepo.pollInfrastructure(10000).collect { snapshot ->
                _uiState.update {
                    it.copy(
                        snapshot = snapshot,
                        isLoading = false,
                        error = null,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val snapshot = dashboardRepo.getInfrastructure()
                _uiState.update {
                    it.copy(
                        snapshot = snapshot,
                        isLoading = false,
                        error = null,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun switchView(view: DashboardView) {
        _uiState.update { it.copy(currentView = view) }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
