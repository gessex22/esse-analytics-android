package com.esseanalytics.android.core.datastore

import android.content.Context
import android.os.Build
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

    val serverUrl: Flow<String> = context.dataStore.data.map { it[KEY_SERVER_URL] ?: "" }

    suspend fun setServerUrl(value: String) {
        context.dataStore.edit { prefs ->
            if (value.isBlank()) prefs.remove(KEY_SERVER_URL) else prefs[KEY_SERVER_URL] = value.trim().trimEnd('/')
        }
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

    // "rojo" | "ambar" -- valor crudo, no el enum de core:designsystem
    // (EsseAnalyticsColorTheme): core:datastore no depende de designsystem
    // a propósito (iría en contra del sentido de la dependencia), así que el
    // mapeo string→enum vive en :app, que ya depende de los dos.
    val colorTheme: Flow<String> = context.dataStore.data.map { it[KEY_COLOR_THEME] ?: "rojo" }

    suspend fun setColorTheme(value: String) {
        context.dataStore.edit { it[KEY_COLOR_THEME] = value }
    }

    // UUID persistido una sola vez — ≥16 chars, como pide POST /api/auth/link-install.
    // Reusado también como installationId de auditoría central (Fase 5) -- no
    // tiene sentido un segundo identificador de instalación en la misma app.
    suspend fun getOrCreateInstallId(): String {
        val existing = context.dataStore.data.map { it[KEY_INSTALL_ID] }.first()
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        context.dataStore.edit { it[KEY_INSTALL_ID] = generated }
        return generated
    }

    // Nombre de este dispositivo (Fase 5, auditoría central) -- a diferencia
    // de iOS (que ya tiene un nombre elegido por el usuario en Ajustes del
    // sistema, UIDevice.current.name) Android no tiene un equivalente
    // accesible sin permisos extra, así que arranca en fabricante+modelo
    // (mismo default que desktop usa el hostname) y queda editable.
    suspend fun getOrCreateDeviceName(): String {
        val existing = context.dataStore.data.map { it[KEY_DEVICE_NAME] }.first()
        if (existing != null) return existing
        val generated = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Este dispositivo" }
        context.dataStore.edit { it[KEY_DEVICE_NAME] = generated }
        return generated
    }

    suspend fun setDeviceName(value: String) {
        context.dataStore.edit { it[KEY_DEVICE_NAME] = value.trim() }
    }

    private companion object {
        val KEY_WORKFLOW_MODE = stringPreferencesKey("workflow_mode")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_uploads")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_INSTALL_ID = stringPreferencesKey("install_id")
        val KEY_DELETE_ORIGINAL = booleanPreferencesKey("delete_original_after_import")
        val KEY_COLOR_THEME = stringPreferencesKey("color_theme")
        val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
    }
}
