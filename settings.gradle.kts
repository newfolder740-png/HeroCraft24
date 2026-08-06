pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HeroCraft24"

include(":app")

include(":core:model")
include(":core:data")
include(":core:ui")

include(":feature:reference")
include(":feature:spells")
include(":feature:equipment")
include(":feature:characters")
include(":feature:settings")