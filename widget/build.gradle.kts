// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
    alias(libs.plugins.mediatheque.android.compose)
    alias(libs.plugins.mediatheque.hilt)
}

// "Upcoming returns" widget (Jetpack Glance), read from the account snapshot.
dependencies {
    implementation(projects.core.model)
    implementation(projects.core.datastore)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
