// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

pluginManagement {
    includeBuild("build-logic")
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
    // Repositories are declared here only, never in a module.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Mediatheque"

include(":app")
include(":widget")

include(":core:bokeh")
include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:datastore")
include(":core:notifications")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")

include(":feature:account")
include(":feature:search")
include(":feature:agenda")
include(":feature:digital")
include(":feature:info")
