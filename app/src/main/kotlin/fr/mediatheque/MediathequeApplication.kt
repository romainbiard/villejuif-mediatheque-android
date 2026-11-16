// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import fr.mediatheque.core.data.SessionStore
import javax.inject.Inject
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class MediathequeApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var sessionStore: SessionStore

    override fun onCreate() {
        super.onCreate()
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
