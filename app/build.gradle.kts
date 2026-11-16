// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

import java.util.Properties

plugins {
    alias(libs.plugins.mediatheque.android.application)
    alias(libs.plugins.mediatheque.android.compose)
    alias(libs.plugins.mediatheque.hilt)
}

/**
 * Identifiant de l'app : valeur neutre dans le dépôt, préfixe réel hors dépôt (comme Config/Local.xcconfig sur iOS).
 * Ordre : variable MEDIATHEQUE_APPLICATION_ID (CI), puis `mediatheque.applicationId` dans local.properties.
 */
val applicationIdOverride: String? = providers.environmentVariable("MEDIATHEQUE_APPLICATION_ID").orNull
    ?: providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { text -> Properties().apply { load(text.reader()) }.getProperty("mediatheque.applicationId") }

android {
    namespace = "fr.mediatheque"

    defaultConfig {
        applicationId = applicationIdOverride ?: "com.example.mediatheque"
        // Numéro de build de la CI (GITHUB_RUN_NUMBER), 1 en local.
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toInt() ?: 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources {
        // Langue par app (Android 13+) : fr (source) et en, déduites des dossiers values-*.
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.notifications)
    implementation(projects.feature.account)
    implementation(projects.feature.search)
    implementation(projects.feature.agenda)
    implementation(projects.feature.digital)
    implementation(projects.feature.info)
    implementation(projects.widget)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
