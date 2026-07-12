plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.auth"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.browser) // Custom Tabs para el OAuth de las 3 plataformas
}
