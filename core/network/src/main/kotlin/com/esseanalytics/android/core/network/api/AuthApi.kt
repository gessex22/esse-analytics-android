package com.esseanalytics.android.core.network.api

import com.esseanalytics.android.core.network.dto.LinkInstallRequest
import com.esseanalytics.android.core.network.dto.LoginRequest
import com.esseanalytics.android.core.network.dto.LoginResponse
import com.esseanalytics.android.core.network.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    // Re-lee tier/role frescos SIN emitir un token nuevo — no hay refresh token,
    // esto solo sirve para refrescar el user object guardado localmente.
    @GET("api/auth/me")
    suspend fun me(): UserDto

    @POST("api/auth/link-install")
    suspend fun linkInstall(@Body body: LinkInstallRequest)
}
