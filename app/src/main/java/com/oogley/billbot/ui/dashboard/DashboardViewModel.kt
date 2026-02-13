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

data class DashboardUiState(
    val snapshot: InfrastructureSnapshot? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val lastUpdated: Long = 0
)

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

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
