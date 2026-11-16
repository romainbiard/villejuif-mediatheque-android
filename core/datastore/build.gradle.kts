// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
    alias(libs.plugins.mediatheque.hilt)
}

// Local storage: settings (DataStore), credential vault (Tink + Android Keystore),
// account snapshot and notification center. Excluded from backups (see app/src/main/res/xml).
dependencies {
    implementation(projects.core.model)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
}
