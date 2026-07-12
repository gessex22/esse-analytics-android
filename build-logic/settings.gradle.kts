dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Reusa el mismo catálogo de versiones del proyecto raíz — una sola fuente
    // de verdad para versiones, tanto para los módulos como para estos plugins.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
