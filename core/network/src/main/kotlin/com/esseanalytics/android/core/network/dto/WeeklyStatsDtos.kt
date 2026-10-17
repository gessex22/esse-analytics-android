package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class VideoDailyGainDto(val views: Long? = null, val likes: Long? = null, val comments: Long? = null, val estimated: Boolean = true, val status: String = "unavailable")

@Serializable
data class WeeklyStatsValuesDto(val views: Long? = null, val likes: Long? = null, val comments: Long? = null, val subscribersGained: Long? = null)

@Serializable
data class WeeklyStatsPlatformDto(val platform: String, val views: Long? = null, val likes: Long? = null, val comments: Long? = null, val subscribersGained: Long? = null)

@Serializable
data class WeeklyStatsWeekDto(
    val id: String, val start: String, val end: String, val status: String,
    val estimated: Boolean = true, val totals: WeeklyStatsValuesDto,
    val partialMetrics: List<String> = emptyList(), val platforms: List<WeeklyStatsPlatformDto> = emptyList(),
)

@Serializable
data class WeeklyStatsResponseDto(val generatedAt: String, val timeZone: String, val weeks: List<WeeklyStatsWeekDto>)
