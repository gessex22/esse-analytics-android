plugins {
    alias(libs.plugins.essenalytics.android.library)
    alias(libs.plugins.essenalytics.android.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.esseanalytics.android.core.database"

    defaultConfig {
        // Necesario para que el APK de androidTest de ESTE módulo arranque con
        // AndroidJUnitRunner (las pruebas instrumentadas de migración viven en
        // core:database, junto al schema que MigrationTestHelper valida).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        // MigrationTestHelper lee los schemas exportados de Room como assets
        // del APK de test (receta estándar de la doc de Room).
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
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

    testImplementation(libs.junit)
    // Los tests del store usan runTest (PublishOperationStoreTest).
    testImplementation(libs.kotlinx.coroutines.test)

    // Prueba instrumentada de migración real (Migration6To7InstrumentedTest).
    // NO verificada en el entorno donde se escribió (Gradle no puede spawnear
    // la JVM daemon acá): pendiente `./gradlew :core:database:connectedDebugAndroidTest`
    // desde Android Studio con un emulador/dispositivo.
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    // JUnit de Android explícito (androidx.test.ext:junit) -- la prueba
    // instrumentada no debe depender de que runner/core lo traigan de forma
    // transitiva.
    androidTestImplementation(libs.androidx.junit)
}
