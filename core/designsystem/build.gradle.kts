// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
    alias(libs.plugins.mediatheque.android.compose)
}

// Villejuif style guide on Material 3: colors, typography, components. Depends on no other module.
dependencies {
    api(libs.androidx.compose.material3)
}
