package com.esseanalytics.android.core.database

// Qué hacer con la fila del journal de un (sourceId, instagram) ANTES de
// tocar la red -- pura, sin Room ni transporte, mismo patrón que
// PublishOperationPolicy (YouTube). Referencia conceptual:
// InstagramOperationPolicy.swift en essenalytics-ios.
//
// El único desenlace que habilita un POST de creación de contenedor nuevo es
// StartFresh, y sale ÚNICAMENTE de una fila que nunca existió o que está
// genuinamente `pending` sin ningún resto de un contenedor/publicación
// anterior. ResumeContainer NO toca la red por sí sola -- le pasa al uploader
// un contenedor existente para que lo CONSULTE, nunca para que lo re-creé ni
// re-suba el archivo. A diferencia de YouTube, Instagram no tiene una fase
// "sesión perdida pero segura de reintentar con handle nuevo": el único avance
// seguro entre variantes (original -> normalized -> trimmed60s) sale de un
// rechazo EXPLÍCITO de Meta y lo maneja el checkpoint StageStarting — no hace
// falta una transición explícita aparte como startNewAttempt.
sealed interface InstagramPublishDecision {
    data class StartFresh(val stage: InstagramUploadStage) : InstagramPublishDecision
    data class ResumeContainer(
        val stage: InstagramUploadStage,
        val containerId: String,
        val uploadUri: String,
    ) : InstagramPublishDecision

    data class AlreadyConfirmed(val platformId: String, val url: String?) : InstagramPublishDecision
    data class AlreadyBlocked(val message: String) : InstagramPublishDecision
    // Recién se detecta la inconsistencia acá — el llamador la persiste
    // (checkpoint Blocked) antes de fallar.
    data class MarkBlocked(val message: String) : InstagramPublishDecision
}

// Variantes de subida de Instagram (rawValue persistido en PublishOperationEntity.igStage).
// "original" = el archivo tal cual; las demás solo se intentan tras un rechazo
// explícito de Meta que las pide.
enum class InstagramUploadStage(val rawValue: String) {
    ORIGINAL("original"),
    NORMALIZED("normalized"),
    TRIMMED_60S("trimmed60s"),
    ;

    companion object {
        fun fromRawValue(raw: String?): InstagramUploadStage? = entries.firstOrNull { it.rawValue == raw }
    }
}

object InstagramOperationPolicy {

    // `phase == null` significa "no hay fila todavía" — tan seguro de empezar
    // de cero (en ORIGINAL) como una fila `pending` sin ningún resto.
    fun decide(
        phase: String?,
        igStage: String?,
        igContainerId: String?,
        igUploadURI: String?,
        igPublishRequested: Boolean,
        resultPlatformId: String?,
        resultURL: String?,
        lastError: String?,
    ): InstagramPublishDecision {
        if (phase == null) return InstagramPublishDecision.StartFresh(InstagramUploadStage.ORIGINAL)

        val hasResult = !resultPlatformId.isNullOrEmpty() || !resultURL.isNullOrEmpty()

        return when (phase) {
            "pending" -> {
                // `pending` solo es "seguí desde acá" si no tiene NINGÚN resto
                // de contenedor, publicación pedida, o resultado — `pending`
                // significa justamente "todavía no se tocó la red para esta
                // etapa". Cualquier resto es inconsistencia: no se puede saber
                // si Meta procesó algo, así que nunca se asume "arrancá igual".
                if (igContainerId != null || igUploadURI != null || igPublishRequested || hasResult) {
                    return InstagramPublishDecision.MarkBlocked(
                        "Estado inconsistente: la publicación de Instagram figura pendiente pero tiene restos " +
                            "de un contenedor, una publicación pedida, o un resultado previo.",
                    )
                }
                // igStage == null es "todavía no se decidió nada, empezá en
                // original". Un valor NO NULLO tiene que resolver a una etapa
                // reconocible: un rawValue corrupto NUNCA cae silenciosamente
                // a original (podría estar pisando una etapa real que no se
                // puede leer), se bloquea.
                val stage = if (igStage == null) {
                    InstagramUploadStage.ORIGINAL
                } else {
                    InstagramUploadStage.fromRawValue(igStage)
                        ?: return InstagramPublishDecision.MarkBlocked(
                            "Estado inconsistente: la etapa guardada de Instagram ($igStage) no es reconocible.",
                        )
                }
                InstagramPublishDecision.StartFresh(stage)
            }

            "containerCreated" -> {
                // El contenedor existe, pero si YA hay evidencia de que se
                // pidió publicar o de un resultado, la fase no coincide con
                // los datos — corrupción, nunca se asume "todavía no se publicó".
                if (igPublishRequested || hasResult) {
                    return InstagramPublishDecision.MarkBlocked(
                        "Estado inconsistente: la publicación de Instagram figura con el contenedor creado pero " +
                            "ya hay evidencia de que se pidió publicar.",
                    )
                }
                val stage = InstagramUploadStage.fromRawValue(igStage)
                val containerId = igContainerId
                val uploadUri = igUploadURI
                if (stage == null || containerId.isNullOrEmpty() || uploadUri == null ||
                    !PublishOperationPolicy.isValidHttpsUrl(uploadUri)
                ) {
                    return InstagramPublishDecision.MarkBlocked(
                        "Estado inconsistente: falta la etapa, el contenedor, o la URI de subida es inválida.",
                    )
                }
                InstagramPublishDecision.ResumeContainer(
                    stage = stage,
                    containerId = containerId,
                    uploadUri = uploadUri,
                )
            }

            "publishRequested" -> {
                // Se pidió publicar y el intento murió antes de saber en qué
                // terminó. Meta no ofrece una forma de reconciliar "¿se aplicó
                // mi media_publish?" sin volver a llamarlo — eso podría
                // duplicar la publicación — así que este desenlace queda
                // ambiguo para siempre: verificación manual. NUNCA reintenta.
                InstagramPublishDecision.MarkBlocked(
                    lastError ?: "La publicación de Instagram se pidió pero su desenlace remoto no se confirmó.",
                )
            }

            "confirmed" -> {
                // El mediaId se persiste ANTES de fetchar el permalink, así que
                // confirmed vale con platformId aunque la URL todavía no
                // exista: la URL es opcional y se completa después.
                val platformId = resultPlatformId
                if (platformId.isNullOrEmpty()) {
                    return InstagramPublishDecision.MarkBlocked(
                        "Estado inconsistente: la publicación de Instagram figura confirmada pero sin el id del resultado.",
                    )
                }
                InstagramPublishDecision.AlreadyConfirmed(
                    platformId = platformId,
                    url = resultURL?.takeIf { it.isNotEmpty() },
                )
            }

            "blocked" -> InstagramPublishDecision.AlreadyBlocked(
                lastError ?: "La publicación de Instagram quedó bloqueada por un desenlace remoto sin confirmar.",
            )

            else -> InstagramPublishDecision.MarkBlocked(
                "Estado desconocido para la publicación de Instagram ($phase).",
            )
        }
    }
}
