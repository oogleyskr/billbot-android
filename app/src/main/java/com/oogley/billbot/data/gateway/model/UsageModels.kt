package com.oogley.billbot.data.gateway.model

import kotlinx.serialization.Serializable

/**
 * Data models for the gateway `sessions.usage` RPC response.
 *
 * The gateway aggregates token usage across all sessions, models, and providers.
 * We call it with `days: 36500` (~100 years) to get all-time totals.
 *
 * Response shape from gateway (usage.ts):
 *   { updatedAt, startDate, endDate, totals: UsageTotals, aggregates: SessionsUsageAggregates }
 *
 * Key mappings for "By Device" view:
 *   - provider="spark", model="gpt-oss-120b"   → DGX Spark (GB10 Blackwell)
 *   - provider="heartbeat"                      → WSL2 CPU (Qwen3-1.7B heartbeat)
 *   - Other providers map to their name capitalized
 *
 * Note: Memory Cortex (Radeon VII) uses its own middleware (port 8300) and doesn't
 * go through the gateway, so it won't appear in these token counts.
 */

/** Aggregate token counts and estimated costs across all sessions. */
@Serializable
data class UsageTotals(
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
    val totalTokens: Long = 0,
    val totalCost: Double = 0.0,
    val inputCost: Double = 0.0,
    val outputCost: Double = 0.0,
    val cacheReadCost: Double = 0.0,
    val cacheWriteCost: Double = 0.0,
    val missingCostEntries: Int = 0
)

/** Per-model (per-device) token breakdown. Used in the "By Device" view. */
@Serializable
data class SessionModelUsage(
    val provider: String? = null,
    val model: String? = null,
    val count: Int = 0,
    val totals: UsageTotals = UsageTotals()
)

/** Message counts across all sessions — user, assistant, tool calls, errors. */
@Serializable
data class SessionMessageCounts(
    val total: Int = 0,
    val user: Int = 0,
    val assistant: Int = 0,
    val toolCalls: Int = 0,
    val toolResults: Int = 0,
    val errors: Int = 0
)

/** Daily token/message breakdown (for future trend charts). */
@Serializable
data class DailyUsage(
    val date: String = "",
    val tokens: Long = 0,
    val cost: Double = 0.0,
    val messages: Int = 0,
    val toolCalls: Int = 0,
    val errors: Int = 0
)

/** Aggregated usage data — messages, per-model breakdown, daily trends. */
@Serializable
data class SessionsUsageAggregates(
    val messages: SessionMessageCounts = SessionMessageCounts(),
    val byModel: List<SessionModelUsage> = emptyList(),
    val byProvider: List<SessionModelUsage> = emptyList(),
    val daily: List<DailyUsage> = emptyList()
)

/** Top-level response from `sessions.usage` gateway RPC. */
@Serializable
data class SessionsUsageResult(
    val updatedAt: Long = 0,
    val startDate: String = "",
    val endDate: String = "",
    val totals: UsageTotals = UsageTotals(),
    val aggregates: SessionsUsageAggregates = SessionsUsageAggregates()
)
