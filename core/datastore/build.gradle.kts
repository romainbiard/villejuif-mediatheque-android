// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
    alias(libs.plugins.mediatheque.hilt)
}

// Stockage local : réglages (DataStore), coffre des identifiants (Tink + Android Keystore),
// instantané du compte et centre de notifications. Exclus des sauvegardes (voir app/src/main/res/xml).
dependencies {
    implementation(projects.core.model)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
}
