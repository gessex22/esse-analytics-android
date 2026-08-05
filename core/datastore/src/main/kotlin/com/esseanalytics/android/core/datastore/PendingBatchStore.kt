package com.esseanalytics.android.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pendingBatchDataStore by preferencesDataStore(name = "essenalytics_pending_batch")

// Metadata local MÍNIMA de un lote de publicación en curso -- SOLO lo justo
// para reconstruir "había una publicación de este archivo en curso" al
// reabrir la app (Fase 2 del plan de estabilidad/UX), nunca JWT/tokens
// OAuth/contenido de video/rutas privadas completas (misma regla que va a
// regir la auditoría central de Fase 5).
//
// A diferencia de iOS (que sí necesita persistir progreso/etapa a mano
// porque no hay nada parecido a WorkManager corriendo la subida real), acá
// WorkManager YA es la fuente de verdad de qué pasó con cada plataforma --
// esto solo guarda el POINTER (qué archivo, qué plataformas, qué
// operationId) para saber a qué unique work names volver a suscribirse
// (observeWork) apenas la UI se reconstruye tras la muerte del proceso.
@Serializable
data class PendingPublishBatch(
    val operationId: String,
    val fileId: Long,
    val fileDisplayName: String,
    val platforms: List<String>, // Platform.apiValue
)

@Singleton
class PendingBatchStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // ignoreUnknownKeys -- mismo criterio que TokenStore: si un campo se
    // agrega/saca más adelante, un batch persistido con la forma vieja no
    // debe romper el decode, simplemente se pierde ese dato puntual.
    private val json = Json { ignoreUnknownKeys = true }

    val current: Flow<PendingPublishBatch?> = context.pendingBatchDataStore.data.map { prefs ->
        prefs[KEY_BATCH]?.let { raw -> runCatching { json.decodeFromString<PendingPublishBatch>(raw) }.getOrNull() }
    }

    suspend fun save(batch: PendingPublishBatch) {
        context.pendingBatchDataStore.edit { it[KEY_BATCH] = json.encodeToString(batch) }
    }

    suspend fun clear() {
        context.pendingBatchDataStore.edit { it.remove(KEY_BATCH) }
    }

    suspend fun currentValue(): PendingPublishBatch? = current.first()

    private companion object {
        val KEY_BATCH = stringPreferencesKey("pending_publish_batch")
    }
}
