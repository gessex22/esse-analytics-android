plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.ingest"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:media"))
    implementation(project(":core:datastore"))
    // RemoteLibraryApi (importFromRemoteLibrary: bajar un video de Nube y
    // tratarlo como cualquier otro importado, mismo criterio que iOS).
    implementation(project(":core:network"))
    implementation(libs.okhttp.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.room.runtime)
}
