plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.sync"
}

dependencies {
    implementation(project(":core:network"))
}
