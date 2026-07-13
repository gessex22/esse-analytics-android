package com.esseanalytics.android.core.network.di

import com.esseanalytics.android.core.network.api.AuthApi
import com.esseanalytics.android.core.network.api.PlatformAuthApi
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.interceptor.AuthAuthenticator
import com.esseanalytics.android.core.network.interceptor.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

// TODO Fase 0→1: mover a BuildConfig por build type (debug apunta a un central
// local de desarrollo, release a la producción) — por ahora un solo valor,
// mismo dominio que usa el frontend web (frontend/src/config.ts).
private const val CENTRAL_BASE_URL = "https://api.esse-analytics.com/"

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CentralRetrofit

// Cliente crudo, SIN AuthInterceptor/AuthAuthenticator de la central — las 3
// subidas directas (feature:upload) le pegan a graph.facebook.com /
// open.tiktokapis.com / googleapis.com con su PROPIO access_token por
// request (el que devuelve GET /api/{platform}/token), nunca con el JWT de
// la central. Usar el cliente de arriba ahí sería un bug: le pondría el
// Bearer equivocado, y peor, AuthAuthenticator interpretaría cualquier 401
// de Google/Meta/TikTok como si fuera de la central y borraría la sesión.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlatformOkHttp

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(authAuthenticator)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides
    @Singleton
    @PlatformOkHttp
    fun providePlatformOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Timeouts largos: subir un video de varios MB/minutos por 3G/4G
        // puede tardar bastante más que el default de 10s de OkHttp.
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
        .readTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides
    @Singleton
    @CentralRetrofit
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(CENTRAL_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(@CentralRetrofit retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun providePlatformAuthApi(@CentralRetrofit retrofit: Retrofit): PlatformAuthApi =
        retrofit.create(PlatformAuthApi::class.java)

    @Provides
    @Singleton
    fun provideSyncApi(@CentralRetrofit retrofit: Retrofit): SyncApi = retrofit.create(SyncApi::class.java)
}
