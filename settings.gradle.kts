pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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
}

// Placeholder name until the owner picks the final brand (SRS 1.3).
rootProject.name = "APP_NAME"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:domain")
include(":core:database")
