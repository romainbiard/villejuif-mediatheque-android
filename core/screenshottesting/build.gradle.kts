// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
    alias(libs.plugins.mediatheque.android.compose)
}

// Helpers for the screenshot tests of the screen modules: the themes, font sizes and windows each screen is drawn in.
dependencies {
    api(projects.core.designsystem)
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.compose.ui.test.junit4.accessibility)
    api(libs.androidx.compose.ui.test.manifest)
    api(libs.junit)
}
