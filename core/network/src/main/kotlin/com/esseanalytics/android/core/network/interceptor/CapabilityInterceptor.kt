package com.esseanalytics.android.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

// Declara a la central qué formatos entiende esta app. Solo se agrega al cliente
// de la central; la subida a proveedores (@PlatformOkHttp) no debe recibirla.
class CapabilityInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().header(HEADER, CAPABILITIES).build()
        return chain.proceed(request)
    }

    companion object {
        const val HEADER = "X-Esse-Capabilities"
        // «sin dato» llega como null en lugar de 0 (contrato metrics-nullable-v1).
        const val METRICS_NULLABLE = "metrics-nullable-v1"
        const val CAPABILITIES = METRICS_NULLABLE
    }
}
