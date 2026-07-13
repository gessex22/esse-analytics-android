plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.library"
}

dependencies {
    implementation(project(":core:database"))
    implementation(libs.coil.compose)
}
