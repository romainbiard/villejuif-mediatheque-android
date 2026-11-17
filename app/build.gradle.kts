// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.mediatheque.android.application)
    alias(libs.plugins.mediatheque.android.compose)
    alias(libs.plugins.mediatheque.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

/**
 * App identifier: a neutral value in the repo, the real prefix kept outside the repo (like Config/Local.xcconfig on iOS).
 * Order: the MEDIATHEQUE_APPLICATION_ID variable (CI), then `mediatheque.applicationId` in local.properties.
 */
val applicationIdOverride: String? = providers.environmentVariable("MEDIATHEQUE_APPLICATION_ID").orNull
    ?: providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { text -> Properties().apply { load(text.reader()) }.getProperty("mediatheque.applicationId") }

android {
    namespace = "fr.mediatheque"

    defaultConfig {
        applicationId = applicationIdOverride ?: "com.example.mediatheque"
        // CI build number (GITHUB_RUN_NUMBER), 1 locally.
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toInt() ?: 1
        versionName = "0.1.0"
    }

    // demo: fictitious data, no network (screenshots, UI tests, trying the app without a card). prod: the portal.
    flavorDimensions += "data"
    productFlavors {
        create("prod") {
            dimension = "data"
            isDefault = true
        }
        // Never published (only prodRelease goes to Play); its release build exists for the baseline profile and the
        // release checks on demo data.
        create("demo") {
            dimension = "data"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Instrumented tests (demo journeys and accessibility checks) on emulators Gradle starts itself, on the CI:
    // ./gradlew pixel6Api35DemoDebugAndroidTest
    testOptions {
        managedDevices {
            localDevices {
                create("pixel6Api35") {
                    device = "Pixel 6"
                    apiLevel = 35
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }

    androidResources {
        // Per-app language (Android 13+): fr (source) and en, derived from the values-* folders.
        generateLocaleConfig = true
    }
}

// The baseline profile is recorded on the demo build (./gradlew :app:generateDemoReleaseBaselineProfile, see
// :baselineprofile), saved in src/demoRelease/generated, and shipped with every build through the main source set:
// the prod build runs the same code on the portal's data.
baselineProfile {
    saveInSrc = true
    automaticGenerationDuringBuild = false
    variants {
        create("demoRelease") { from(project(":baselineprofile")) }
    }
}
android.sourceSets.getByName("main").baselineProfiles.directories.add("src/demoRelease/generated/baselineProfiles")

androidComponents {
    // Static app shortcuts name their target package, which differs per variant (and per local applicationId):
    // the file is generated for each variant rather than written once in res/xml.
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register("generate${name}Shortcuts", GenerateShortcutsTask::class.java) {
            applicationId.set(variant.applicationId)
        }
        variant.sources.res?.addGeneratedSourceDirectory(task, GenerateShortcutsTask::outputDirectory)
    }
}

/** Writes res/xml/shortcuts.xml: "Ma carte", "Rechercher", "Agenda", each opening MainActivity on its screen. */
abstract class GenerateShortcutsTask : DefaultTask() {
    @get:Input
    abstract val applicationId: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        fun shortcut(id: String, link: String, icon: String, short: String, long: String) = """
            |    <shortcut
            |        android:enabled="true"
            |        android:icon="@drawable/$icon"
            |        android:shortcutDisabledMessage="@string/shortcut_disabled"
            |        android:shortcutId="$id"
            |        android:shortcutLongLabel="@string/$long"
            |        android:shortcutShortLabel="@string/$short">
            |        <intent
            |            android:action="android.intent.action.VIEW"
            |            android:targetClass="fr.mediatheque.MainActivity"
            |            android:targetPackage="${applicationId.get()}">
            |            <extra
            |                android:name="fr.mediatheque.extra.LINK"
            |                android:value="$link" />
            |        </intent>
            |    </shortcut>
        """.trimMargin()
        val xml = listOf(
            shortcut("card", "card", "ic_shortcut_card", "shortcut_card_short", "shortcut_card_long"),
            shortcut("search", "search", "ic_shortcut_search", "shortcut_search_short", "shortcut_search"),
            shortcut("agenda", "agenda", "ic_shortcut_agenda", "shortcut_agenda_short", "shortcut_agenda_long")
        ).joinToString(
            separator = "\n",
            prefix = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<shortcuts xmlns:android=\"http://schemas.android.com/apk/res/android\">\n",
            postfix = "\n</shortcuts>\n"
        )
        val file = outputDirectory.get().dir("xml").file("shortcuts.xml").asFile
        file.parentFile.mkdirs()
        file.writeText(xml)
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.notifications)
    implementation(projects.feature.account)
    implementation(projects.feature.search)
    implementation(projects.feature.agenda)
    implementation(projects.feature.digital)
    implementation(projects.feature.info)
    implementation(projects.widget)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(projects.core.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    // Recent enough for Android 16 and later (InputManager changes).
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
