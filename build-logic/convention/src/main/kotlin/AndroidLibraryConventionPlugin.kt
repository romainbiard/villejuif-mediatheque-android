// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

import com.android.build.api.dsl.LibraryExtension
import fr.mediatheque.buildlogic.configureKotlin
import fr.mediatheque.buildlogic.configureLint
import fr.mediatheque.buildlogic.configureTests
import fr.mediatheque.buildlogic.int
import fr.mediatheque.buildlogic.javaVersion
import fr.mediatheque.buildlogic.library
import fr.mediatheque.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Bibliothèque Android. L'espace de noms suit le chemin du module :
 * `:feature:account` → `fr.mediatheque.feature.account`.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                namespace = "fr.mediatheque" + path.replace(':', '.').replace('-', '_')
                compileSdk = libs.int("compileSdk")
                defaultConfig {
                    minSdk = libs.int("minSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                testOptions.targetSdk = libs.int("targetSdk")
                compileOptions {
                    sourceCompatibility = javaVersion
                    targetCompatibility = javaVersion
                }
                lint {
                    targetSdk = libs.int("targetSdk")
                    configureLint(target)
                }
            }
            configureKotlin()
            configureTests()
            dependencies {
                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
