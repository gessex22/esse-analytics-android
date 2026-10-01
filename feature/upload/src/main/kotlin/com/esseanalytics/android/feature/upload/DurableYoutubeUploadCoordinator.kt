package com.esseanalytics.android.feature.upload

import com.esseanalytics.android.core.database.PublishOperationPolicy
import com.esseanalytics.android.core.database.PublishOperationStore
import com.esseanalytics.android.core.database.YouTubePublishDecision
import com.esseanalytics.android.core.database.YouTubeUploadCheckpoint
import com.esseanalytics.android.core.database.entity.PublishOperationEntity
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Coordinador durable de la subida resumible a YouTube: junta el journal
// (PublishOperationStore + PublishOperationPolicy) con el transporte puro
// (YoutubeResumableTransport) para que ningún reintento pueda duplicar una
// publicación — cada intento decide SOLO desde la fila persistida, cada punto
// de no retorno se escribe ANTES de la llamada remota correspondiente y los
// desenlaces Already*/Mark* resuelven sin tocar la red. Nunca reinicia el
// intento por su cuenta: la única vía a una sesión nueva es la transición
// explícita PublishOperationStore.startNewAttempt, por fuera de esta clase.
class DurableYoutubeUploadCoordinator(
    private val store: PublishOperationStore,
    private val transport: YoutubeResumableTransport,
) {

    suspend fun upload(
        file: File,
        metadata: UploadMetadata,
        userKey: String,
        sourceId: String,
        operationId: String,
        tokenProvider: suspend () -> String,
        onProgress: (Float) -> Unit = {},
    ): UploadResult {
        // Identidad y archivo se validan ANTES de journal y red: un argumento
        // vacío es un error del llamador, no un fallo reintentable.
        require(userKey.isNotBlank() && sourceId.isNotBlank()) {
            "userKey y sourceId tienen que ser no vacíos."
        }
        require(operationId.isNotBlank()) { "operationId tiene que ser no vacío." }
        val totalBytes = file.length()
        require(totalBytes > 0) { "El archivo a subir está vacío (${file.path})." }

        return try {
            uploadDurably(file, metadata, userKey, sourceId, operationId, tokenProvider, onProgress, totalBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Fallo del journal de persistencia — PublishOperationStoreException
            // del dominio o cualquier excepción cruda de DAO/Room que se le
            // escape al store: ruidoso en el store, acá se traduce a fallo NO
            // reintentable — jamás se interpreta como "seguí como si el
            // checkpoint hubiera quedado". El contorno es solo uploadDurably:
            // los errores remotos retryables ya los clasifica
            // YoutubeResumableTransport en RetryableFailure (nunca saltan por
            // excepción) y el tokenProvider tiene su propio manejo en
            // startFresh, así que un catch genérico acá no convierte fallos
            // remotos reintentables en permanentes.
            UploadResult.Failure(e.message ?: "Fallo de persistencia del journal.", retryable = false)
        }
    }

    private suspend fun uploadDurably(
        file: File,
        metadata: UploadMetadata,
        userKey: String,
        sourceId: String,
        operationId: String,
        tokenProvider: suspend () -> String,
        onProgress: (Float) -> Unit,
        totalBytes: Long,
    ): UploadResult {
        val row = store.getOrCreate(userKey, sourceId, PLATFORM_YOUTUBE, operationId)
        val decision = PublishOperationPolicy.decide(
            phase = row.phase,
            ytSessionURL = row.ytSessionURL,
            ytBytesConfirmed = row.ytBytesConfirmed,
            ytTotalBytes = row.ytTotalBytes,
            ytFinalChunkSent = row.ytFinalChunkSent,
            resultPlatformId = row.resultPlatformId,
            resultURL = row.resultURL,
            lastError = row.lastError,
        )
        return when (decision) {
            is YouTubePublishDecision.AlreadyConfirmed ->
                // El éxito se resuelve cero-red desde el journal, pero la
                // identidad que viaja es SIEMPRE la de la fila durable — el
                // operationId del batch de UI nunca reemplaza al persistido.
                UploadResult.Success(
                    decision.platformId,
                    decision.url,
                    durableOperationId = row.operationId,
                )
            is YouTubePublishDecision.AlreadyBlocked ->
                UploadResult.Failure(decision.message, retryable = false)
            is YouTubePublishDecision.AlreadyInvalidated ->
                UploadResult.Failure(decision.message, retryable = false)
            is YouTubePublishDecision.MarkBlocked -> {
                persist(YouTubeUploadCheckpoint.Blocked(decision.message), userKey, sourceId)
                UploadResult.Failure(decision.message, retryable = false)
            }
            is YouTubePublishDecision.MarkInvalidated -> {
                persist(YouTubeUploadCheckpoint.SessionInvalidated(decision.message), userKey, sourceId)
                UploadResult.Failure(decision.message, retryable = false)
            }
            is YouTubePublishDecision.StartFresh ->
                startFresh(
                    metadata,
                    file,
                    totalBytes,
                    userKey,
                    sourceId,
                    row.operationId,
                    tokenProvider,
                    onProgress,
                )
            is YouTubePublishDecision.Resume ->
                resume(decision, row, file, totalBytes, userKey, sourceId, onProgress)
        }
    }

    // Único camino que toca el token y el POST de apertura. Un RetryableFailure
    // deja la fila `pending` intacta: el próximo intento decide de nuevo desde
    // el journal, sin sesión huérfana.
    private suspend fun startFresh(
        metadata: UploadMetadata,
        file: File,
        totalBytes: Long,
        userKey: String,
        sourceId: String,
        durableOperationId: String,
        tokenProvider: suspend () -> String,
        onProgress: (Float) -> Unit,
    ): UploadResult {
        val token = try {
            tokenProvider()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return UploadResult.Failure(
                "No se pudo obtener el token de YouTube: ${e.message ?: "error de red"}.",
                retryable = true,
            )
        } catch (e: Exception) {
            return UploadResult.Failure(
                "El proveedor de tokens rechazó la solicitud: ${e.message ?: e.javaClass.simpleName}.",
                retryable = false,
            )
        }

        return when (val created = io { transport.createSession(token, metadata, totalBytes) }) {
            is YoutubeResumableResult.Created -> {
                // SessionCreated se persiste ANTES de que salga el primer byte.
                persist(
                    YouTubeUploadCheckpoint.SessionCreated(created.sessionUrl, totalBytes),
                    userKey,
                    sourceId,
                )
                sendFromOffset(created.sessionUrl, file, 0L, totalBytes, userKey, sourceId, durableOperationId, onProgress)
            }
            is YoutubeResumableResult.RetryableFailure ->
                UploadResult.Failure(created.message, retryable = true)
            is YoutubeResumableResult.PermanentFailure ->
                UploadResult.Failure(created.message, retryable = false)
            // Completed/Incomplete/Gone no existen en el POST de apertura: un
            // transporte que los devuelve está rompiendo el contrato.
            else -> UploadResult.Failure(
                "Desenlace imposible al crear la sesión de YouTube: $created.",
                retryable = false,
            )
        }
    }

    private suspend fun resume(
        decision: YouTubePublishDecision.Resume,
        row: PublishOperationEntity,
        file: File,
        totalBytes: Long,
        userKey: String,
        sourceId: String,
        onProgress: (Float) -> Unit,
    ): UploadResult {
        // El total persistido tiene que seguir describiendo ESTE archivo: si
        // cambió, la sesión guardada ya no es reanudable. El desenlace depende
        // del punto de no retorno: con el final efectivo mandado la
        // inconsistencia es ambigua (blocked); sin él es falla segura
        // (sessionInvalidated). Cero red en ambos.
        val storedTotal = decision.totalBytes
        if (storedTotal == null || storedTotal != totalBytes) {
            val mensaje = "El tamaño del archivo ($totalBytes bytes) ya no coincide con el total " +
                "persistido de la sesión (${storedTotal ?: "nulo"}): la sesión guardada no es reanudable."
            return if (decision.finalChunkSent) {
                persist(YouTubeUploadCheckpoint.Blocked(mensaje), userKey, sourceId)
                UploadResult.Failure(mensaje, retryable = false)
            } else {
                persist(YouTubeUploadCheckpoint.SessionInvalidated(mensaje), userKey, sourceId)
                UploadResult.Failure(mensaje, retryable = false)
            }
        }

        // La sesión guardada pudo avanzar o morir mientras el proceso no
        // estaba: SIEMPRE se consulta antes de mandar un solo byte.
        return when (val query = io { transport.querySession(decision.sessionUrl, totalBytes) }) {
            is YoutubeResumableResult.Completed -> {
                // Evidencia explícita de publicación. Si la fila todavía está
                // en sessionCreated, se marca el final antes del confirmed —
                // el store no confirma sin esa evidencia.
                if (row.phase == PublishOperationEntity.PHASE_SESSION_CREATED) {
                    persist(YouTubeUploadCheckpoint.FinalChunkSent, userKey, sourceId)
                }
                confirm(query.platformId, userKey, sourceId, row.operationId)
            }
            is YoutubeResumableResult.Incomplete ->
                resumeFromQueryProgress(decision, row, query, file, totalBytes, userKey, sourceId, onProgress)
            is YoutubeResumableResult.Gone ->
                if (decision.finalChunkSent) {
                    persist(YouTubeUploadCheckpoint.Blocked(query.message), userKey, sourceId)
                    UploadResult.Failure(query.message, retryable = false)
                } else {
                    persist(YouTubeUploadCheckpoint.SessionInvalidated(query.message), userKey, sourceId)
                    UploadResult.Failure(query.message, retryable = false)
                }
            is YoutubeResumableResult.RetryableFailure ->
                // Sin cambios del journal: la fila queda donde estaba y el
                // próximo intento vuelve a consultar la MISMA sesión (nunca a
                // crear otra, incluso post-final).
                UploadResult.Failure(query.message, retryable = true)
            is YoutubeResumableResult.PermanentFailure ->
                UploadResult.Failure(query.message, retryable = false)
            else -> UploadResult.Failure(
                "Desenlace imposible al consultar la sesión de YouTube: $query.",
                retryable = false,
            )
        }
    }

    private suspend fun resumeFromQueryProgress(
        decision: YouTubePublishDecision.Resume,
        row: PublishOperationEntity,
        query: YoutubeResumableResult.Incomplete,
        file: File,
        totalBytes: Long,
        userKey: String,
        sourceId: String,
        onProgress: (Float) -> Unit,
    ): UploadResult {
        val journalBytes = decision.bytesConfirmed
        if (query.confirmedBytes < journalBytes) {
            // El servidor reportó MENOS de lo que el journal ya tenía: no es
            // un reintento, es corrupción de estado.
            val mensaje = "El servidor reportó ${query.confirmedBytes} bytes confirmados, " +
                "menos que los $journalBytes del journal."
            persist(YouTubeUploadCheckpoint.Blocked(mensaje), userKey, sourceId)
            return UploadResult.Failure(mensaje, retryable = false)
        }
        if (row.phase == PublishOperationEntity.PHASE_FINAL_CHUNK_SENT) {
            // 308 posterior al chunk final con Range válido: el final NO
            // completó la subida — única reversa del journal. Se conservan
            // sesión, total y operationId; se reanuda desde el byte que el
            // servidor confirmó.
            persist(YouTubeUploadCheckpoint.FinalChunkNotCompleted(query.confirmedBytes), userKey, sourceId)
        } else if (query.confirmedBytes > journalBytes) {
            persist(YouTubeUploadCheckpoint.Progress(query.confirmedBytes), userKey, sourceId)
        }
        return sendFromOffset(
            decision.sessionUrl,
            file,
            query.confirmedBytes,
            totalBytes,
            userKey,
            sourceId,
            row.operationId,
            onProgress,
        )
    }

    // Envío desde un offset hasta el final del archivo: SIEMPRE es el chunk
    // final, así que FinalChunkSent se persiste ANTES de la llamada.
    private suspend fun sendFromOffset(
        sessionUrl: String,
        file: File,
        offsetBytes: Long,
        totalBytes: Long,
        userKey: String,
        sourceId: String,
        durableOperationId: String,
        onProgress: (Float) -> Unit,
    ): UploadResult {
        persist(YouTubeUploadCheckpoint.FinalChunkSent, userKey, sourceId)
        return when (val result = io {
            transport.uploadFromOffset(sessionUrl, file, offsetBytes, totalBytes, onProgress)
        }) {
            is YoutubeResumableResult.Completed -> confirm(result.platformId, userKey, sourceId, durableOperationId)
            is YoutubeResumableResult.Incomplete -> {
                persist(YouTubeUploadCheckpoint.FinalChunkNotCompleted(result.confirmedBytes), userKey, sourceId)
                UploadResult.Failure(
                    "El chunk final no completó la subida (${result.confirmedBytes} de $totalBytes bytes): " +
                        "el próximo intento consulta la sesión antes de reenviar.",
                    retryable = true,
                )
            }
            is YoutubeResumableResult.Gone -> {
                persist(YouTubeUploadCheckpoint.Blocked(result.message), userKey, sourceId)
                UploadResult.Failure(result.message, retryable = false)
            }
            is YoutubeResumableResult.PermanentFailure -> {
                persist(YouTubeUploadCheckpoint.Blocked(result.message), userKey, sourceId)
                UploadResult.Failure(result.message, retryable = false)
            }
            is YoutubeResumableResult.RetryableFailure ->
                // FinalChunkSent ya quedó persistido: la respuesta se perdió
                // después del punto de no retorno y el próximo intento solo
                // vuelve a consultar la sesión.
                UploadResult.Failure(result.message, retryable = true)
            else -> UploadResult.Failure(
                "Desenlace imposible al subir a YouTube: $result.",
                retryable = false,
            )
        }
    }

    // El confirmed se persiste ANTES de devolver el éxito: un proceso que
    // muera después de esta llamada encuentra el resultado durable. La
    // identidad que acompaña al Success es siempre la operationId de la fila
    // del journal (nunca la del batch de UI que llegó en este intento).
    private suspend fun confirm(
        platformId: String,
        userKey: String,
        sourceId: String,
        durableOperationId: String,
    ): UploadResult {
        val url = "$SHORTS_URL_BASE$platformId"
        persist(YouTubeUploadCheckpoint.Confirmed(platformId, url), userKey, sourceId)
        return UploadResult.Success(platformId, url, durableOperationId = durableOperationId)
    }

    private suspend fun persist(checkpoint: YouTubeUploadCheckpoint, userKey: String, sourceId: String) {
        store.checkpointYouTube(checkpoint, userKey, sourceId, PLATFORM_YOUTUBE)
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val PLATFORM_YOUTUBE = "youtube"
        const val SHORTS_URL_BASE = "https://youtube.com/shorts/"
    }
}
