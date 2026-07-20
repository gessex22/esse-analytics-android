package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.BackupFilesResponse
import retrofit2.http.GET

// Catálogo de solo lectura del backup automático del escritorio -- gate real
// es requirePremium (backend/src/routes/backup.routes.ts), no
// requireCloudStorage: es gratis para todo premium, a diferencia de la cola
// remota con bytes reales (RemoteLibraryApi).
interface BackupApi {
    @GET("api/backup/files")
    suspend fun listFiles(): BackupFilesResponse
}
