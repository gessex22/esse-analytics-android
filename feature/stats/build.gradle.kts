plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.stats"
}

dependencies {
    implementation(project(":core:network"))
    // TokenStore (para armar la URL de miniatura con ?token=, ver
    // remoteLibraryThumbnailUrl) -- core:network también lo usa pero como
    // `implementation`, no transitivo.
    implementation(project(":core:datastore"))
    // Retrofit directo (para reusar retrofit.baseUrl(), mismo motivo que
    // feature:remotelibrary/feature:library) -- core:network lo declara
    // `implementation`, no es transitivo.
    implementation(libs.retrofit.core)
    // Miniatura puntual de Biblioteca remota en la tarjeta de Estadísticas
    // (StatsThumbnail) -- antes la tarjeta no mostraba ninguna imagen.
    implementation(libs.coil.compose)
}
