// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
    alias(libs.plugins.mediatheque.android.compose)
}

// Charte de Villejuif sur Material 3 : couleurs, typographie, composants. Ne dépend d'aucun autre module.
dependencies {
    api(libs.androidx.compose.material3)
}
