package com.esseanalytics.android.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.esseanalytics.android.core.model.WorkflowMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "essenalytics_settings")

// Settings no sensibles (a diferencia de TokenStore) — modo de flujo, subir
// solo con WiFi, y el installId estable que requiere POST /api/auth/link-install.
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val workflowMode: Flow<WorkflowMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_WORKFLOW_MODE] ?: WorkflowMode.SIMPLE.name
        runCatching { WorkflowMode.valueOf(raw) }.getOrDefault(WorkflowMode.SIMPLE)
    }

    suspend fun setWorkflowMode(mode: WorkflowMode) {
        context.dataStore.edit { it[KEY_WORKFLOW_MODE] = mode.name }
    }

    val wifiOnlyUploads: Flow<Boolean> = context.dataStore.data.map { it[KEY_WIFI_ONLY] ?: false }

    suspend fun setWifiOnlyUploads(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WIFI_ONLY] = enabled }
    }

    // Importar SIEMPRE copia el archivo a storage privado (ver ImportUseCase
    // y el plan, sección "Ingesta de videos") -- el original en Galería/
    // Archivos queda intacto a menos que el usuario prenda esto. Default
    // false a propósito: borrar el original es mejor-esfuerzo (puede fallar
    // sin permiso en Android 10+) y es una decisión de espacio-en-disco del
    // usuario, no algo que la app deba asumir sola.
    val deleteOriginalAfterImport: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_DELETE_ORIGINAL] ?: false }

    suspend fun setDeleteOriginalAfterImport(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DELETE_ORIGINAL] = enabled }
    }

    // UUID persistido una sola vez — ≥16 chars, como pide POST /api/auth/link-install.
    suspend fun getOrCreateInstallId(): String {
        val existing = context.dataStore.data.map { it[KEY_INSTALL_ID] }.first()
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        context.dataStore.edit { it[KEY_INSTALL_ID] = generated }
        return generated
    }

    private companion object {
        val KEY_WORKFLOW_MODE = stringPreferencesKey("workflow_mode")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_uploads")
        val KEY_INSTALL_ID = stringPreferencesKey("install_id")
        val KEY_DELETE_ORIGINAL = booleanPreferencesKey("delete_original_after_import")
    }
}
