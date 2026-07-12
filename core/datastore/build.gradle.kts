plugins {
    alias(libs.plugins.essenalytics.android.library)
    alias(libs.plugins.essenalytics.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esseanalytics.android.core.datastore"
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
