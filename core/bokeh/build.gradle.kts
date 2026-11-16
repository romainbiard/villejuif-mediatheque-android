// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// Transport et formats du portail Bokeh (≈ BokehAPI) : routes, OkHttp, DTO JSON, analyse HTML et iCal.
// Seule couche qui change si le portail change.
dependencies {
    api(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.mockwebserver)
}
