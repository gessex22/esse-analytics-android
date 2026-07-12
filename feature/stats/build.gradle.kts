plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.stats"
}

dependencies {
    implementation(project(":core:network"))
}
