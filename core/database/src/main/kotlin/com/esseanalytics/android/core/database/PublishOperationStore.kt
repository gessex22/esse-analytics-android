package com.esseanalytics.android.core.database

import com.esseanalytics.android.core.database.dao.PublishOperationDao
import com.esseanalytics.android.core.database.entity.PublishOperationEntity
import com.esseanalytics.android.core.database.entity.PublishOperationEntity.Companion.CONFIRMED_NOT_PUBLISHED
import com.esseanalytics.android.core.database.entity.PublishOperationEntity.Companion.PHASE_BLOCKED
import com.esseanalytics.android.core.database.entity.PublishOperationEntity.Companion.PHASE_CONFIRMED
import com.esseanalytics.android.core.database.entity.PublishOperationEntity.Companion.PHASE_CONTAINER_CREATED
import com.esseanalytics.android.core.database.entity.PublishOperationEntity.Companion.PHASE_FINAL_CHUNK_SENT
import com.esseanalytics.android.core.database.entity.PublishOperationEntity.Companion.PHASE_PENDING
import com.esseanalytics.android.core.database.entity.PublishOperationEntity.Companion.PHASE_PUBLISH_REQUESTED
import com.esseanalytics.android.core.database.entity.PublishOperationEntity.Companion.PHASE_SESSION_CREATED
import com.esseanalytics.android.core.database.entity.PublishOperationEntity.Companion.PHASE_SESSION_INVALIDATED
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

// Fallos de persistencia/transición SIEMPRE ruidosos: un `find` que no
// encuentra la fila donde un checkpoint esperaba una, o una transición que no
// aplica, abortan la subida entera. Nada de esto se trata como "no pasa nada"
// — un checkpoint que no quedó escrito no puede dejar seguir mandando bytes
// como si hubiera quedado (especialmente ANTES del punto de no retorno de
// cada plataforma). Mismo contrato que PublishOperationStore.swift (iOS).
sealed class PublishOperationStoreException(message: String) : Exception(message) {
    class RowMissing : PublishOperationStoreException(
        "No se encontró el registro de la publicación en curso.",
    )

    class TransitionNotApplicable(message: String) : PublishOperationStoreException(message)
}

// Checkpoints de YouTube. Cada uno se persiste ANTES de la llamada remota
// irreversible correspondiente (FinalChunkSent antes del último chunk,
// SessionCreated antes del primer byte); la excepción es
// FinalChunkNotCompleted, que registra DESPUÉS la respuesta 308 a ese último
// chunk. La definición vive acá (no en feature:upload) porque el journal es
// dueño del contrato de durabilidad.
sealed interface YouTubeUploadCheckpoint {
    data class SessionCreated(val sessionUrl: String, val totalBytes: Long?) : YouTubeUploadCheckpoint
    data class Progress(val bytesConfirmed: Long) : YouTubeUploadCheckpoint
    object FinalChunkSent : YouTubeUploadCheckpoint
    // Un 308 posterior al último chunk con Range válido probó que el final NO
    // completó la subida: la sesión sigue viva y se reanuda desde el byte que
    // el servidor confirmó. Única reversa permitida del journal.
    data class FinalChunkNotCompleted(val bytesConfirmed: Long) : YouTubeUploadCheckpoint
    data class Confirmed(val platformId: String, val url: String) : YouTubeUploadCheckpoint
    data class Blocked(val message: String) : YouTubeUploadCheckpoint
    data class SessionInvalidated(val message: String) : YouTubeUploadCheckpoint
}

// Checkpoints de Instagram. PublishRequested se persiste ANTES de pedir
// media_publish — Meta no ofrece forma de reconciliar ese llamado sin
// repetirlo (duplicaría la publicación).
sealed interface InstagramUploadCheckpoint {
    data class StageStarting(val stage: InstagramUploadStage) : InstagramUploadCheckpoint
    data class ContainerCreated(val containerId: String, val uploadUri: String) : InstagramUploadCheckpoint
    object PublishRequested : InstagramUploadCheckpoint
    // `url` nullable a propósito: el mediaId se persiste ANTES de fetchear el
    // permalink (una llamada extra a la API de Meta), así la confirmación
    // puede completarse con platformId solo y la URL llega después.
    data class Confirmed(val platformId: String, val url: String?) : InstagramUploadCheckpoint
    data class Blocked(val message: String) : InstagramUploadCheckpoint
}

