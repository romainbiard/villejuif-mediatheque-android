// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.mediatheque.core.bokeh.PortalHttpClient
import java.io.File
import java.time.Clock
import javax.inject.Singleton

/**
 * The portal's transport and cache. Which repositories the app uses, the portal's or the demo's, is decided by
 * the app's build flavor (app/src/prod and app/src/demo).
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    /** A single shared transport: it carries the session cookie. */
    @Provides
    @Singleton
    fun portalHttpClient(solver: WebViewChallengeSolver) = PortalHttpClient(challengeSolver = solver)

    /** Public data only, in the app's cache folder: Android may clear it at any time. */
    @Provides
    @Singleton
    fun dataCache(@ApplicationContext context: Context) = DataCache(File(context.cacheDir, "portal"))

    /** The current time, replaceable in tests. */
    @Provides
    fun clock(): Clock = Clock.systemUTC()
}
