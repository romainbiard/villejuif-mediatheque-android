// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.notifications.BackgroundSync
import fr.mediatheque.core.notifications.LocalNotifications
import javax.inject.Inject
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class MediathequeApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    @Inject
    lateinit var sessionStore: SessionStore

    @Inject
    lateinit var notifications: LocalNotifications

    @Inject
    lateinit var backgroundSync: BackgroundSync

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** WorkManager starts on demand with Hilt's factory (its default initializer is removed from the manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        // The whole app in the background, not one screen: the biometric lock counts from there.
        val process = ProcessLifecycleOwner.get()
        process.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) = sessionStore.onBackground()

                override fun onStart(owner: LifecycleOwner) {
                    process.lifecycleScope.launch { sessionStore.onForeground() }
                }
            }
        )
        // The background sync runs while signed in, locked included, and stops at sign-out.
        process.lifecycleScope.launch {
            sessionStore.state.collect { state ->
                when (state) {
                    is SessionStore.State.LoggedIn -> backgroundSync.start()
                    SessionStore.State.LoggedOut -> backgroundSync.stop()
                    SessionStore.State.Locked, SessionStore.State.Restoring -> Unit
                }
            }
        }
    }

    /**
     * Covers and posters stay from one launch to the next: the default cache empties as soon as a search is
     * scrolled through. In the cache folder, which Android may clear when space runs low.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images").toOkioPath())
                .maxSizeBytes(IMAGE_CACHE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()

    private companion object {
        const val IMAGE_CACHE_BYTES = 250L * 1024 * 1024
    }
}
