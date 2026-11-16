// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
    alias(libs.plugins.mediatheque.hilt)
}

// Dépôts, session (reconnexion automatique), cache des données publiques, synchronisation du compte,
// solveur du défi BunkerWeb (WebView hors écran).
dependencies {
    api(projects.core.model)
    implementation(projects.core.bokeh)
    implementation(projects.core.domain)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.webkit)

    testImplementation(projects.core.testing)
}
