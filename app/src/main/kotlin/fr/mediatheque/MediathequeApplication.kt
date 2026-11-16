// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class MediathequeApplication :
    Application(),
    SingletonImageLoader.Factory {
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
