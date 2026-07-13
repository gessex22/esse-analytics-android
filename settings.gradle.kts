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
    // Declarado explícito (no solo por convención de carpeta) — en setups con
    // includeBuild("build-logic") que declara su propio catálogo "libs", el
    // catálogo implícito del root a veces no se genera bien para el sync del
    // IDE. Ver: los alias libs.plugins.* (resueltos a nivel de settings) andaban
    // bien, pero libs.<libreria> (resuelto a nivel de proyecto) tiraba
    // "Unresolved reference" — exactamente el síntoma de este bug conocido.
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "EsseAnalytics"

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
