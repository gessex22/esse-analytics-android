plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.ingest"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:media"))
    implementation(project(":core:datastore"))
    // RemoteLibraryApi (importFromRemoteLibrary: bajar un video de Nube y
    // tratarlo como cualquier otro importado, mismo criterio que iOS).
    implementation(project(":core:network"))
    implementation(libs.okhttp.core)
}
