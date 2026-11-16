// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

import fr.mediatheque.buildlogic.library
import fr.mediatheque.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Screen module (`:feature:*`): Compose, Hilt, ViewModel, Navigation 3, and the useful `core` modules.
 * A feature module never depends on another feature module.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("mediatheque.android.library")
            pluginManager.apply("mediatheque.android.compose")
            pluginManager.apply("mediatheque.hilt")
            // Navigation 3 routes are serializable, so the back stack survives process death.
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
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
