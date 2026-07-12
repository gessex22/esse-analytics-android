plugins {
    alias(libs.plugins.essenalytics.android.library)
    alias(libs.plugins.essenalytics.android.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.esseanalytics.android.core.database"
}

ksp {
    // Room escribe acá el JSON de schema de cada versión — hace falta para
    // poder escribir Migration reales más adelante (a partir de la v2).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
}
