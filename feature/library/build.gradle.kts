plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.library"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:media"))
    // Fusión Videos local+remoto (Parte D del plan): RemoteLibraryApi vive en
    // core:network, el entitlement canUseCloudStorage se lee de TokenStore.
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(libs.coil.compose)
    // Para armar la URL de miniatura de Nube con retrofit.baseUrl() (ver
    // remoteLibraryThumbnailUrl) -- core:network lo declara `implementation`,
    // no es transitivo.
    implementation(libs.retrofit.core)
    // Reproductor para videos locales -- no existía ninguno (solo miniatura
    // estática), ver LocalVideoPlayerScreen.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
}
