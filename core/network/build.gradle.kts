plugins {
    alias(libs.plugins.essenalytics.android.library)
    alias(libs.plugins.essenalytics.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esseanalytics.android.core.network"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:datastore"))

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
