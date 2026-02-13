package com.oogley.billbot.ui.tokens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oogley.billbot.data.gateway.model.SessionModelUsage
import com.oogley.billbot.data.gateway.model.SessionMessageCounts
import com.oogley.billbot.data.gateway.model.UsageTotals
import com.oogley.billbot.data.repository.TokensRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TokensView { TOTAL, BY_DEVICE }

data class TokensUiState(
    val totals: UsageTotals = UsageTotals(),
    val messages: SessionMessageCounts = SessionMessageCounts(),
    val byModel: List<SessionModelUsage> = emptyList(),
    val currentView: TokensView = TokensView.TOTAL,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class TokensViewModel @Inject constructor(
    private val tokensRepo: TokensRepository
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
                val result = tokensRepo.getUsage()
                _uiState.update {
                    it.copy(
                        totals = result.totals,
                        messages = result.aggregates.messages,
                        byModel = result.aggregates.byModel,
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
}
