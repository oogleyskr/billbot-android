package com.oogley.billbot.data.gateway.model

import kotlinx.serialization.Serializable

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

@Serializable
data class SessionModelUsage(
    val provider: String? = null,
    val model: String? = null,
    val count: Int = 0,
    val totals: UsageTotals = UsageTotals()
)

@Serializable
data class SessionMessageCounts(
    val total: Int = 0,
    val user: Int = 0,
    val assistant: Int = 0,
    val toolCalls: Int = 0,
    val toolResults: Int = 0,
    val errors: Int = 0
)

@Serializable
data class DailyUsage(
    val date: String = "",
    val tokens: Long = 0,
    val cost: Double = 0.0,
    val messages: Int = 0,
    val toolCalls: Int = 0,
    val errors: Int = 0
)

@Serializable
data class SessionsUsageAggregates(
    val messages: SessionMessageCounts = SessionMessageCounts(),
    val byModel: List<SessionModelUsage> = emptyList(),
    val byProvider: List<SessionModelUsage> = emptyList(),
    val daily: List<DailyUsage> = emptyList()
)

@Serializable
data class SessionsUsageResult(
    val updatedAt: Long = 0,
    val startDate: String = "",
    val endDate: String = "",
    val totals: UsageTotals = UsageTotals(),
    val aggregates: SessionsUsageAggregates = SessionsUsageAggregates()
)
