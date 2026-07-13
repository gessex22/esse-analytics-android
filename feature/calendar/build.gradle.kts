plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.calendar"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:database"))
}
