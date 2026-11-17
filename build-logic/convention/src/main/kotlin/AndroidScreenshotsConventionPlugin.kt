// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

import com.android.build.api.dsl.LibraryExtension
import fr.mediatheque.buildlogic.library
import fr.mediatheque.buildlogic.libs
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Screenshot tests of a screen module: Roborazzi on Robolectric, on the JVM. The reference images live next to the
 * tests (src/test/screenshots) and are reviewed like code.
 * - `./gradlew recordRoborazziDebug` records them again after an intended change;
 * - `./gradlew verifyRoborazziDebug` (CI) fails when a screen no longer matches its reference.
 */
class AndroidScreenshotsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")
            extensions.configure<LibraryExtension> {
                testOptions.unitTests.isIncludeAndroidResources = true
            }
            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
            }
            dependencies {
                add("testImplementation", project(":core:screenshottesting"))
                add("testImplementation", libs.library("robolectric"))
            }
        }
    }
}
