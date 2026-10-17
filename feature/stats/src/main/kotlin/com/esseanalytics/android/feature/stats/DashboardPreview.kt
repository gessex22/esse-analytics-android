package com.esseanalytics.android.feature.stats

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.esseanalytics.android.core.designsystem.theme.StudioTheme
import com.esseanalytics.android.core.network.dto.*

// Design-time fixtures only: never used by DashboardViewModel or a server preset.
private fun previewSummary() = DashboardSummaryDto(
    generatedAt = "2026-10-17T17:00:00Z", timeZone = "America/Chicago",
    followers = DashboardFollowersDto(42_427, false, listOf(
        DashboardFollowerDto("youtube", 24_000, 439, "ready"),
        DashboardFollowerDto("instagram", 11_000, 187, "ready"),
        DashboardFollowerDto("tiktok", 7_427, 92, "ready"),
    )),
    week = DashboardWeekDto("2026-10-12T05:00:00Z", 4, 8_700, 9.0),
    pending = DashboardPendingDto(3, 3, 0, 0, source = "calendar", items = listOf("youtube", "instagram", "tiktok").map {
        DashboardPendingItemDto(it, "preview-calendar", "La próxima historia por compartir", "2026-10-17", it == "tiktok")
    }),
    latest = listOf("youtube", "instagram", "tiktok").mapIndexed { index, platform ->
        DashboardLatestDto(platform, DashboardVideoDto(platform, "preview-$index", "", listOf("Una idea que vale la pena compartir", "Así empieza una buena historia", "Detrás de cada creación")[index], "", listOf(2400L, 1700L, 4600L)[index], "2026-10-17T10:00:00Z"))
    },
)

@Preview(name = "Studio · claro", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Studio · oscuro", showBackground = true, widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Studio · accesibilidad", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 1.6f)
@Composable
private fun StudioDashboardPreview() {
    StudioTheme { DashboardContent(DashboardUiState.Success(previewSummary()), onRefresh = {}) }
}
