// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.android.library)
    alias(libs.plugins.mediatheque.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Repositories, session (automatic reconnection), public data cache, account synchronization,
// BunkerWeb challenge solver (off-screen WebView).
dependencies {
    api(projects.core.model)
    api(projects.core.domain)
    api(projects.core.bokeh)
    api(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.webkit)

    testImplementation(testFixtures(projects.core.bokeh))
    testImplementation(libs.androidx.datastore.preferences)

    // The BunkerWeb solver runs in a real WebView, on a device.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
