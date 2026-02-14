package com.oogley.billbot.data.gateway.model

import kotlinx.serialization.Serializable

@Serializable
data class LogTailResponse(
    val file: String? = null,
    val cursor: String? = null,
    val size: Long? = null,
    val lines: List<String> = emptyList(),
    val truncated: Boolean = false,
    val reset: Boolean = false
)