// Acceso al journal anti-duplicados (ver PublishOperationEntity). Reglas
// duras, todas verificadas ANTES de escribir:
//  - getOrCreate NUNCA toca una fila existente: operationId es durable y el
//    operationId del batch de UI no autoriza a reemplazar un journal
//    existente (divergencia a propósito con iOS, que lo refrescaba).
//  - startNewAttempt es una vía EXPLÍCITA a un operationId nuevo, y solo desde
//    SESSION_INVALIDATED sin evidencia fuerte de publicación.
//  - confirmNotPublished es la otra vía EXPLÍCITA a un operationId nuevo, y
//    solo desde BLOCKED: la confirmación humana «Revisé la plataforma y el
//    video no se publicó» (diseño docs/ambiguous-publish-recovery-design-
//    2026-09-22.md). Una fila `blocked` jamás se reintenta sola.
//  - Las fases ambiguas (publishRequested/finalChunkSent) jamás retroceden a
//    pending ni cambian de operationId: solo pueden avanzar a confirmed (con
//    evidencia explícita válida) o a blocked (la política devuelve MarkBlocked
//    exactamente desde estados corruptos de esas fases) — nunca a red ni
//    reinicio. Única excepción: finalChunkSent vuelve a sessionCreated vía
//    FinalChunkNotCompleted (un 308 válido probó que el final no completó).
//    confirmed/blocked ya asentados no se mueven de ninguna forma, salvo la
//    transición confirmNotPublished que SÍ sale de blocked (y únicamente de
//    blocked — una fila confirmed jamás entra por ese flujo).
//  - Cada mutación escribe una COPIA inmutable por el DAO y solo devuelve
//    éxito después de persistir.
@Singleton
class PublishOperationStore @Inject constructor(
    private val dao: PublishOperationDao,
) {
    // Plataformas del journal como constantes locales puras (mismos apiValue
    // que Platform en core:model) — el store no depende de otros módulos y
    // los tests JVM no levantan nada.
    private companion object {
        const val PLATFORM_YOUTUBE = "youtube"
        const val PLATFORM_INSTAGRAM = "instagram"
    }

    // La fila vigente para reanudar — la crea si no existe. Si existe, se
    // devuelve INTACTA aunque el caller pase otro operationId: reintentar
    // declara la MISMA operación mientras no pase por startNewAttempt, y el
    // operationId que viaja es siempre el de la fila.
    suspend fun getOrCreate(
        userKey: String,
        sourceId: String,
        platform: String,
        operationId: String,
    ): PublishOperationEntity {
        requireKeyParts(userKey, sourceId, platform)
        if (operationId.isBlank()) throw IllegalArgumentException("operationId tiene que ser no vacío.")
        dao.find(userKey, sourceId, platform)?.let { return it }
        val now = System.currentTimeMillis()
        val row = PublishOperationEntity(
            userKey = userKey,
            sourceId = sourceId,
            platform = platform,
            operationId = operationId,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        // ABORT: una colisión de la clave única (carrera con otro insert)
        // revienta acá y se propaga — aceptado a propósito en esta subfase;
        // nunca se resuelve pisando la fila existente.
        val insertedId = dao.insert(row)
        // El id real lo asigna la base (autogenerate): devolver la fila con
        // id=0 rompería cualquier correlación posterior por id.
        return row.copy(id = insertedId)
    }

    // Transición EXPLÍCITA a un intento nuevo — nunca implícita dentro de un
    // mismo intento. Solo aplica si la fila está en SESSION_INVALIDATED Y no
    // tiene evidencia fuerte de posible publicación (el chunk final nunca se
    // mandó, no hay resultado y los bytes confirmados no cubren el total).
    // confirmed/blocked/publishRequested/finalChunkSent no pueden pasar por
    // acá — reemplazarlas sería pisar un resultado verificado o un desenlace
    // ambiguo sin que el usuario lo haya verificado.
    //
    // La fila resultante queda COMPLETAMENTE limpia para reintentar: se
    // limpian SOLO los campos de sesión YouTube permitidos (yt*) y los de
    // resultado/error (nil exacto, no vacío — un vacío rompería la
    // reclasificación como pending limpio en el próximo decide()), y recibe
    // el operationId NUEVO que el llamador genera para este intento. Los
    // campos de Instagram (ig*) no se tocan: no aplican a esta transición.
    suspend fun startNewAttempt(
        userKey: String,
        sourceId: String,
        platform: String,
        newOperationId: String,
    ): PublishOperationEntity {
        requireKeyParts(userKey, sourceId, platform)
        if (newOperationId.isBlank()) throw IllegalArgumentException("newOperationId tiene que ser no vacío.")
        val row = dao.find(userKey, sourceId, platform) ?: throw PublishOperationStoreException.RowMissing()
        if (row.platform != PLATFORM_YOUTUBE) {
            throw PublishOperationStoreException.TransitionNotApplicable(
                "startNewAttempt es una transición del journal de YouTube (plataforma: ${row.platform}).",
            )
        }
        if (row.phase != PHASE_SESSION_INVALIDATED) {
            throw PublishOperationStoreException.TransitionNotApplicable(
                "startNewAttempt solo aplica desde sessionInvalidated (fase actual: ${row.phase}).",
            )
        }
        if (PublishOperationPolicy.hasStrongPublicationEvidence(
                ytFinalChunkSent = row.ytFinalChunkSent,
                ytBytesConfirmed = row.ytBytesConfirmed,
                ytTotalBytes = row.ytTotalBytes,
                resultPlatformId = row.resultPlatformId,
                resultURL = row.resultURL,
            )
        ) {
            throw PublishOperationStoreException.TransitionNotApplicable(
                "startNewAttempt no aplica: hay evidencia fuerte de que la sesión anterior pudo publicar.",
            )
        }
        val cleaned = row.copy(
            operationId = newOperationId,
            phase = PHASE_PENDING,
            ytSessionURL = null,
            ytBytesConfirmed = 0,
            ytTotalBytes = null,
            ytFinalChunkSent = false,
            resultPlatformId = null,
            resultURL = null,
            lastError = null,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        dao.update(cleaned)
        return cleaned
    }

    // Transición EXPLÍCITA de recuperación humana desde un desenlace ambiguo
    // — la ÚNICA vía a un intento nuevo desde una fila `blocked` (el punto de
    // no retorno de la plataforma se cruzó pero nunca llegó evidencia de
    // publicación, así que reintentar a ciegas podría duplicar el video). Ver
    // el diseño docs/ambiguous-publish-recovery-design-2026-09-22.md
    // («Recuperación de publicación ambigua sin duplicados»): NUNCA se
    // reintenta automáticamente una operación blocked; se habilita UN intento
    // nuevo limpio solo después de la confirmación humana «Revisé la
    // plataforma y el video no se publicó». La persona —no la app— es quien
    // verificó en la plataforma que el video efectivamente NO está público;
    // si SÍ aparece, corresponde vincularlo por el flujo de Matching, jamás
    // re-subirlo.
    //
    // Reglas duras (verificadas ANTES de escribir, fallo ruidoso igual que el
    // resto del store):
    //  - Solo acepta filas en PHASE_BLOCKED. `confirmed` es un desenlace
    //    asentado con evidencia real: UNA FILA CONFIRMED JAMÁS ENTRA ACÁ —
    //    pasarla por este flujo sería pisar un resultado verificado con una
    //    suposición humana. `pending` y el resto de las fases tampoco aplican:
    //    no hay desenlace ambiguo que recuperar.
    //  - La validación de userKey es la misma que en el resto del store
    //    (requireKeyParts: partes de la clave no vacías); el userKey del
    //    usuario actual lo resuelve el llamador desde TokenStore, como hace
    //    UploadWorker para la subida durable. userKey es parte de la clave
    //    del find: un userKey ajeno simplemente NO encuentra la fila
    //    (RowMissing) en vez de tocar la fila de otro usuario.
    //  - newOperationId lo genera el llamador (el ViewModel, como ya hace
    //    UploadViewModel.publish para el lote): el journal nunca inventa ids.
    //
    // La fila resultante queda COMPLETAMENTE limpia para el intento nuevo: se
    // resetean TODOS los handles remotos y el progreso (yt* e ig*, nil
    // exacto / 0 / false — no vacío) y los resultados, así el próximo
    // decide() reclasifica la fila como `pending` limpio (StartFresh) y el
    // intento arranca con sesión/contenedor NUEVOS, sin reusar el handle
    // ambiguo. lastError se PRESERVA a propósito: es la evidencia del mensaje
    // original que llevó al bloqueo (soporte), no un handle reutilizable. Y se
    // fija la evidencia local de la confirmación: lastConfirmationAction =
    // CONFIRMED_NOT_PUBLISHED + su timestamp (plataforma, archivo y fecha ya
    // viven en la fila). No toca badges ni registra publicación central:
    // todavía no existe un video remoto confirmado.
    suspend fun confirmNotPublished(
        userKey: String,
        sourceId: String,
        platform: String,
        newOperationId: String,
    ): PublishOperationEntity {
        requireKeyParts(userKey, sourceId, platform)
        if (newOperationId.isBlank()) throw IllegalArgumentException("newOperationId tiene que ser no vacío.")
        val row = dao.find(userKey, sourceId, platform) ?: throw PublishOperationStoreException.RowMissing()
        if (row.phase != PHASE_BLOCKED) {
            throw PublishOperationStoreException.TransitionNotApplicable(
                "confirmNotPublished solo aplica desde blocked (fase actual: ${row.phase}). " +
                    "Un desenlace confirmed jamás pasa por este flujo.",
            )
        }
        val now = System.currentTimeMillis()
        val confirmed = row.copy(
            operationId = newOperationId,
            phase = PHASE_PENDING,
            ytSessionURL = null,
            ytBytesConfirmed = 0,
            ytTotalBytes = null,
            ytFinalChunkSent = false,
            igStage = null,
            igContainerId = null,
            igUploadURI = null,
            igPublishRequested = false,
            resultPlatformId = null,
            resultURL = null,
            // lastError se conserva: evidencia del bloqueo original para
            // soporte, no un handle reutilizable.
            lastConfirmationAction = CONFIRMED_NOT_PUBLISHED,
            lastConfirmationAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        dao.update(confirmed)
        return confirmed
    }

    // Filas `blocked` del usuario, vivas (Flow reactivo de Room): alimentan la
    // UI de recuperación (qué plataformas del lote/archivo actual quedaron con
    // desenlace ambiguo y pueden confirmarse, ver UploadViewModel/
    // UploadScreen). Solo lectura: ninguna mutación del journal pasa por acá.
    fun observeBlocked(userKey: String): Flow<List<PublishOperationEntity>> {
        if (userKey.isBlank()) throw IllegalArgumentException("userKey tiene que ser no vacío.")
        return dao.observeBlocked(userKey)
    }

    // Aplica un checkpoint de YouTube a la fila. Lanza RowMissing si la fila
    // no está y TransitionNotApplicable si la transición no aplica a la fase
    // actual — el uploader aborta la subida entera ante cualquiera de los dos.
    suspend fun checkpointYouTube(
        checkpoint: YouTubeUploadCheckpoint,
        userKey: String,
        sourceId: String,
        platform: String,
    ): PublishOperationEntity {
        val row = dao.find(userKey, sourceId, platform) ?: throw PublishOperationStoreException.RowMissing()
        if (row.platform != PLATFORM_YOUTUBE) {
            throw PublishOperationStoreException.TransitionNotApplicable(
                "checkpoint de YouTube sobre una fila de ${row.platform}.",
            )
        }
        val next = when (checkpoint) {
            is YouTubeUploadCheckpoint.SessionCreated -> {
                requirePhase(row, setOf(PHASE_PENDING)) {
                    "sessionCreated solo aplica desde pending limpio (fase actual: ${row.phase})."
                }
                row.copy(
                    phase = PHASE_SESSION_CREATED,
                    ytSessionURL = checkpoint.sessionUrl,
                    ytTotalBytes = checkpoint.totalBytes,
                    ytBytesConfirmed = 0,
                    ytFinalChunkSent = false,
                    lastError = null,
                )
            }
            is YouTubeUploadCheckpoint.Progress -> {
                requirePhase(row, setOf(PHASE_SESSION_CREATED)) {
                    "progress solo aplica con sesión creada (fase actual: ${row.phase})."
                }
                val total = row.ytTotalBytes
                // Monotónico a propósito: un "progreso" menor que lo ya
                // confirmado no es un reintento, es corrupción de estado.
                if (checkpoint.bytesConfirmed < 0 ||
                    checkpoint.bytesConfirmed < row.ytBytesConfirmed ||
                    (total != null && checkpoint.bytesConfirmed > total)
                ) {
                    throw PublishOperationStoreException.TransitionNotApplicable(
                        "bytes confirmados inválidos o no monotónicos " +
                            "(${checkpoint.bytesConfirmed} contra ${row.ytBytesConfirmed} confirmados / $total total).",
                    )
                }
                row.copy(ytBytesConfirmed = checkpoint.bytesConfirmed)
            }
            YouTubeUploadCheckpoint.FinalChunkSent -> {
                requirePhase(row, setOf(PHASE_SESSION_CREATED)) {
                    "finalChunkSent solo aplica con sesión creada (fase actual: ${row.phase})."
                }
                row.copy(phase = PHASE_FINAL_CHUNK_SENT, ytFinalChunkSent = true)
            }
            is YouTubeUploadCheckpoint.FinalChunkNotCompleted -> {
                // Única reversa permitida: un 308 con Range válido tras el
                // último chunk prueba que el final NO completó la subida — la
                // sesión sigue viva y se reanuda. Conserva sesión, total y
                // operationId; solo corrige los campos del intento de final.
                requirePhase(row, setOf(PHASE_FINAL_CHUNK_SENT)) {
                    "finalChunkNotCompleted solo aplica desde finalChunkSent (fase actual: ${row.phase})."
                }
                val total = row.ytTotalBytes
                // El valor confirmado por el 308 tiene que ser plausible:
                // monotónico respecto de lo ya confirmado y, si el total se
                // conoce, estrictamente menor que él (llegar al total SIN
                // completar es imposible — eso sería el final completo).
                if (checkpoint.bytesConfirmed < 0 ||
                    checkpoint.bytesConfirmed < row.ytBytesConfirmed ||
                    (total != null && checkpoint.bytesConfirmed >= total)
                ) {
                    throw PublishOperationStoreException.TransitionNotApplicable(
                        "bytes confirmados por el 308 inválidos o no monotónicos " +
                            "(${checkpoint.bytesConfirmed} contra ${row.ytBytesConfirmed} confirmados / $total total).",
                    )
                }
                row.copy(
                    phase = PHASE_SESSION_CREATED,
                    ytBytesConfirmed = checkpoint.bytesConfirmed,
                    ytFinalChunkSent = false,
                )
            }
            is YouTubeUploadCheckpoint.Confirmed -> {
                // confirmed solo con evidencia explícita: viene del final y
                // trae resultado válido. Nunca desde sessionCreated (podría ser
                // un 2xx imaginado) ni desde fases terminales/ambiguas.
                requirePhase(row, setOf(PHASE_FINAL_CHUNK_SENT)) {
                    "confirmed solo aplica desde finalChunkSent (fase actual: ${row.phase})."
                }
                if (checkpoint.platformId.isBlank() || checkpoint.url.isBlank()) {
                    throw PublishOperationStoreException.TransitionNotApplicable(
                        "confirmed exige platformId y url no vacíos.",
                    )
                }
                row.copy(
                    phase = PHASE_CONFIRMED,
                    resultPlatformId = checkpoint.platformId,
                    resultURL = checkpoint.url,
                    lastError = null,
                )
            }
            is YouTubeUploadCheckpoint.Blocked -> {
                requireNotSettled(row) { "blocked" }
                // Un desenlace ambiguo NUEVO supersedede cualquier confirmación
                // humana previa: esa confirmación hablaba del intento anterior,
                // y si quedara viva quedaría evidencia falsa sobre ESTE bloqueo
                // (mismo criterio que iOS, ver confirmNotPublished).
                row.copy(
                    phase = PHASE_BLOCKED,
                    lastError = checkpoint.message,
                    lastConfirmationAction = null,
                    lastConfirmationAtEpochMs = null,
                )
            }
            is YouTubeUploadCheckpoint.SessionInvalidated -> {
                // La sesión vieja ya no existe PERO el chunk final nunca se
                // mandó — falla segura. Aplica desde pending (restos de una
                // sesión anterior sin confirmar, sin evidencia fuerte) o con
                // sesión creada. Con evidencia fuerte no es falla segura sino
                // corrupción/ambigüedad: el llamador debe usar Blocked, no
                // esto.
                requirePhase(row, setOf(PHASE_PENDING, PHASE_SESSION_CREATED)) {
                    "sessionInvalidated solo aplica desde pending o sessionCreated (fase actual: ${row.phase})."
                }
                if (PublishOperationPolicy.hasStrongPublicationEvidence(
                        ytFinalChunkSent = row.ytFinalChunkSent,
                        ytBytesConfirmed = row.ytBytesConfirmed,
                        ytTotalBytes = row.ytTotalBytes,
                        resultPlatformId = row.resultPlatformId,
                        resultURL = row.resultURL,
                    )
                ) {
                    throw PublishOperationStoreException.TransitionNotApplicable(
                        "hay evidencia fuerte de posible publicación: corresponde blocked, no sessionInvalidated.",
                    )
                }
                row.copy(phase = PHASE_SESSION_INVALIDATED, lastError = checkpoint.message)
            }
        }
        dao.update(next)
        return next
    }

    // Aplica un checkpoint de Instagram a la fila. Mismo contrato que
    // checkpointYouTube: lanza si la fila no existe o si la transición no
    // aplica — en particular ANTES de media_publish, porque un
    // PublishRequested que no quedó escrito no puede dejar salir esa request.
    suspend fun checkpointInstagram(
        checkpoint: InstagramUploadCheckpoint,
        userKey: String,
        sourceId: String,
        platform: String,
    ): PublishOperationEntity {
        val row = dao.find(userKey, sourceId, platform) ?: throw PublishOperationStoreException.RowMissing()
        if (row.platform != PLATFORM_INSTAGRAM) {
            throw PublishOperationStoreException.TransitionNotApplicable(
                "checkpoint de Instagram sobre una fila de ${row.platform}.",
            )
        }
        val next = when (checkpoint) {
            is InstagramUploadCheckpoint.StageStarting -> {
                // Avance de etapa tras rechazo explícito de Meta: solo desde
                // pending o con un contenedor creado todavía no publicado.
                // Limpia cualquier contenedor de una etapa anterior — la fila
                // queda `pending` con la etapa nueva, así un reinicio a mitad
                // de un avance retoma ESTA etapa en vez de volver a original.
                requirePhase(row, setOf(PHASE_PENDING, PHASE_CONTAINER_CREATED)) {
                    "stageStarting solo aplica desde pending o containerCreated (fase actual: ${row.phase})."
                }
                row.copy(
                    phase = PHASE_PENDING,
                    igStage = checkpoint.stage.rawValue,
                    igContainerId = null,
                    igUploadURI = null,
                    igPublishRequested = false,
                    lastError = null,
                )
            }
            is InstagramUploadCheckpoint.ContainerCreated -> {
                requirePhase(row, setOf(PHASE_PENDING)) {
                    "containerCreated solo aplica desde pending (fase actual: ${row.phase})."
                }
                if (checkpoint.containerId.isBlank() ||
                    !PublishOperationPolicy.isValidHttpsUrl(checkpoint.uploadUri)
                ) {
                    throw PublishOperationStoreException.TransitionNotApplicable(
                        "containerCreated exige containerId no vacío y uploadUri https absoluta con host.",
                    )
                }
                row.copy(
                    phase = PHASE_CONTAINER_CREATED,
                    igContainerId = checkpoint.containerId,
                    igUploadURI = checkpoint.uploadUri,
                    igPublishRequested = false,
                    lastError = null,
                )
            }
            InstagramUploadCheckpoint.PublishRequested -> {
                // Punto de no retorno. Solo desde containerCreated, y una vez
                // acá jamás se reinicia (lo marca la propia fase publishRequested).
                requirePhase(row, setOf(PHASE_CONTAINER_CREATED)) {
                    "publishRequested solo aplica desde containerCreated (fase actual: ${row.phase})."
                }
                row.copy(phase = PHASE_PUBLISH_REQUESTED, igPublishRequested = true)
            }
            is InstagramUploadCheckpoint.Confirmed -> {
                requirePhase(row, setOf(PHASE_PUBLISH_REQUESTED)) {
                    "confirmed solo aplica desde publishRequested (fase actual: ${row.phase})."
                }
                // El mediaId se persiste ANTES de fetchar el permalink: la
                // confirmación vale con platformId aunque la URL todavía no
                // exista (puede ser null y completarse después).
                if (checkpoint.platformId.isBlank()) {
                    throw PublishOperationStoreException.TransitionNotApplicable(
                        "confirmed exige platformId no vacío (la URL del permalink puede llegar después).",
                    )
                }
                row.copy(
                    phase = PHASE_CONFIRMED,
                    resultPlatformId = checkpoint.platformId,
                    resultURL = checkpoint.url,
                    lastError = null,
                )
            }
            is InstagramUploadCheckpoint.Blocked -> {
                requireNotSettled(row) { "blocked" }
                // Mismo criterio que el checkpoint de YouTube: un bloqueo
                // nuevo supersedede la confirmación humana del intento anterior.
                row.copy(
                    phase = PHASE_BLOCKED,
                    lastError = checkpoint.message,
                    lastConfirmationAction = null,
                    lastConfirmationAtEpochMs = null,
                )
            }
        }
        dao.update(next)
        return next
    }

    private inline fun requirePhase(row: PublishOperationEntity, allowed: Set<String>, message: () -> String) {
        if (row.phase !in allowed) throw PublishOperationStoreException.TransitionNotApplicable(message())
    }

    private fun requireKeyParts(userKey: String, sourceId: String, platform: String) {
        if (userKey.isBlank() || sourceId.isBlank() || platform.isBlank()) {
            throw IllegalArgumentException("userKey, sourceId y platform tienen que ser no vacíos.")
        }
    }

    // confirmed/blocked ya son desenlaces asentados: no se bloquean "de
    // nuevo" ni se mueven. Las fases ambiguas (publishRequested/
    // finalChunkSent) SÍ pueden avanzar a blocked — la política devuelve
    // MarkBlocked exactamente desde estados corruptos de esas fases — pero
    // nunca a red ni a un reinicio.
    private inline fun requireNotSettled(
        row: PublishOperationEntity,
        checkpoint: () -> String,
    ) {
        if (row.phase == PHASE_CONFIRMED || row.phase == PHASE_BLOCKED) {
            throw PublishOperationStoreException.TransitionNotApplicable(
                "${checkpoint()} no aplica desde la fase ${row.phase}: es un desenlace asentado, no se mueve.",
            )
        }
    }
}
