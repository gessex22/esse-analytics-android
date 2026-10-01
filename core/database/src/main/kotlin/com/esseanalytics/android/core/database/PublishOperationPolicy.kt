package com.esseanalytics.android.core.database

import java.net.URI
import java.net.URISyntaxException

// Qué hacer con la fila del journal de un (sourceId, youtube) ANTES de tocar
// la red -- pura (sin Room ni transporte), para poder probar la política
// fail-closed en tests JVM sin Android. Referencia conceptual:
// PublishOperationPolicy.swift en essenalytics-ios.
//
// El único desenlace que habilita un POST de inicio nuevo es StartFresh, y
// sale ÚNICAMENTE de una fila que nunca existió o que está genuinamente
// `pending` sin ningún handle de sesión registrado. Cualquier otra
// combinación — una fase que dice que hubo sesión pero el handle es
// inválido, un resultado confirmado sin los campos que lo demuestran, una
// fase que ni siquiera es una de las conocidas — cae a un desenlace
// conservador que NUNCA abre una sesión nueva dentro de esta decisión. La
// única vía a un intento nuevo desde un estado no pending es
// PublishOperationStore.startNewAttempt, deliberadamente por FUERA de esta
// función (transición explícita que orquesta el llamador, nunca algo que la
// política decida sola ante un estado ambiguo).
sealed interface YouTubePublishDecision {
    object StartFresh : YouTubePublishDecision
    data class Resume(
        val sessionUrl: String,
        val bytesConfirmed: Long,
        val totalBytes: Long?,
        val finalChunkSent: Boolean,
    ) : YouTubePublishDecision

    data class AlreadyConfirmed(val platformId: String, val url: String) : YouTubePublishDecision
    data class AlreadyBlocked(val message: String) : YouTubePublishDecision
    // Recién se detecta la inconsistencia acá — el llamador la persiste
    // (checkpoint Blocked) antes de fallar.
    data class MarkBlocked(val message: String) : YouTubePublishDecision
    data class AlreadyInvalidated(val message: String) : YouTubePublishDecision
    // La sesión no sirve: falla segura (nunca hubo chunk final, así que
    // YouTube no pudo crear el video). El llamador la persiste (checkpoint
    // SessionInvalidated) y solo una transición explícita posterior puede
    // reintentar con handle nuevo.
    data class MarkInvalidated(val message: String) : YouTubePublishDecision
}

object PublishOperationPolicy {

