package com.esseanalytics.android.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.esseanalytics.android.core.network.dto.NextVideoDto
import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import java.time.LocalDate

private val previewToday = LocalDate.of(2026, 10, 14)
private val previewMonday = previewToday.minusDays((previewToday.dayOfWeek.value - 1).toLong())
private val previewSunday = previewMonday.plusDays(6)
private val previewYearBoundary = LocalDate.of(2026, 12, 31)
private val previewPublishedToday = previewHistory(previewToday, "published-today", title = "Publicado hoy")
private val previewInstagramCrosspost = previewHistory(
    previewToday,
    id = "history-instagram-crosspost",
    platform = "instagram",
    platformId = "preview-ig-platform-id",
    title = "Reel publicado en Instagram y Facebook",
    hour = 16,
    minute = 2,
    fileName = "agenda-simple-crosspost.mp4",
    operationId = "preview-crosspost-operation",
    deviceId = "preview-device",
    source = "android",
)
private val previewFacebookCrosspost = previewHistory(
    previewToday,
    id = "history-facebook-crosspost",
    platform = "facebook",
    platformId = "preview-fb-platform-id",
    title = "Título del evento Facebook",
    hour = 16,
    minute = 4,
    fileName = "agenda-simple-crosspost.mp4",
    operationId = "preview-crosspost-operation",
    deviceId = "preview-device",
    source = "android",
)
private val previewCanonicalInstagram = previewHistory(
    previewToday,
    id = "history-canonical-instagram",
    platform = "instagram",
    platformId = "preview-canonical-ig-platform",
    title = "Renombrado después de publicar",
    hour = 16,
    minute = 2,
    fileName = "instagram-old-name.mp4",
    contentId = "canonical-shared-content",
)
private val previewCanonicalFacebook = previewHistory(
    previewToday,
    id = "history-canonical-facebook",
    platform = "facebook",
    platformId = "preview-canonical-fb-platform",
    title = "Nombre distinto del mismo contenido",
    hour = 16,
    minute = 4,
    fileName = "facebook-renamed-name.mp4",
    contentId = "canonical-shared-content",
)
private val previewFallbackInstagram = previewHistory(
    previewToday,
    id = "history-fallback-instagram",
    platform = "instagram",
    platformId = "preview-fallback-ig-platform",
    fileName = "batch-fallback.mp4",
    operationId = "preview-fallback-batch",
)
private val previewFallbackFacebook = previewHistory(
    previewToday,
    id = "history-fallback-facebook",
    platform = "facebook",
    platformId = "preview-fallback-fb-platform",
    fileName = "batch-fallback.mp4",
    operationId = "preview-fallback-batch",
)
private val previewDifferentContentInstagram = previewHistory(
    previewToday,
    id = "history-different-content-instagram",
    platform = "instagram",
    platformId = "preview-different-ig-platform",
    fileName = "same-name-different-content.mp4",
    operationId = "preview-different-content-batch",
    contentId = "content-ig-only",
)
private val previewDifferentContentFacebook = previewHistory(
    previewToday,
    id = "history-different-content-facebook",
    platform = "facebook",
    platformId = "preview-different-fb-platform",
    fileName = "same-name-different-content.mp4",
    operationId = "preview-different-content-batch",
    contentId = "content-fb-only",
)
private val previewRepublicationInstagramOne = previewHistory(
    previewToday,
    id = "history-republication-ig-1",
    platform = "instagram",
    platformId = "preview-republication-ig-1",
    fileName = "republication-one.mp4",
    operationId = "preview-republication-batch-one",
    contentId = "repeated-content",
)
private val previewRepublicationInstagramTwo = previewHistory(
    previewToday,
    id = "history-republication-ig-2",
    platform = "instagram",
    platformId = "preview-republication-ig-2",
    fileName = "republication-two.mp4",
    operationId = "preview-republication-batch-two",
    contentId = "repeated-content",
)
private val previewRepublicationFacebook = previewHistory(
    previewToday,
    id = "history-republication-facebook",
    platform = "facebook",
    platformId = "preview-republication-fb",
    fileName = "republication-one.mp4",
    operationId = "preview-republication-batch-one",
    contentId = "repeated-content",
)
private val previewAmbiguousInstagram = previewHistory(
    previewToday,
    id = "history-ambiguous-instagram",
    platform = "instagram",
    fileName = "ambiguous-crosspost.mp4",
    operationId = "preview-ambiguous-operation",
    deviceId = "preview-device",
    source = "android",
)
private val previewAmbiguousFacebookOne = previewHistory(
    previewToday,
    id = "history-ambiguous-facebook-1",
    platform = "facebook",
    fileName = "ambiguous-crosspost.mp4",
    operationId = "preview-ambiguous-operation",
    deviceId = "preview-device",
    source = "android",
)
private val previewAmbiguousFacebookTwo = previewHistory(
    previewToday,
    id = "history-ambiguous-facebook-2",
    platform = "facebook",
    fileName = "ambiguous-crosspost.mp4",
    operationId = "preview-ambiguous-operation",
    deviceId = "preview-device",
    source = "android",
)
private val previewMissingIdInstagram = previewHistory(
    previewToday,
    id = "",
    platform = "instagram",
    platformId = "preview-missing-id-instagram",
    fileName = "missing-id-crosspost.mp4",
    operationId = "preview-missing-id-operation",
    deviceId = "preview-device",
    source = "android",
)
private val previewMissingIdFacebook = previewHistory(
    previewToday,
    id = "history-facebook-missing-instagram-id",
    platform = "facebook",
    platformId = "preview-missing-id-facebook",
    fileName = "missing-id-crosspost.mp4",
    operationId = "preview-missing-id-operation",
    deviceId = "preview-device",
    source = "android",
)
private val previewPublishedYesterday = previewHistory(
    previewToday.minusDays(1),
    id = "published-yesterday",
    platform = "instagram",
    title = "Historial de ayer",
)
private val previewThumbnailSources = mapOf(
    publishedThumbnailKey(previewPublishedToday) to PublishedThumbnailSources(
        platformThumbnailUrl = "file:///agenda-simple-preview-missing-thumbnail.jpg",
        cacheNamespace = "preview-only",
    ),
    publishedThumbnailKey(previewInstagramCrosspost) to PublishedThumbnailSources(
        platformThumbnailUrl = "file:///agenda-simple-preview-instagram-crosspost.jpg",
        cacheNamespace = "preview-instagram-crosspost-only",
    ),
    publishedThumbnailKey(previewCanonicalInstagram) to PublishedThumbnailSources(
        platformThumbnailUrl = "file:///agenda-simple-preview-canonical-instagram.jpg",
        cacheNamespace = "preview-canonical-instagram-only",
    ),
)

