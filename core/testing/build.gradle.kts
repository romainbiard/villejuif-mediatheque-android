// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
}

// Repository fakes, test rules and (anonymized) portal fixtures, shared by the tests.
dependencies {
    api(projects.core.model)
    api(projects.core.data)
    implementation(libs.androidx.datastore.preferences)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
