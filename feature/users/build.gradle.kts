plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.users"
}

dependencies {
    implementation(project(":core:network"))
}
