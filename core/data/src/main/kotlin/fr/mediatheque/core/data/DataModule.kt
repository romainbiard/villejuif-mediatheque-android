// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.mediatheque.core.bokeh.PortalHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun sessionRepository(session: PortalSession): SessionRepository

    @Binds
    abstract fun accountRepository(repository: PortalAccountRepository): AccountRepository

    @Binds
    abstract fun catalogRepository(repository: PortalCatalogRepository): CatalogRepository

    @Binds
    abstract fun agendaRepository(repository: PortalAgendaRepository): AgendaRepository

    @Binds
    abstract fun libraryRepository(repository: PortalLibraryRepository): LibraryRepository

    @Binds
    abstract fun profileRepository(repository: PortalProfileRepository): ProfileRepository

    @Binds
    abstract fun digitalRepository(repository: PortalDigitalRepository): DigitalRepository

    companion object {
        /** A single shared transport: it carries the session cookie. */
        @Provides
        @Singleton
        fun portalHttpClient(solver: WebViewChallengeSolver) = PortalHttpClient(challengeSolver = solver)

        /** Public data only, in the app's cache folder: Android may clear it at any time. */
        @Provides
        @Singleton
        fun dataCache(@ApplicationContext context: Context) = DataCache(File(context.cacheDir, "portal"))
    }
}
