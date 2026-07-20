plugins {
    alias(libs.plugins.essenalytics.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esseanalytics.android.feature.remotelibrary"
}

dependencies {
    // Reusa YoutubeUploader/InstagramUploader/TiktokUploader tal cual (ver
    // Parte C.2 del plan) -- único feature:* que depende de otro feature:*,
    // tradeoff deliberado para esta primera pasada owner-only, ver el plan.
    implementation(project(":feature:upload"))
    implementation(project(":core:network"))
    implementation(project(":core:media"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
}