private fun previewSlot(
    platform: String,
    fileId: String,
    dayOffset: Long?,
    title: String,
) = CalendarSlot(
    platform = platform,
    lastPublishedTitle = "",
    lastPublishedDate = "",
    intervalDays = 0,
    nextVideo = NextVideoDto(fileId, title, "00:42"),
    nextDate = dayOffset?.let(previewToday::plusDays),
)

private fun previewHistory(
    day: LocalDate,
    id: String,
    platform: String = "instagram",
    title: String = "Publicación de ejemplo",
    hour: Int = 18,
    minute: Int = 15,
    fileName: String? = null,
    operationId: String? = null,
    deviceId: String? = null,
    source: String? = null,
    platformId: String = "preview-$platform-$id",
    contentId: String? = null,
) = UploadHistoryItemDto(
    id = id,
    platform = platform,
    platformId = platformId,
    publishedAt = day.atTime(hour, minute).toInstant(java.time.ZoneOffset.UTC).toString(),
    title = title,
    fileName = fileName,
    operationId = operationId,
    deviceId = deviceId,
    source = source,
    contentId = contentId,
)

private val richPreview = CalendarUiState.Success(
    CalendarUiData(
        slots = listOf(
            previewSlot(
                "instagram",
                "shared-preview-01",
                -2,
                "Video atrasado y listo para Instagram",
            ),
            previewSlot(
                "youtube",
                "shared-preview-01",
                1,
                "El mismo archivo programado para mañana",
            ),
            previewSlot(
                "tiktok",
                "overdue-preview-02",
                -5,
                "Otro candidato atrasado",
            ),
            previewSlot(
                "facebook",
                "future-preview-02",
                12,
                "Una idea para una fecha futura",
            ),
            previewSlot(
                "instagram",
                "future-selected-day",
                2,
                "Candidato para el día futuro seleccionado",
            ),
            previewSlot(
                "youtube",
                "undated-preview-03",
                null,
                "Candidato sin fecha calculada",
            ),
        ),
        history = listOf(
            previewPublishedToday,
            previewInstagramCrosspost,
            previewFacebookCrosspost,
            previewCanonicalInstagram,
            previewCanonicalFacebook,
            previewFallbackInstagram,
            previewFallbackFacebook,
            previewDifferentContentInstagram,
            previewDifferentContentFacebook,
            previewRepublicationInstagramOne,
            previewRepublicationInstagramTwo,
            previewRepublicationFacebook,
            previewAmbiguousInstagram,
            previewAmbiguousFacebookOne,
            previewAmbiguousFacebookTwo,
            previewMissingIdInstagram,
            previewMissingIdFacebook,
            previewPublishedYesterday,
            previewHistory(
                previewToday.plusDays(2),
                "published-selected-day",
                platform = "youtube",
                title = "Evento del día seleccionado",
                hour = 9,
            ),
            previewHistory(
                previewToday.plusDays(2),
                "published-selected-day-2",
                platform = "tiktok",
                title = "Otro evento, sin fusionar",
                hour = 12,
            ),
        ),
    ),
)

