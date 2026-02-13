package com.oogley.billbot.data.gateway.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RequestFrame(
    val type: String = "req",
    val id: String,
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class ResponseFrame(
    val type: String,
    val id: String,
    val ok: Boolean,
    val payload: JsonElement? = null,
    val error: ErrorShape? = null
)

@Serializable
data class EventFrame(
    val type: String,
    val event: String,
    val payload: JsonElement? = null,
    val seq: Int? = null,
    val stateVersion: StateVersion? = null
)

@Serializable
data class ErrorShape(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
    val retryable: Boolean? = null,
    val retryAfterMs: Int? = null
)

@Serializable
data class StateVersion(
    val presence: Int,
    val health: Int
)

// Union type for incoming frames - parse by "type" field
@Serializable
data class RawFrame(
    val type: String,
    val id: String? = null,
    val ok: Boolean? = null,
    val payload: JsonElement? = null,
    val error: ErrorShape? = null,
    val event: String? = null,
    val seq: Int? = null,
    val stateVersion: StateVersion? = null
)
