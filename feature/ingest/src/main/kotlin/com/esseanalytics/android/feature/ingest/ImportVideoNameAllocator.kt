package com.esseanalytics.android.feature.ingest

import android.content.Context
import com.esseanalytics.android.core.database.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Shared by local imports and cloud uploads, including simultaneous requests. */
@Singleton
class ImportVideoNameAllocator @Inject constructor(
    @ApplicationContext context: Context,
    private val fileRepository: FileRepository,
) {
    private val mutex = Mutex()
    private val preferences = context.getSharedPreferences("import_video_names", Context.MODE_PRIVATE)

    suspend fun reserve(sourceName: String?, extension: String, remoteNames: List<String> = emptyList()): String {
        if (!ImportVideoNaming.needsName(sourceName)) return requireNotNull(sourceName)
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val reserved = ImportVideoNaming.nextName(
                    preferences.getLong("last_sequence", 0), fileRepository.getImportNames() + remoteNames, extension,
                )
                check(preferences.edit().putLong("last_sequence", reserved.sequence).commit()) {
                    "No se pudo guardar el nombre de la importación."
                }
                reserved.fileName
            }
        }
    }
}
