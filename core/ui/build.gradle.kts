// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
    alias(libs.plugins.mediatheque.android.compose)
}

// Components built from the domain (≈ CoreUI): error screens, shared document sheet.
dependencies {
    api(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.data)
}
