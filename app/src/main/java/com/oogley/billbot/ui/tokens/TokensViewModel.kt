package com.oogley.billbot.ui.tokens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oogley.billbot.data.gateway.model.InfrastructureSnapshot
import com.oogley.billbot.data.gateway.model.SessionModelUsage
import com.oogley.billbot.data.gateway.model.SessionMessageCounts
import com.oogley.billbot.data.gateway.model.UsageTotals
import com.oogley.billbot.data.repository.DashboardRepository
import com.oogley.billbot.data.repository.TokensRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which view the user is looking at on the Tokens screen. */
enum class TokensView { TOTAL, BY_DEVICE }

/**
 * Live inference speed for a single device, extracted from the infrastructure snapshot.
 *
 * [device] - friendly name (e.g. "DGX Spark", "Memory Cortex", "WSL2 CPU")
 * [model] - model name (e.g. "gpt-oss-120b")
 * [tokPerSec] - current generation tok/s (live from last measurement)
 * [avgTokPerSec] - rolling average tok/s (null if not available)
 * [completions] - total completions counted (null if not available)
 */
data class DeviceSpeed(
    val device: String,
    val model: String,
    val tokPerSec: Double,
    val avgTokPerSec: Double? = null,
    val completions: Int? = null
)

/**
 * UI state for the Token Counter screen.
 *
 * [totals] - grand total across all devices/sessions (hero number)
 * [messages] - aggregate message counts (user, assistant, tool calls, errors)
 * [byModel] - per-model breakdown for the "By Device" view, sorted by cost/tokens desc
 * [speeds] - live tok/s for each device from infrastructure snapshot
 * [currentView] - which tab is active (TOTAL or BY_DEVICE)
 */
data class TokensUiState(
    val totals: UsageTotals = UsageTotals(),
    val messages: SessionMessageCounts = SessionMessageCounts(),
    val byModel: List<SessionModelUsage> = emptyList(),
    val speeds: List<DeviceSpeed> = emptyList(),
    val currentView: TokensView = TokensView.TOTAL,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel for the Token Counter screen.
 *
 * Fetches both usage data (sessions.usage RPC) and infrastructure snapshot
 * (for live tok/s speeds) in parallel. User can pull-to-refresh.
 */
@HiltViewModel
class TokensViewModel @Inject constructor(
    private val tokensRepo: TokensRepository,
    private val dashboardRepo: DashboardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TokensUiState())
    val uiState: StateFlow<TokensUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Fetch usage and infrastructure in parallel
                val usageDeferred = async { tokensRepo.getUsage() }
                val infraDeferred = async { dashboardRepo.getInfrastructure() }

                val result = usageDeferred.await()
                val infra = infraDeferred.await()

                _uiState.update {
                    it.copy(
                        totals = result.totals,
                        messages = result.aggregates.messages,
                        byModel = result.aggregates.byModel,
                        speeds = extractSpeeds(infra),
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun switchView(view: TokensView) {
        _uiState.update { it.copy(currentView = view) }
    }

    /**
     * Extract live tok/s from the infrastructure snapshot for each device.
     *
     * Data sources:
     *   - DGX Spark: inferenceSpeed.tokensPerSecond / averageTokPerSec (SGLang)
     *   - Memory Cortex (Radeon VII): memoryCortex.generationTokPerSec (llama.cpp Vulkan)
     *   - Heartbeat (WSL2 CPU): no dedicated speed metric in infrastructure
     */
    private fun extractSpeeds(infra: InfrastructureSnapshot): List<DeviceSpeed> {
        val speeds = mutableListOf<DeviceSpeed>()

        // DGX Spark — SGLang inference speed
        infra.inferenceSpeed?.let { speed ->
            if (speed.tokensPerSecond > 0 || speed.averageTokPerSec > 0) {
                speeds.add(DeviceSpeed(
                    device = "DGX Spark",
                    model = "gpt-oss-120b",
                    tokPerSec = speed.tokensPerSecond,
                    avgTokPerSec = speed.averageTokPerSec,
                    completions = speed.completionCount
                ))
            }
        }

        // Memory Cortex (Radeon VII) — llama.cpp Vulkan
        infra.memoryCortex?.let { cortex ->
            cortex.generationTokPerSec?.let { genSpeed ->
                if (genSpeed > 0) {
                    speeds.add(DeviceSpeed(
                        device = "Radeon VII",
                        model = cortex.modelName ?: "Qwen3-8B",
                        tokPerSec = genSpeed
                    ))
                }
            }
        }

        return speeds
    }
}
