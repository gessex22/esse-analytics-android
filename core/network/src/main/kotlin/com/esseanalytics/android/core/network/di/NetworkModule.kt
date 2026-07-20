package com.esseanalytics.android.core.network.di

import android.content.Context
import com.esseanalytics.android.core.network.api.AuthApi
import com.esseanalytics.android.core.network.api.BackupApi
import com.esseanalytics.android.core.network.api.PlatformAuthApi
import com.esseanalytics.android.core.network.api.RemoteLibraryApi
import com.esseanalytics.android.core.network.api.SyncApi
import com.esseanalytics.android.core.network.interceptor.AuthAuthenticator
import com.esseanalytics.android.core.network.interceptor.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
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
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(authAuthenticator)
        // Timeouts default de OkHttp (10s) alcanzaban mientras esto era solo
        // JSON chico -- la Biblioteca remota (Parte C del plan) sube videos
        // multipart por acá mismo (mismo @CentralRetrofit, para que el JWT se
        // adjunte solo vía AuthInterceptor). Subirlos no perjudica las
        // llamadas JSON normales, solo les da más margen si algo se cuelga.
        .writeTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
        .readTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
        // Cache de disco para los GET de la central (listados de sync/biblioteca
        // remota) -- la central no manda Cache-Control propio, así que sin el
        // network interceptor de acá abajo OkHttp jamás cachearía nada. 60s
        // alcanza para evitar el pedido repetido de una ida y vuelta rápida
        // (ej. abrir y cerrar Ajustes) sin arriesgar datos viejos por mucho tiempo.
        .cache(Cache(java.io.File(context.cacheDir, "http_cache"), 10L * 1024 * 1024))
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (chain.request().method == "GET" && response.header("Cache-Control") == null) {
                response.newBuilder().header("Cache-Control", "public, max-age=60").build()
            } else {
                response
            }
        }
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

    @Provides
    @Singleton
    fun provideRemoteLibraryApi(@CentralRetrofit retrofit: Retrofit): RemoteLibraryApi =
        retrofit.create(RemoteLibraryApi::class.java)

    @Provides
    @Singleton
    fun provideBackupApi(@CentralRetrofit retrofit: Retrofit): BackupApi = retrofit.create(BackupApi::class.java)
}
