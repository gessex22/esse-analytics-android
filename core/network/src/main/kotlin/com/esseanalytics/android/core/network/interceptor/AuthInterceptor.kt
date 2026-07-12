package com.esseanalytics.android.core.network.interceptor

import com.esseanalytics.android.core.datastore.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

// Agrega el Bearer token a cada request saliente contra la central. Rutas
// públicas (login, register, callbacks de OAuth) simplemente no tienen token
// guardado todavía en esos momentos, así que esto no rompe nada ahí.
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.token
        val request = if (token != null) {
            chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
