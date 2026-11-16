// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.feature)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.datastore)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.play.services.code.scanner)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.androidx.datastore.preferences)
}
