// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** The partner apps on this device. Only the packages declared in the manifest's `<queries>` are visible. */
interface InstalledApps {
    fun isInstalled(packageName: String): Boolean

    /** Opens the app as it is (its own session). `false` when it cannot be opened. */
    fun launch(packageName: String): Boolean

    /** The app's Play Store page: the Play Store app, or the website without it. */
    fun openStore(packageName: String): Boolean
}

internal class AndroidInstalledApps @Inject constructor(@ApplicationContext private val context: Context) : InstalledApps {
    override fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return start(intent)
    }

    override fun openStore(packageName: String): Boolean = start(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())) ||
        start(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))

    // Started from the application context: a new task, as a launcher would.
    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DigitalModule {
    @Binds
    fun installedApps(apps: AndroidInstalledApps): InstalledApps
}
