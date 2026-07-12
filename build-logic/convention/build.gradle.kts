plugins {
    `kotlin-dsl`
}

group = "com.esseanalytics.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

// Registra cada convention plugin bajo el id que se usa en libs.versions.toml
// (sección [plugins], prefijo "essenalytics.") — así cada módulo solo escribe
// `alias(libs.plugins.essenalytics.android.feature)` en vez de repetir 15
// líneas de configuración de Compose/Kotlin/lint en cada build.gradle.kts.
gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "essenalytics.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "essenalytics.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "essenalytics.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "essenalytics.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
    }
}
