// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
    alias(libs.plugins.mediatheque.hilt)
}

// Canaux, contenus et planification des notifications (rappels de retour, réservations, agenda).
dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
}
