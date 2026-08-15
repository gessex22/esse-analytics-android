import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP ya está en el classpath desde los convention plugins del proyecto;
    // aplicarlo sin versión evita que Gradle intente resolverlo por segunda vez.
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.esseanalytics.android.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Este módulo solo produce perfiles; nunca forma parte del APK.
    targetProjectPath = ":app"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

baselineProfile {
    // El OPPO con Android 15 permite generar el perfil sin root. No atamos el
    // proyecto a un emulador: la tarea usa el dispositivo conectado.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
}
