package com.esseanalytics.android.feature.upload

import androidx.work.WorkInfo
import com.esseanalytics.android.core.model.Platform

// Estado del LOTE completo de una publicación multi-plataforma (Fase 2 del
// plan de estabilidad/UX) -- mismo vocabulario que iOS (BatchStage/
// PlatformPublishState en PublishFormView.swift) para que ambas apps
// muestren una UX equivalente, aunque la fuente de verdad acá sea distinta:
// WorkManager (no un loop propio en el ViewModel) es quien de verdad corre
// cada subida y persiste su resultado a través de la muerte del proceso.
enum class BatchStage {
    PREPARING,
    UPLOADING,
    COMPLETED,
    PARTIAL_FAILURE,
    FAILED,
    CANCELLED,
}

enum class PlatformPublishStage {
    PENDING,
    UPLOADING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

data class PlatformPublishState(
    val platform: Platform,
    val stage: PlatformPublishStage,
    val progress: Float? = null,
    val finalUrl: String? = null,
    val error: String? = null,
)

data class PublishBatchState(
    val operationId: String,
    val fileId: Long,
    val fileDisplayName: String,
    val stage: BatchStage,
    val platforms: List<PlatformPublishState>,
) {
    val isActive: Boolean
        get() = stage == BatchStage.PREPARING || stage == BatchStage.UPLOADING
}

// Traduce el WorkInfo crudo de WorkManager al vocabulario compartido de
// arriba. No hay un estado "interrumpido" separado acá a propósito -- a
// diferencia de iOS (que se suspende y no puede seguir corriendo nada),
// WorkManager sigue la subida en background y hasta sobrevive la muerte del
// proceso, reintentando solo cuando el sistema operativo se lo permite. Si
// de verdad no puede continuar (ej. el usuario fuerza el cierre de la app
// desde Ajustes, que cancela todo el trabajo pendiente), WorkManager reporta
// CANCELLED/FAILED -- la UI ya refleja eso honestamente sin necesidad de
// fabricar un estado extra.
fun WorkInfo?.toPlatformStage(): PlatformPublishStage = when (this?.state) {
    null, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> PlatformPublishStage.PENDING
    WorkInfo.State.RUNNING -> PlatformPublishStage.UPLOADING
    WorkInfo.State.SUCCEEDED -> PlatformPublishStage.SUCCESS
    WorkInfo.State.FAILED -> PlatformPublishStage.FAILED
    WorkInfo.State.CANCELLED -> PlatformPublishStage.CANCELLED
}

fun deriveBatchStage(platforms: List<PlatformPublishState>): BatchStage {
    if (platforms.isEmpty()) return BatchStage.PREPARING
    val allTerminal = platforms.all {
        it.stage == PlatformPublishStage.SUCCESS ||
            it.stage == PlatformPublishStage.FAILED ||
            it.stage == PlatformPublishStage.CANCELLED
    }
    if (!allTerminal) return BatchStage.UPLOADING
    val allSuccess = platforms.all { it.stage == PlatformPublishStage.SUCCESS }
    val anySuccess = platforms.any { it.stage == PlatformPublishStage.SUCCESS }
    return when {
        allSuccess -> BatchStage.COMPLETED
        anySuccess -> BatchStage.PARTIAL_FAILURE
        else -> BatchStage.FAILED
    }
}
