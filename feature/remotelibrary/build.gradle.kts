plugins {
    alias(libs.plugins.essenalytics.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esseanalytics.android.feature.remotelibrary"
}

dependencies {
    // Reusa YoutubeUploader/InstagramUploader/TiktokUploader tal cual (ver
    // Parte C.2 del plan) -- único feature:* que depende de otro feature:*,
    // tradeoff deliberado para esta primera pasada owner-only, ver el plan.
    implementation(project(":feature:upload"))
    implementation(project(":core:network"))
    implementation(project(":core:media"))
    // TokenStore (para armar la URL de miniatura con ?token=, ver
    // remoteLibraryThumbnailUrl) -- core:network también lo usa pero como
    // `implementation`, no transitivo.
    implementation(project(":core:datastore"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    // TUSUploadClient necesita Retrofit directo (para reusar retrofit.baseUrl()
    // y no repetir CENTRAL_BASE_URL) -- core:network lo declara `implementation`,
    // así que no es transitivo, hace falta declararlo acá también.
    implementation(libs.retrofit.core)
    // Miniaturas reales de Nube (RemoteVideoRow) -- antes solo había un ícono
    // genérico, nunca se cargaba la imagen real.
    implementation(libs.coil.compose)
    // Reproductor para streaming de Nube -- no existía ninguno, ver
    // VideoPlayerDialog.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
}
