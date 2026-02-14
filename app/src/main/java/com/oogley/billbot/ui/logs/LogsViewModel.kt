package com.oogley.billbot.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oogley.billbot.data.repository.LogsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogsUiState(
    val lines: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val filterLevel: String = "ALL",
    val autoScroll: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logsRepo: LogsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        logsRepo.startTailing()

        viewModelScope.launch {
            logsRepo.lines.collect { lines ->
                _uiState.update { state ->
                    val filtered = filterLines(lines, state.filterLevel)
                    state.copy(lines = filtered)
                }
            }
        }

        viewModelScope.launch {
            logsRepo.isLoading.collect { loading ->
                _uiState.update { it.copy(isLoading = loading) }
            }
        }
    }

    fun setFilterLevel(level: String) {
        _uiState.update { state ->
            val filtered = filterLines(logsRepo.lines.value, level)
            state.copy(filterLevel = level, lines = filtered)
        }
    }

    fun setAutoScroll(enabled: Boolean) {
        _uiState.update { it.copy(autoScroll = enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        logsRepo.stopTailing()
    }

    private fun filterLines(lines: List<String>, level: String): List<String> {
        if (level == "ALL") return lines
        val levels = when (level) {
            "ERROR" -> listOf("ERROR", "FATAL")
            "WARN" -> listOf("ERROR", "FATAL", "WARN", "WARNING")
            "INFO" -> listOf("ERROR", "FATAL", "WARN", "WARNING", "INFO")
            "DEBUG" -> return lines // show everything
            else -> return lines
        }
        return lines.filter { line ->
            levels.any { lvl -> line.contains(lvl, ignoreCase = true) }
        }
    }
}