    // `phase == null` significa "no hay fila todavía" — tan seguro de empezar
    // de cero como una fila `pending` sin sesión, así que se trata igual.
    fun decide(
        phase: String?,
        ytSessionURL: String?,
        ytBytesConfirmed: Long,
        ytTotalBytes: Long?,
        ytFinalChunkSent: Boolean,
        resultPlatformId: String?,
        resultURL: String?,
        lastError: String?,
    ): YouTubePublishDecision {
        if (phase == null) return YouTubePublishDecision.StartFresh

        val evidenciaFuerte = hasStrongPublicationEvidence(
            ytFinalChunkSent = ytFinalChunkSent,
            ytBytesConfirmed = ytBytesConfirmed,
            ytTotalBytes = ytTotalBytes,
            resultPlatformId = resultPlatformId,
            resultURL = resultURL,
        )

        return when (phase) {
            "pending" -> {
                // `pending` solo es "arrancá de cero" si está COMPLETAMENTE
                // limpia — sin sesión, sin bytes, sin total, sin final y sin
                // resultado. Cualquier resto es una inconsistencia, y no todos
                // pesan igual: evidencia FUERTE (YouTube pudo haber procesado
                // algo) es ambigua -> block; un resto débil es falla segura
                // pero tampoco arranca solo -> invalidated. Ninguno arranca
                // sola: solo la fila genuinamente limpia lo hace.
                val limpia = ytSessionURL == null &&
                    ytBytesConfirmed == 0L &&
                    ytTotalBytes == null &&
                    !ytFinalChunkSent &&
                    resultPlatformId == null &&
                    resultURL == null
                when {
                    limpia -> YouTubePublishDecision.StartFresh
                    evidenciaFuerte -> YouTubePublishDecision.MarkBlocked(
                        "Estado inconsistente: la publicación figura pendiente pero hay evidencia fuerte de " +
                            "que el chunk final se mandó, de un resultado previo, o de bytes ya completos.",
                    )
                    else -> YouTubePublishDecision.MarkInvalidated(
                        "Estado inconsistente: la publicación figura pendiente pero tiene restos de una " +
                            "sesión anterior sin confirmar.",
                    )
                }
            }

            "sessionCreated", "finalChunkSent" -> {
                // El desenlace "el final se mandó" no depende solo del flag
                // crudo NI solo del nombre de la fase — cualquiera de los dos,
                // o evidencia fuerte independiente, cuenta: confiar en un solo
                // campo dejaría que un 404/410 POSTERIOR se tratara como
                // pre-final (sessionInvalidated, que sí puede reintentar con
                // startNewAttempt) cuando en realidad el desenlace es ambiguo.
                val finalEfectivo = evidenciaFuerte || phase == "finalChunkSent"
                if (ytSessionURL == null || !isValidHttpsUrl(ytSessionURL)) {
                    val motivo = "El handle de sesión guardado no es válido o está incompleto."
                    return if (finalEfectivo) {
                        YouTubePublishDecision.MarkBlocked(motivo)
                    } else {
                        YouTubePublishDecision.MarkInvalidated(motivo)
                    }
                }
                YouTubePublishDecision.Resume(
                    sessionUrl = ytSessionURL,
                    bytesConfirmed = ytBytesConfirmed,
                    totalBytes = ytTotalBytes,
                    finalChunkSent = finalEfectivo,
                )
            }

            "confirmed" -> {
                val platformId = resultPlatformId
                val url = resultURL
                if (platformId.isNullOrEmpty() || url.isNullOrEmpty()) {
                    return YouTubePublishDecision.MarkBlocked(
                        "Estado inconsistente: la publicación figura confirmada pero sin los datos del resultado.",
                    )
                }
                YouTubePublishDecision.AlreadyConfirmed(platformId = platformId, url = url)
            }

            "blocked" -> YouTubePublishDecision.AlreadyBlocked(
                lastError ?: "La publicación quedó bloqueada por un desenlace remoto sin confirmar.",
            )

            "sessionInvalidated" -> {
                // SESSION_INVALIDATED solo puede significar "nunca se mandó el
                // chunk final y no hay resultado". Con evidencia fuerte la fila
                // está corrupta: no se puede confiar en que la sesión no haya
                // producido un video -> block (y jamás pasa por
                // startNewAttempt). Solo sin evidencia es falla segura de verdad.
                if (evidenciaFuerte) {
                    return YouTubePublishDecision.MarkBlocked(
                        "Estado inconsistente: la publicación quedó marcada como sesión inválida, pero hay " +
                            "evidencia fuerte de que el chunk final se mandó o de un resultado previo.",
                    )
                }
                YouTubePublishDecision.AlreadyInvalidated(
                    lastError ?: "La sesión de subida a YouTube ya no es válida.",
                )
            }

            else -> YouTubePublishDecision.MarkBlocked(
                "Estado desconocido para la publicación ($phase).",
            )
        }
    }

    // Evidencia FUERTE de que YouTube pudo haber procesado la publicación —
    // centralizada acá para que decide() y
    // PublishOperationStore.startNewAttempt nunca diverjan sobre qué cuenta
    // como "posible publicación". Tres señales, cualquiera alcanza:
    //   - el chunk final se marcó como mandado;
    //   - ya hay un resultado registrado (platformId o URL no vacíos);
    //   - los bytes confirmados ya cubren el total declarado (aunque el flag
    //     de final no se haya llegado a marcar — guardar `.confirmed` pudo
    //     fallar después de un 2xx real).
    fun hasStrongPublicationEvidence(
        ytFinalChunkSent: Boolean,
        ytBytesConfirmed: Long,
        ytTotalBytes: Long?,
        resultPlatformId: String?,
        resultURL: String?,
    ): Boolean {
        if (ytFinalChunkSent) return true
        if (!resultPlatformId.isNullOrEmpty()) return true
        if (!resultURL.isNullOrEmpty()) return true
        val total = ytTotalBytes
        return total != null && total > 0 && ytBytesConfirmed >= total
    }

    // Una sesión utilizable es una URL ABSOLUTA, HTTPS, con HOST real. Con
    // java.net.URI (disponible igual en Android y en JVM de tests, sin tocar
    // android.*): URI(string) solo no alcanzaría — hay que exigir absoluta,
    // esquema https (case-insensitive) y host no blanco. Además se rechaza la
    // userinfo: puede enmascarar el destino real, y las sesiones de Google no
    // la usan (siempre https://…upload.googleapis.com/…). Tratar cualquiera
    // de los inválidos como válida mandaría el chunk a un destino que no es
    // ninguna sesión real — o a un esquema que ni siquiera es de red.
    fun isValidHttpsUrl(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val uri = try {
            URI(raw.trim())
        } catch (e: URISyntaxException) {
            return false
        }
        if (!uri.isAbsolute) return false
        if (uri.scheme?.equals("https", ignoreCase = true) != true) return false
        if (uri.userInfo != null) return false
        val host = uri.host ?: return false
        // Un host sin ningún carácter alfanumérico ("." o "..") no resuelve a
        // ninguna sesión real.
        return host.isNotBlank() && host.any { it.isLetterOrDigit() }
    }
}
