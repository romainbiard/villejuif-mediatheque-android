// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

import com.android.build.api.dsl.TestExtension

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

// The baseline profile generator: launch and main journeys of the demo build (no network, no account), compiled
// ahead of time on install so the app starts and scrolls without waiting for the JIT.
// Regenerate on a connected device or emulator: ./gradlew :app:generateBaselineProfile
configure<TestExtension> {
    namespace = "fr.mediatheque.baselineprofile"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
    flavorDimensions += "data"
    productFlavors {
        create("prod") { dimension = "data" }
        create("demo") { dimension = "data" }
    }
}

// Journeys are recorded on the demo data only: the prod build would need the network and a card.
androidComponents {
    beforeVariants(selector().withFlavor("data" to "prod")) { it.enable = false }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
