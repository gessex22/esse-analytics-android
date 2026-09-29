plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.settings"
    // Habilita BuildConfig.DEBUG -- gatea los presets de servidor "Laboratorio"
    // (SettingsScreen.kt) para que NUNCA compilen en un build de Release/Play
    // Store. No estaba prendido en ningún módulo (ver
    // AndroidLibraryConventionPlugin.kt) porque hasta ahora nadie lo necesitaba.
    buildFeatures.buildConfig = true
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    // ServerHealthChecker (prueba del candidato con GET {base}/api/health sin
    // pasar por el singleton de Retrofit, ver ServerHealthChecker.kt).
    implementation(libs.okhttp.core)

    // Tests unitarios puros de normalización/validación de URL (ServerUrlRulesTest).
    testImplementation(libs.junit)
}