@Preview(name = "Agenda · semana clara 393", showBackground = true, widthDp = 393, heightDp = 900)
@Composable
private fun AgendaSimpleLightPreview() = CalendarStudioContent(
    state = richPreview,
    today = previewToday,
    publishedThumbnails = previewThumbnailSources,
)

@Preview(name = "Agenda simple portada de preview con fallback", showBackground = true, heightDp = 900)
@Composable
private fun AgendaSimpleThumbnailFallbackPreview() = CalendarStudioContent(
    state = richPreview,
    today = previewToday,
    publishedThumbnails = previewThumbnailSources,
)

@Preview(
    name = "Agenda simple oscura",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    widthDp = 393,
    heightDp = 900,
)
@Composable
private fun AgendaSimpleDarkPreview() = CalendarStudioContent(state = richPreview, today = previewToday)

@Preview(name = "Agenda · semana amplia", showBackground = true, widthDp = 480, heightDp = 900)
@Composable
private fun AgendaSimpleWideWeekPreview() = CalendarStudioContent(state = richPreview, today = previewToday)

@Preview(name = "Agenda simple 320dp", showBackground = true, widthDp = 320, heightDp = 900)
@Composable
private fun AgendaSimpleCompactPreview() = CalendarStudioContent(state = richPreview, today = previewToday)

@Preview(
    name = "Agenda simple texto 1.3",
    showBackground = true,
    widthDp = 320,
    fontScale = 1.3f,
    heightDp = 1100,
)
@Composable
private fun AgendaSimpleLargeTextPreview() = CalendarStudioContent(state = richPreview, today = previewToday)

@Preview(name = "Agenda simple día anterior seleccionado", showBackground = true, heightDp = 1000)
@Composable
private fun AgendaSimplePreviousDayPreview() = CalendarStudioContent(
    state = richPreview,
    today = previewToday,
    initialSelectedDate = previewToday.minusDays(1),
)

@Preview(name = "Agenda simple otro día seleccionado", showBackground = true, heightDp = 1000)
@Composable
private fun AgendaSimpleSelectedDayPreview() = CalendarStudioContent(
    state = richPreview,
    today = previewToday,
    initialSelectedDate = previewToday.plusDays(2),
)

@Preview(name = "Agenda · lunes seleccionado", showBackground = true, widthDp = 393, heightDp = 900)
@Composable
private fun AgendaMondaySelectedPreview() = CalendarStudioContent(
    state = richPreview,
    today = previewToday,
    initialSelectedDate = previewMonday,
)

@Preview(name = "Agenda · domingo seleccionado", showBackground = true, widthDp = 393, heightDp = 900)
@Composable
private fun AgendaSundaySelectedPreview() = CalendarStudioContent(
    state = richPreview,
    today = previewToday,
    initialSelectedDate = previewSunday,
)

@Preview(name = "Agenda · cambio de año", showBackground = true, widthDp = 393, heightDp = 900)
@Composable
private fun AgendaYearBoundaryPreview() = CalendarStudioContent(
    state = richPreview,
    today = previewYearBoundary,
)

@Preview(name = "Agenda simple sin próximos", showBackground = true, heightDp = 850)
@Composable
private fun AgendaSimpleNoCandidatesPreview() = CalendarStudioContent(
    state = CalendarUiState.Success(
        CalendarUiData(
            slots = listOf(
                previewSlot("youtube", "future-only", 1, "Solo candidato futuro"),
                previewSlot("instagram", "no-date", null, "Candidato sin fecha"),
            ),
            history = emptyList(),
        ),
    ),
    today = previewToday,
)

@Preview(name = "Agenda simple solo historial", showBackground = true, heightDp = 850)
@Composable
private fun AgendaSimpleHistoryOnlyPreview() = CalendarStudioContent(
    state = CalendarUiState.Success(
        CalendarUiData(
            slots = emptyList(),
            history = listOf(
                previewHistory(previewToday, "history-only-1", title = "Primer evento"),
                previewHistory(previewToday, "history-only-2", platform = "youtube", title = "Segundo evento", hour = 10),
            ),
        ),
    ),
    today = previewToday,
)

@Preview(name = "Agenda simple carga", showBackground = true, heightDp = 850)
@Composable
private fun AgendaSimpleLoadingPreview() = CalendarStudioContent(state = CalendarUiState.Loading, today = previewToday)

@Preview(name = "Agenda simple error inicial", showBackground = true, heightDp = 900)
@Composable
private fun AgendaSimpleInitialErrorPreview() = CalendarStudioContent(
    state = CalendarUiState.Error("No se pudo cargar. Revisa la conexión e inténtalo de nuevo."),
    today = previewToday,
)

@Preview(name = "Agenda simple error al actualizar", showBackground = true, heightDp = 1000)
@Composable
private fun AgendaSimpleRefreshErrorPreview() = CalendarStudioContent(
    state = richPreview.copy(
        isRefreshing = false,
        refreshError = "No se pudo actualizar. Revisa la conexión e inténtalo de nuevo.",
    ),
    today = previewToday,
)
