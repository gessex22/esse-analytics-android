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
}
