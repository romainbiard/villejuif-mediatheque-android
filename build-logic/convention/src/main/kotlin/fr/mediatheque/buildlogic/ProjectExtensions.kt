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

/** Bytecode Java 17, la cible recommandée pour Android ; tout avertissement Kotlin est une erreur. */
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
 * Les tests vérifient des textes en français et des échéances à l'heure de Paris :
 * on fixe la langue et le fuseau, quelle que soit la machine (comme `-testLanguage fr` sur iOS).
 */
internal fun Project.configureTests() {
    tasks.withType<Test>().configureEach {
        systemProperty("user.language", "fr")
        systemProperty("user.country", "FR")
        systemProperty("user.timezone", "Europe/Paris")
    }
}

/** Lint strict partout : une alerte casse le build (réglages fins dans lint.xml). */
internal fun Lint.configureLint(project: Project) {
    abortOnError = true
    warningsAsErrors = true
    checkReleaseBuilds = true
    checkDependencies = false
    lintConfig = project.rootProject.file("lint.xml")
    htmlReport = true
    sarifReport = true
    xmlReport = false
}
