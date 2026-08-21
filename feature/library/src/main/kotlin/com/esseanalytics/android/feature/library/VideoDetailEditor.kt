package com.esseanalytics.android.feature.library

import com.esseanalytics.android.core.model.Platform
import kotlinx.coroutines.flow.StateFlow

internal interface VideoDetailEditor {
    val isSaving: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>
    fun togglePlatform(platform: Platform)
    fun saveLink(platform: Platform, rawUrl: String)
}
