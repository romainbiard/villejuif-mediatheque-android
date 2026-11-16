// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

import fr.mediatheque.buildlogic.library
import fr.mediatheque.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Module d'écran (`:feature:*`) : Compose, Hilt, ViewModel, Navigation 3 et les modules `core` utiles.
 * Un module feature ne dépend jamais d'un autre module feature.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("mediatheque.android.library")
            pluginManager.apply("mediatheque.android.compose")
            pluginManager.apply("mediatheque.hilt")
            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:data"))
                add("implementation", project(":core:model"))
                add("implementation", libs.library("androidx-compose-material3"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-navigation3-runtime"))
                add("testImplementation", project(":core:testing"))
                add("testImplementation", libs.library("turbine"))
            }
        }
    }
}
