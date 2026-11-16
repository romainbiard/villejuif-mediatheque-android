// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.mediatheque.core.data.AccountRepository
import fr.mediatheque.core.data.AgendaRepository
import fr.mediatheque.core.data.CatalogRepository
import fr.mediatheque.core.data.DigitalRepository
import fr.mediatheque.core.data.LibraryRepository
import fr.mediatheque.core.data.PortalAccountRepository
import fr.mediatheque.core.data.PortalAgendaRepository
import fr.mediatheque.core.data.PortalCatalogRepository
import fr.mediatheque.core.data.PortalDigitalRepository
import fr.mediatheque.core.data.PortalLibraryRepository
import fr.mediatheque.core.data.PortalProfileRepository
import fr.mediatheque.core.data.PortalSession
import fr.mediatheque.core.data.ProfileRepository
import fr.mediatheque.core.data.SessionRepository

/** The real app: every repository talks to the library portal. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun sessionRepository(repository: PortalSession): SessionRepository

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
}
