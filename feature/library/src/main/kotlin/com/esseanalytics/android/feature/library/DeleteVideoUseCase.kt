package com.esseanalytics.android.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.esseanalytics.android.core.database.FileRepository
import com.esseanalytics.android.core.media.MediaSource
import com.esseanalytics.android.core.model.FileStatus
import com.esseanalytics.android.core.model.VideoFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Soft-delete igual que desktop (local-backend/src/controllers/video.controller.ts,
// deleteFileFromDisk): el registro queda en Room con status=ELIMINADO_DISCO, no
// un DELETE de SQL -- FileDao ya excluye ese status de todas sus queries, así
// que alcanza con el update para que desaparezca de Biblioteca/Subir.
//
// La diferencia con desktop está en QUÉ se borra de verdad, por el modelo de
// "sin duplicar video" (ver ImportUseCase): una copia propia (LocalFile, en
// filesDir/videos) se borra del disco del teléfono -- la app era la única
// dueña. Una referencia persistida (ContentUri, elegida por SAF sin copiar)
// NO toca el archivo del usuario -- solo se libera el permiso persistente.
@Singleton
class DeleteVideoUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileRepository: FileRepository,
) {
    suspend fun delete(file: VideoFile) = withContext(Dispatchers.IO) {
        when (val source = MediaSource.fromStoredPath(file.filePath)) {
            is MediaSource.LocalFile -> source.file.delete()
            is MediaSource.ContentUri -> releasePersistedPermissionBestEffort(source.uri)
        }
        file.thumbnailPath?.let { File(it).delete() }
        fileRepository.update(file.copy(status = FileStatus.ELIMINADO_DISCO))
    }

    private fun releasePersistedPermissionBestEffort(uri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}
