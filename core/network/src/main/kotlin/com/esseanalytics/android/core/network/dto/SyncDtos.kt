package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupStatsSlotDto(
    val platformId: String,
    val platformUrl: String,
    val title: String,
    val thumbnail: String,
    val views: Int,
    val likes: Int,
    val comments: Int,
)

@Serializable
data class GroupStatsItemDto(
    val fileId: String,
    val fileName: String,
    val fecha_creacion: String,
    val platforms: Map<String, GroupStatsSlotDto>,
)

@Serializable
data class GroupStatsResponse(val items: List<GroupStatsItemDto>)

@Serializable
data class CalendarConfigDto(
    val platform: String,
    val lastPublishedTitle: String,
    val lastPublishedDate: String,
    val intervalDays: Int,
    val lastVideoId: String? = null,
    val nextVideoId: String? = null,
)
