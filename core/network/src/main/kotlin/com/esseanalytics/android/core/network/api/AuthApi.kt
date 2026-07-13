package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.LinkInstallRequest
import com.esseanalytics.android.core.network.dto.LoginRequest
import com.esseanalytics.android.core.network.dto.LoginResponse
import com.esseanalytics.android.core.network.dto.UpdateTierRequest
import com.esseanalytics.android.core.network.dto.UserDto
import com.esseanalytics.android.core.network.dto.UsersListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    // Re-lee tier/role frescos SIN emitir un token nuevo — no hay refresh token,
    // esto solo sirve para refrescar el user object guardado localmente.
    @GET("api/auth/me")
    suspend fun me(): UserDto

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

    @PATCH("api/auth/users/{id}/deactivate")
    suspend fun deactivateUser(@Path("id") id: String)
}
