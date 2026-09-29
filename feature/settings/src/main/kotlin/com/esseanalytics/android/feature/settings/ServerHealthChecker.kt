package com.esseanalytics.android.feature.settings

import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface ServerHealthResult {
    data class Alive(val environment: String?) : ServerHealthResult
    data class Unreachable(val message: String) : ServerHealthResult
}

// Chequeo del candidato SIN pasar por HealthApi/Retrofit: el singleton de
// Retrofit ya quedó pegado a la baseUrl con la que arrancó el proceso (ver
// TODO de NetworkModule), así que probar el candidato por ahí pegaría contra
// el servidor VIEJO. Un OkHttp dedicado con timeout de 5s pega directo a
// {candidato}/api/health y solo ante HTTP 2xx el caller persiste el valor
// (SettingsViewModel.saveServerUrl).
@Singleton
class ServerHealthChecker @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun check(healthCheckBase: String): ServerHealthResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$healthCheckBase/api/health").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val environment = ENVIRONMENT_REGEX
                        .find(response.body?.string().orEmpty())
                        ?.groupValues?.get(1)
                    ServerHealthResult.Alive(environment)
                } else {
                    ServerHealthResult.Unreachable("El servidor respondió con error (HTTP ${response.code})")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ServerHealthResult.Unreachable("No se pudo conectar con el servidor. Revisá la URL y que estés en la misma red.")
        }
    }

    private companion object {
        val ENVIRONMENT_REGEX = Regex("\"environment\"\\s*:\\s*\"([^\"]+)\"")
    }
}
