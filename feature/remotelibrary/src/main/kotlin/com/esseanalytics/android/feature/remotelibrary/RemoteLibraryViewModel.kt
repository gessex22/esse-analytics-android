package com.esseanalytics.android.feature.remotelibrary

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.esseanalytics.android.core.datastore.TokenStore
import com.esseanalytics.android.core.media.AndroidFrameThumbnailGenerator
import com.esseanalytics.android.core.media.AndroidMediaProber
import com.esseanalytics.android.core.media.MediaSource
import com.esseanalytics.android.core.model.Platform
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.di.CentralRetrofit
import com.esseanalytics.android.core.network.dto.RemoteLibraryUploadResponse
import com.esseanalytics.android.core.network.dto.RemoteLibraryVideoDto
import com.esseanalytics.android.core.network.tus.TUSUploadClient
import com.esseanalytics.android.core.network.util.remoteLibraryStreamUrl
import com.esseanalytics.android.core.network.util.remoteLibraryThumbnailUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed interface RemoteLibraryUiState {
    data object Loading : RemoteLibraryUiState
    data class Loaded(val videos: List<RemoteLibraryVideoDto>) : RemoteLibraryUiState
    data class Error(val message: String) : RemoteLibraryUiState
}

@HiltViewModel
class RemoteLibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: RemoteLibraryApi,
    private val mediaProber: AndroidMediaProber,
    private val thumbnailGenerator: AndroidFrameThumbnailGenerator,
    private val okHttpClient: OkHttpClient,
    @CentralRetrofit private val retrofit: Retrofit,
    private val json: Json,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    // Mirror de RemoteLibraryAPI.thumbnailURL en iOS. Null si el video no
    // tiene miniatura -- el caller (RemoteVideoRow) muestra el ícono
    // genérico en ese caso, no rompe nada.
    fun thumbnailUrl(video: RemoteLibraryVideoDto): String? =
        remoteLibraryThumbnailUrl(retrofit.baseUrl(), video._id, video.thumbnailStoredFileName, tokenStore.token)

    // Mirror de RemoteLibraryAPI.streamURL en iOS -- usada por el reproductor
    // (ver VideoPlayerDialog) desde la lista de esta pantalla.
    fun streamUrl(video: RemoteLibraryVideoDto): String? =
        remoteLibraryStreamUrl(retrofit.baseUrl(), video._id, tokenStore.token)

    private val _uiState = MutableStateFlow<RemoteLibraryUiState>(RemoteLibraryUiState.Loading)
    val uiState: StateFlow<RemoteLibraryUiState> = _uiState.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = RemoteLibraryUiState.Loading
            runCatching { api.listVideos().videos }
                .onSuccess { _uiState.value = RemoteLibraryUiState.Loaded(it) }
                .onFailure { _uiState.value = RemoteLibraryUiState.Error(it.message ?: "No se pudo cargar la cola remota.") }
        }
    }

    // Copia el Uri elegido (SAF) a un temporal en cacheDir -- necesario para
    // poder probarlo con AndroidMediaProber (necesita un File real o un Uri
    // persistente) y armar el multipart, no queda nada guardado localmente
    // después de esto (a diferencia de ImportUseCase, acá la "biblioteca" en
    // sí vive en la central, no en Room).
    fun uploadVideo(uri: Uri) {
        viewModelScope.launch {
            _uploading.value = true
            runCatching { doUpload(uri) }
                .onFailure { _uiState.value = RemoteLibraryUiState.Error(it.message ?: "No se pudo subir el video.") }
            _uploading.value = false
            refresh()
        }
    }

    // Sube por TUS resumable (mismo protocolo que iOS y el frontend de
    // escritorio, ver TUSUploadClient) -- el multipart single-shot que había
    // acá antes apuntaba a un endpoint que la central ya no tiene (bug real:
    // subir a Nube estaba roto). La miniatura sigue siendo multipart simple,
    // DESPUÉS de crear el video (mismo orden que iOS).
    private suspend fun doUpload(uri: Uri) {
        val displayName = queryDisplayName(uri) ?: "video_${System.currentTimeMillis()}.mp4"
        val extension = displayName.substringAfterLast('.', "mp4")
        val tempDir = File(context.cacheDir, "remote-library-uploads").apply { mkdirs() }
        val tempFile = File(tempDir, "${UUID.randomUUID()}.$extension")

        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied) error("No se pudo leer el archivo seleccionado.")

            val mediaSource = MediaSource.LocalFile(tempFile)
            val info = mediaProber.probe(mediaSource)

            val thumbnailFile = File(tempDir, "${UUID.randomUUID()}.jpg")
            val hasThumbnail = thumbnailGenerator.generate(mediaSource, thumbnailFile)

            val metadata = buildMap {
                put("filename", tempFile.name)
                put("filetype", "video/mp4")
                put("fileName", displayName)
                info.durationSeconds?.let { put("durationSeconds", it.toInt().toString()) }
                if (info.width != null && info.height != null) put("resolution", "${info.width}x${info.height}")
                put("formato", extension)
            }

            val tusEndpoint = retrofit.baseUrl().newBuilder()
                .addPathSegments("api/remote-library/tus")
                .build()
                .toString()

            val responseBody = TUSUploadClient.upload(
                file = tempFile,
                endpoint = tusEndpoint,
                okHttpClient = okHttpClient,
                metadata = metadata,
            )
            var video = json.decodeFromString<RemoteLibraryUploadResponse>(responseBody).video

            if (hasThumbnail) {
                val thumbnailPart = MultipartBody.Part.createFormData(
                    "thumbnail", thumbnailFile.name, thumbnailFile.asRequestBody("image/jpeg".toMediaType()),
                )
                video = runCatching { api.uploadThumbnail(video._id, thumbnailPart).video }.getOrDefault(video)
                thumbnailFile.delete()
            }
        } finally {
            tempFile.delete()
        }
    }

    fun publish(video: RemoteLibraryVideoDto, platforms: Set<Platform>, title: String, description: String) {
        platforms.forEach { platform ->
            val request = OneTimeWorkRequestBuilder<RemoteUploadWorker>()
                .setInputData(RemoteUploadWorker.buildInputData(video._id, platform, title, description, video.platforms))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniqueWork(
                uniqueWorkName(video._id, platform),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    fun observeWork(videoId: String, platform: Platform): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(videoId, platform)).map { it.firstOrNull() }

    fun delete(video: RemoteLibraryVideoDto) {
        viewModelScope.launch {
            runCatching { api.deleteVideo(video._id) }
                .onSuccess { refresh() }
                .onFailure { _uiState.value = RemoteLibraryUiState.Error(it.message ?: "No se pudo borrar el video.") }
        }
    }

    private fun uniqueWorkName(videoId: String, platform: Platform) = "remote_upload_${videoId}_${platform.apiValue}"

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
}
