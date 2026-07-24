plugins {
    alias(libs.plugins.essenalytics.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esseanalytics.android.feature.upload"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:media"))
    implementation(project(":core:datastore"))
    // ImportUseCase.importFromRemoteLibrary -- dejar elegir un video de Nube
    // en el picker de Subir, igual que iOS (UploadView.VideoPickerView).
    implementation(project(":feature:ingest"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    implementation(libs.coil.compose)
    // Para armar la URL de miniatura de Nube con retrofit.baseUrl() (ver
    // remoteLibraryThumbnailUrl) -- core:network lo declara `implementation`,
    // no es transitivo.
    implementation(libs.retrofit.core)
}
