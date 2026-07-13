pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    // El catálogo "libs" se registra solo por convención desde
    // gradle/libs.versions.toml — NO declarar un versionCatalogs{} acá, Gradle
    // ya lo hace automáticamente y tira error si se llama from() dos veces.
}

rootProject.name = "essenalytics-android"

include(":app")

include(":core:model")
include(":core:common")
include(":core:database")
include(":core:network")
include(":core:datastore")
include(":core:media")
include(":core:designsystem")

include(":feature:auth")
include(":feature:library")
include(":feature:ingest")
include(":feature:upload")
include(":feature:calendar")
include(":feature:sync")
include(":feature:stats")
include(":feature:users")
include(":feature:gems")
include(":feature:settings")
