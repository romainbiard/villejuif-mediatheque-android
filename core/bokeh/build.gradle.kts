// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    // Anonymized portal captures, shared with the tests of the other modules (testFixtures(projects.core.bokeh)).
    `java-test-fixtures`
}

// Transport and formats of the Bokeh portal (≈ BokehAPI): routes, OkHttp, JSON DTOs, HTML and iCal parsing.
// The only layer that changes if the portal changes.
dependencies {
    api(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
