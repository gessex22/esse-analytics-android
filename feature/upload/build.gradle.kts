plugins {
    alias(libs.plugins.essenalytics.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esseanalytics.android.feature.upload"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:media"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    implementation(libs.coil.compose)
}
