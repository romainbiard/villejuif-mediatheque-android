// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
    alias(libs.plugins.mediatheque.android.compose)
}

// Composants construits depuis le domaine (≈ CoreUI) : écrans d'erreur, fiche document partagée.
dependencies {
    api(projects.core.designsystem)
    implementation(projects.core.model)
}
