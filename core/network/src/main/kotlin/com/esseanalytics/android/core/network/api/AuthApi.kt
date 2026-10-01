package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.LinkInstallRequest
import com.esseanalytics.android.core.network.dto.LoginRequest
import com.esseanalytics.android.core.network.dto.RegisterRequest
import com.esseanalytics.android.core.network.dto.LoginResponse
import com.esseanalytics.android.core.network.dto.AuthMeResponse
import com.esseanalytics.android.core.network.dto.UpdateCloudStorageRequest
import com.esseanalytics.android.core.network.dto.UpdateTierRequest
import com.esseanalytics.android.core.network.dto.UserDto
import com.esseanalytics.android.core.network.dto.UsersListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): LoginResponse

    // Relee perfil y capacidades efectivas desde central, SIN emitir token nuevo.
    @Headers("Cache-Control: no-cache")
    @GET("api/auth/me")
    suspend fun me(): AuthMeResponse

    @POST("api/auth/link-install")
    suspend fun linkInstall(@Body body: LinkInstallRequest)

    // Owner-only -- feature:users (Fase 2). Mismos endpoints que ya consume
    // frontend/src/components/UsersPanel.tsx.
    @GET("api/auth/users")
    suspend fun listUsers(
        @Query("status") status: String,
        @Query("limit") limit: Int = 5,
        @Query("q") query: String? = null,
    ): UsersListResponse

    @PATCH("api/auth/users/{id}/tier")
    suspend fun updateUserTier(@Path("id") id: String, @Body body: UpdateTierRequest)

    @PATCH("api/auth/users/{id}/cloud-storage")
    suspend fun updateUserCloudStorage(@Path("id") id: String, @Body body: UpdateCloudStorageRequest)

    @PATCH("api/auth/users/{id}/deactivate")
    suspend fun deactivateUser(@Path("id") id: String)
}
