package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class DashboardSummaryDto(
    val generatedAt: String,
    val timeZone: String,
    val followers: DashboardFollowersDto,
    val week: DashboardWeekDto,
    val pending: DashboardPendingDto?,
    val latest: List<DashboardLatestDto>,
)

@Serializable
data class DashboardFollowersDto(val total: Long?, val partial: Boolean, val platforms: List<DashboardFollowerDto>)

@Serializable
data class DashboardFollowerDto(
    val platform: String, val count: Long?, val weeklyGain: Long?, val status: String, val updatedAt: String? = null,
)

@Serializable
data class DashboardWeekDto(
    val start: String, val publications: Int?, val views: Long?, val viewsDeltaPercent: Double?, val viewsEstimated: Boolean = true,
    val viewsStatus: String? = null,
)

@Serializable
data class DashboardPendingDto(
    val total: Int, val toPublish: Int, val drafts: Int, val failed: Int,
    val source: String? = null, val items: List<DashboardPendingItemDto> = emptyList(),
)

@Serializable
data class DashboardPendingItemDto(val platform: String, val fileId: String, val title: String, val dueDate: String, val overdue: Boolean)

@Serializable
data class DashboardLatestDto(val platform: String, val video: DashboardVideoDto?)

@Serializable
data class DashboardVideoDto(
    val platform: String, val platformId: String, val platformUrl: String, val title: String,
    val thumbnail: String, val views: Long, val publishedAt: String?,
    val fileId: String? = null, val fileName: String? = null,
)
