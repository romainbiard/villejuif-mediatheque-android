// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// Domain model (≈ MediathequeCore/Models): subscriber, loans, reservations, libraries, opening hours, agenda.
dependencies {
    implementation(libs.kotlinx.serialization.json)
}
