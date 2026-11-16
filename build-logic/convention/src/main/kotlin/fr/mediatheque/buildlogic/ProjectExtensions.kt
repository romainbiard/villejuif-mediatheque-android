// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

internal fun VersionCatalog.int(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()

/** Java 17 bytecode, the recommended target for Android; every Kotlin warning is an error. */
internal val javaVersion = JavaVersion.VERSION_17

internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }
}

/**
 * Tests check French text and deadlines in Paris time:
 * we pin the language and time zone, whatever the machine (like `-testLanguage fr` on iOS).
 */
internal fun Project.configureTests() {
    tasks.withType<Test>().configureEach {
        systemProperty("user.language", "fr")
        systemProperty("user.country", "FR")
        systemProperty("user.timezone", "Europe/Paris")
    }
}

/** Strict lint everywhere: any warning breaks the build (fine-grained settings in lint.xml). */
internal fun Lint.configureLint(project: Project) {
    abortOnError = true
    warningsAsErrors = true
    checkReleaseBuilds = true
    checkDependencies = false
    lintConfig = project.rootProject.file("lint.xml")
}
