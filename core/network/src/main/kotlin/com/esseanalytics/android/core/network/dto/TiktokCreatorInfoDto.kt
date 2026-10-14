package com.esseanalytics.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TiktokCreatorInfoDto(
    val nickname: String = "",
    val username: String = "",
    val privacyOptions: List<String> = emptyList(),
    val commentDisabled: Boolean = false,
    val duetDisabled: Boolean = false,
    val stitchDisabled: Boolean = false,
    val maxVideoDurationSec: Int = 0,
)
