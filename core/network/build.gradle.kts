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
    // Outbox persistente de recordPublish (HistoryOutbox, hallazgo SYNC-02#4)
    // necesita Room para encolar eventos que fallaron -- core:database no
    // depende de core:network, así que no hay ciclo.
    implementation(project(":core:database"))

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
