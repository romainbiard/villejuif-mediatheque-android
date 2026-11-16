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
import fr.mediatheque.core.data.ProfileRepository
import fr.mediatheque.core.data.SessionRepository
import fr.mediatheque.core.data.demo.DemoAccountRepository
import fr.mediatheque.core.data.demo.DemoAgendaRepository
import fr.mediatheque.core.data.demo.DemoCatalogRepository
import fr.mediatheque.core.data.demo.DemoDigitalRepository
import fr.mediatheque.core.data.demo.DemoLibraryRepository
import fr.mediatheque.core.data.demo.DemoProfileRepository
import fr.mediatheque.core.data.demo.DemoSessionRepository

/** The demo app: fictitious data in memory, no network and no account (screenshots, UI tests, trying the app). */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun sessionRepository(repository: DemoSessionRepository): SessionRepository

    @Binds
    abstract fun accountRepository(repository: DemoAccountRepository): AccountRepository

    @Binds
    abstract fun catalogRepository(repository: DemoCatalogRepository): CatalogRepository

    @Binds
    abstract fun agendaRepository(repository: DemoAgendaRepository): AgendaRepository

    @Binds
    abstract fun libraryRepository(repository: DemoLibraryRepository): LibraryRepository

    @Binds
    abstract fun profileRepository(repository: DemoProfileRepository): ProfileRepository

    @Binds
    abstract fun digitalRepository(repository: DemoDigitalRepository): DigitalRepository
}
