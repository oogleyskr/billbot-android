package com.oogley.billbot.data.gateway.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ConnectParams(
    val minProtocol: Int = 3,
    val maxProtocol: Int = 3,
    val client: ClientInfo,
    val caps: List<String> = emptyList(),
    val role: String = "operator",
    val scopes: List<String> = listOf("operator.admin"),
    val auth: AuthParams? = null
)

@Serializable
data class ClientInfo(
    val id: String = "billbot-android",
    val displayName: String = "BillBot Android",
    val version: String,
    val platform: String = "android",
    val deviceFamily: String? = "phone",
    val modelIdentifier: String? = null,
    val mode: String = "ui",
    val instanceId: String? = null
)

@Serializable
data class AuthParams(
    val token: String? = null,
    val password: String? = null
)

// Hello-OK response from server
@Serializable
data class HelloOk(
    val type: String,
    val protocol: Int,
    val server: ServerInfo,
    val features: Features,
    val snapshot: JsonElement? = null,
    val auth: AuthResponse? = null,
    val policy: Policy
)

@Serializable
data class ServerInfo(
    val version: String,
    val commit: String? = null,
    val host: String? = null,
    val connId: String
)

@Serializable
data class Features(
    val methods: List<String> = emptyList(),
    val events: List<String> = emptyList()
)

@Serializable
data class AuthResponse(
    val deviceToken: String,
    val role: String,
    val scopes: List<String> = emptyList(),
    val issuedAtMs: Long? = null
)

@Serializable
data class Policy(
    val maxPayload: Int = 10485760,
    val maxBufferedBytes: Int = 52428800,
    val tickIntervalMs: Int = 30000
)
