// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

// Plugins are declared here without being applied: a single version loaded for the whole build.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless)
}

// Code format: ktlint (rules in .editorconfig) and license header, as on iOS.
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    val header =
        listOf(
            "// Médiathèque",
            "// SPDX-License-Identifier: MIT",
            "// See LICENSE in the project root for the full license text and copyright notice."
        ).joinToString(separator = "\n", postfix = "\n\n")
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
        licenseHeader(header, "(package |import |@file:|class |object |internal |fun )")
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}
