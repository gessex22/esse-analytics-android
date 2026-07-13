package com.esseanalytics.android.core.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

// Recorte SIN recodificar (MediaExtractor + MediaMuxer, remux de las
// muestras ya comprimidas) -- ningún ffmpeg de por medio, decisión tomada
// con el usuario tras revisar las licencias de los forks disponibles
// (libx264, que hubiera hecho falta para Normalize, es GPL; una app
// comercial de código cerrado no puede empaquetarlo). Mismo criterio que
// desktop (trimToMaxDuration en video-normalize.service.ts): stream-copy,
// clip clampeado a [1,60]s, timestamps arrancando en 0.
@Singleton
class AndroidTrimProcessor @Inject constructor() : TrimProcessor {

    override suspend fun trim(input: File, output: File, startSec: Double, maxDurationSec: Double): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val startUs = (startSec.coerceAtLeast(0.0) * MICROS_PER_SEC).toLong()
                val endUs = startUs + (maxDurationSec.coerceIn(1.0, 60.0) * MICROS_PER_SEC).toLong()

                val extractor = MediaExtractor()
                val muxer: MediaMuxer
                try {
                    extractor.setDataSource(input.absolutePath)
                    output.parentFile?.mkdirs()
                    muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                } catch (e: Exception) {
                    extractor.release()
                    throw e
                }

                try {
                    // extractorTrackIndex -> muxerTrackIndex -- solo se
                    // seleccionan video/audio, cualquier otro track (subs,
                    // metadata) se ignora, igual que el -c copy de ffmpeg
                    // solo toca los streams que le interesan.
                    val trackIndexMap = HashMap<Int, Int>()
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                            trackIndexMap[i] = muxer.addTrack(format)
                            extractor.selectTrack(i)
                        }
                    }
                    check(trackIndexMap.isNotEmpty()) { "El video no tiene tracks de video/audio legibles." }

                    muxer.start()

                    val buffer = ByteBuffer.allocate(BUFFER_SIZE)
                    val bufferInfo = MediaCodec.BufferInfo()

                    // MediaMuxer no puede arrancar un video en medio de un
                    // GOP -- el seek cae en el keyframe más cercano ANTES de
                    // startUs (puede quedar algo antes de lo pedido, mismo
                    // trade-off que ffmpeg -ss antes del -i vs después).
                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                    while (true) {
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleTimeUs < 0 || sampleTimeUs > endUs) break

                        val muxerTrackIndex = trackIndexMap[extractor.sampleTrackIndex]
                        if (muxerTrackIndex == null) {
                            extractor.advance()
                            continue
                        }

                        buffer.clear()
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        // avoid_negative_ts make_zero: relativo al startUs
                        // elegido, nunca negativo (samples entre el keyframe
                        // de seek y el startUs real quedan en 0).
                        bufferInfo.presentationTimeUs = (sampleTimeUs - startUs).coerceAtLeast(0)
                        bufferInfo.flags = extractor.sampleFlags

                        muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                        extractor.advance()
                    }

                    muxer.stop()
                } finally {
                    muxer.release()
                    extractor.release()
                }
            }
        }

    private companion object {
        const val MICROS_PER_SEC = 1_000_000.0
        const val BUFFER_SIZE = 2 * 1024 * 1024
    }
}
