pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        maven { url = uri("https://artifactory.videolan.org/artifactory/public") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "CalmSource"
include(":app-mobile")
include(":app-tv")
include(":core:model")
include(":core:database")
include(":core:network")
include(":core:parser")
include(":core:playback")
include(":feature:iptv")
include(":feature:extensions")
include(":feature:debrid")
include(":feature:search")
include(":core:sourceintelligence")
include(":core:discoveryengine")
include(":core:data")

