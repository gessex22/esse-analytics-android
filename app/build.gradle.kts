plugins {
    alias(libs.plugins.essenalytics.android.application)
    alias(libs.plugins.essenalytics.android.hilt)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.esseanalytics.android.app"

    defaultConfig {
        applicationId = "com.esseanalytics.android"
        versionCode = 1
        versionName = "0.1.0-alpha"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false // se activa junto con las reglas de ProGuard/R8, no en Fase 0
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:designsystem"))

    implementation(project(":feature:auth"))
    implementation(project(":feature:library"))
    implementation(project(":feature:ingest"))
    implementation(project(":feature:upload"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:sync"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:users"))
    implementation(project(":feature:gems"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
