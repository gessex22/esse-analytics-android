package com.esseanalytics.android.feature.calendar

import com.esseanalytics.android.core.network.dto.UploadHistoryItemDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class PublishedHistoryGroup(
    val identity: String,
    val events: List<UploadHistoryItemDto>,
    val thumbnailEvent: UploadHistoryItemDto,
    val latestPublishedAt: Instant?,
)

private data class ContentDay(val contentId: String, val day: LocalDate)
private data class BatchFile(val operationId: String, val fileName: String)

internal fun groupPublishedHistory(
    history: List<UploadHistoryItemDto>,
    selectedDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<PublishedHistoryGroup> {
    val indexed = history.withIndex().toList()
    val canonicalGroups = indexed.mapNotNull { indexedEvent ->
        val event = indexedEvent.value
        val contentId = event.contentId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val day = event.localDayOrNull(zoneId) ?: return@mapNotNull null
        ContentDay(contentId, day) to indexedEvent
    }.groupBy({ it.first }, { it.second })

    val pairedByIndex = mutableMapOf<Int, PublishedHistoryGroup>()
    val blockedFromFallback = mutableSetOf<Int>()
    canonicalGroups.forEach { (contentDay, members) ->
        val instagram = members.filter { it.value.platform == "instagram" }
        val facebook = members.filter { it.value.platform == "facebook" }
        if (instagram.size > 1 || facebook.size > 1) {
            blockedFromFallback += members.map { it.index }
            return@forEach
        }
        if (instagram.isEmpty() || facebook.isEmpty()) return@forEach
        val ig = instagram.single()
        val fb = facebook.single()
        if (contentDay.day != selectedDate || !ig.value.hasValidEventIdentity() || !fb.value.hasValidEventIdentity() ||
            ig.value.id == fb.value.id
        ) {
            blockedFromFallback += listOf(ig.index, fb.index)
            return@forEach
        }
        val group = pairedGroup(ig.value, fb.value)
        pairedByIndex[ig.index] = group
        pairedByIndex[fb.index] = group
    }

    val batchGroups = indexed.mapNotNull { indexedEvent ->
        val event = indexedEvent.value
        val operationId = event.operationId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val fileName = event.fileName?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        BatchFile(operationId, fileName) to indexedEvent
    }.groupBy({ it.first }, { it.second })

    batchGroups.forEach { (_, members) ->
        val instagram = members.filter { it.value.platform == "instagram" }
        val facebook = members.filter { it.value.platform == "facebook" }
        if (instagram.size != 1 || facebook.size != 1) return@forEach
        val ig = instagram.single()
        val fb = facebook.single()
        if (ig.index in blockedFromFallback || fb.index in blockedFromFallback ||
            ig.index in pairedByIndex || fb.index in pairedByIndex
        ) return@forEach
        val igEvent = ig.value
        val fbEvent = fb.value
        if (!igEvent.hasValidEventIdentity() || !fbEvent.hasValidEventIdentity() ||
            igEvent.id == fbEvent.id ||
            igEvent.localDayOrNull(zoneId) != selectedDate || fbEvent.localDayOrNull(zoneId) != selectedDate ||
            hasContradiction(igEvent.deviceId, fbEvent.deviceId) ||
            hasContradiction(igEvent.source, fbEvent.source) ||
            hasContradiction(igEvent.contentId, fbEvent.contentId)
        ) return@forEach
        val group = pairedGroup(igEvent, fbEvent)
        pairedByIndex[ig.index] = group
        pairedByIndex[fb.index] = group
    }

    val groups = mutableListOf<PublishedHistoryGroup>()
    val emittedPairs = mutableSetOf<String>()
    val duplicateOccurrences = mutableMapOf<String, Int>()
    indexed.forEach { indexedEvent ->
        val group = pairedByIndex[indexedEvent.index]
        if (group != null) {
            if (emittedPairs.add(group.identity)) groups += group
        } else {
            val event = indexedEvent.value
            if (event.localDayOrNull(zoneId) != selectedDate) return@forEach
            val eventIdentity = historyEventIdentity(event)
            val occurrence = duplicateOccurrences.getOrDefault(eventIdentity, 0)
            duplicateOccurrences[eventIdentity] = occurrence + 1
            groups += PublishedHistoryGroup(
                identity = "event:" + encodeIdentityParts(listOf(eventIdentity, occurrence.toString())),
                events = listOf(event),
                thumbnailEvent = event,
                latestPublishedAt = event.publishedInstantOrNull(),
            )
        }
    }
    return groups.sortedWith(
        compareByDescending<PublishedHistoryGroup> { it.latestPublishedAt }
            .thenBy { it.identity },
    )
}

private fun pairedGroup(
    instagram: UploadHistoryItemDto,
    facebook: UploadHistoryItemDto,
): PublishedHistoryGroup {
    val members = listOf(instagram, facebook).map(::historyEventIdentity).sorted()
    val igInstant = instagram.publishedInstantOrNull()!!
    val fbInstant = facebook.publishedInstantOrNull()!!
    return PublishedHistoryGroup(
        identity = "crosspost:" + encodeIdentityParts(members),
        events = listOf(instagram, facebook),
        thumbnailEvent = instagram,
        latestPublishedAt = maxOf(igInstant, fbInstant),
    )
}

private fun UploadHistoryItemDto.hasValidEventIdentity(): Boolean =
    id.isNotBlank() && platformId.isNotBlank() && publishedInstantOrNull() != null

private fun hasContradiction(left: String?, right: String?): Boolean =
    !left.isNullOrBlank() && !right.isNullOrBlank() && left != right

private fun UploadHistoryItemDto.localDayOrNull(zoneId: ZoneId): LocalDate? =
    publishedInstantOrNull()?.atZone(zoneId)?.toLocalDate()

private fun UploadHistoryItemDto.publishedInstantOrNull(): Instant? =
    runCatching { Instant.parse(publishedAt) }.getOrNull()

private fun historyEventIdentity(event: UploadHistoryItemDto): String = listOf(
    event.id,
    event.platform,
    event.platformId,
    event.fileName.orEmpty(),
    event.publishedAt,
    event.operationId.orEmpty(),
    event.deviceId.orEmpty(),
    event.source.orEmpty(),
    event.contentId.orEmpty(),
).let(::encodeIdentityParts)

private fun encodeIdentityParts(parts: List<String>): String =
    parts.joinToString(separator = "") { part -> part.length.toString() + ":" + part }
