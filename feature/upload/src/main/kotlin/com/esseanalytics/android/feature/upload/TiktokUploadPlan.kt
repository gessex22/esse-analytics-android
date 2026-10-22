package com.esseanalytics.android.feature.upload

/** El mismo plan alimenta init y los PUT; el bloque final absorbe el resto. */
internal class TiktokUploadPlan(val videoSize: Long) {
    init {
        require(videoSize > 0) { "El archivo de video está vacío y no se puede subir a TikTok." }
    }

    private val nominalChunkSize = 10L * 1024 * 1024
    val totalChunks = (videoSize / nominalChunkSize).coerceAtLeast(1).toInt()
    val chunkSize = if (totalChunks == 1) videoSize else nominalChunkSize

    fun range(index: Int): LongRange {
        require(index in 0 until totalChunks)
        val start = index * chunkSize
        val end = if (index == totalChunks - 1) videoSize - 1 else start + chunkSize - 1
        return start..end
    }
}
