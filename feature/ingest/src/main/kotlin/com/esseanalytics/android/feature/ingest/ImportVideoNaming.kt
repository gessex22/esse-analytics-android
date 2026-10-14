package com.esseanalytics.android.feature.ingest

/** The display name is independent of the file's URI and stable content ID. */
internal object ImportVideoNaming {
    private val opaqueName = Regex("(?:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[0-9a-f]{32}|video_[0-9]{12,})", RegexOption.IGNORE_CASE)

    fun needsName(sourceName: String?): Boolean {
        val name = sourceName?.trim().orEmpty()
        return name.isEmpty() || opaqueName.matches(name.substringBeforeLast('.', name))
    }

    data class ReservedName(val fileName: String, val sequence: Long)

    fun nextName(lastSequence: Long, existingNames: Collection<String>, extension: String): ReservedName {
        val used = existingNames.map { it.substringBeforeLast('.', it).lowercase() }.toSet()
        var sequence = lastSequence.coerceAtLeast(0)
        do { sequence++ } while ("nuevo video $sequence" in used)
        return ReservedName("Nuevo video $sequence.$extension", sequence)
    }
}
