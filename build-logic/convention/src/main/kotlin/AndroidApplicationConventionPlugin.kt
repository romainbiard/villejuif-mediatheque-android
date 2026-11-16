// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

import com.android.build.api.dsl.ApplicationExtension
import fr.mediatheque.buildlogic.configureKotlin
import fr.mediatheque.buildlogic.configureLint
import fr.mediatheque.buildlogic.configureTests
import fr.mediatheque.buildlogic.int
import fr.mediatheque.buildlogic.javaVersion
import fr.mediatheque.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `:app` module: SDK, Java 17, strict lint. Kotlin is built into AGP 9 (no kotlin-android plugin). */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                compileSdk = libs.int("compileSdk")
                defaultConfig {
                    minSdk = libs.int("minSdk")
                    targetSdk = libs.int("targetSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = javaVersion
                    targetCompatibility = javaVersion
                }
                lint { configureLint(target) }
            }
            configureKotlin()
            configureTests()
        }
    }
}
