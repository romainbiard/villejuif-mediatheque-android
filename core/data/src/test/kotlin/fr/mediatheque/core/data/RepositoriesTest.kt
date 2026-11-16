// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.bokeh.testing.FakePortal
import fr.mediatheque.core.bokeh.testing.FakePortal.Response
import fr.mediatheque.core.bokeh.testing.Fixture
import fr.mediatheque.core.model.DefaultSelection
import fr.mediatheque.core.model.Event
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.RenewalResult
import fr.mediatheque.core.model.SearchQuery
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The repositories for real, against a fake portal that serves the captured pages. */
abstract class PortalTest {
    private val harnesses = mutableListOf<PortalHarness>()

    @After
    fun close() = harnesses.forEach { it.close() }

    protected fun harness(signedIn: Boolean = true, respond: (FakePortal.Request) -> Response) =
        PortalHarness(signedIn, respond).also { harnesses += it }
}

class AccountRepositoryTest : PortalTest() {
    private fun PortalHarness.account() = PortalAccountRepository(http, session)

    @Test
    fun loansComeFromTheApi() = runTest {
        val loans = harness { Response.json("loans.json") }.account().loans()
        assertEquals(listOf("100001_36674", "100001_55179", "100001_55180"), loans.map { it.id })
        assertEquals(Paris.instant(2026, 11, 20), loans[0].dueDate)
        assertEquals(Library.PULP_NORD, loans[0].library)
        assertNull(loans[1].coverUrl)
    }

    @Test
    fun holdsTakeTheirQueuePositionFromTheHoldsPage() = runTest {
        val harness = harness { request ->
            if (request.path.startsWith(
                    "/abonne/reservations/"
                )
            ) {
                Response.htmlLiteral("<html><body><p>Rang 2</p></body></html>")
            } else {
                Response.json("holds.json")
            }
        }
        val holds = harness.account().holds()
        assertEquals(1, holds.size)
        assertEquals(2, holds.first().rank)
        assertEquals("Astérix n° 39 · Astérix et le griffon", holds.first().title)
        // The HTML page uses the patron identifier taken from the hold's own identifier.
        assertTrue(harness.portal.requests.any { it.path == "/abonne/reservations/id_profil/1/id/100001" })
    }

    @Test
    fun holdsStillShowWhenTheRankCannotBeRead() = runTest {
        val harness = harness { request ->
            if (request.path.startsWith("/abonne/reservations/")) Response(status = 500) else Response.json("holds.json")
        }
        val holds = harness.account().holds()
        assertEquals(1, holds.size)
        assertNull(holds.first().rank)
    }

    /** Seen in November 2026: the portal sends `"status": null` for a hold. The list still shows. */
    @Test
    fun aHoldWithoutStatusStillShows() = runTest {
        val holds = Fixture.string("holds.json").replace(""""Pas disponible avant le 14\/11\/2026"""", "null")
        assertTrue(holds.contains(""""status":null"""))
        val harness = harness { request ->
            if (request.path.startsWith("/abonne/reservations/")) Response.htmlLiteral("<p>Rang 1</p>") else Response.jsonLiteral(holds)
        }
        val hold = harness.account().holds().first()
        assertEquals("", hold.statusText)
        assertFalse(hold.isReadyForPickup)
        assertNull(hold.expectedDate)
        assertEquals(1, hold.rank)
    }

    @Test
    fun renewalComparesTheDueDatesBeforeAndAfter() = runTest {
        // After the renewal, only the first loan has a later due date.
        val renewed = Fixture.string("loans.json").replace("2026-11-20", "2026-12-11")
        val harness = harness { request ->
            when {
                request.path.startsWith("/abonne/prolongerPret/") -> Response.htmlLiteral("<html></html>")
                request.earlier.any { it.path.startsWith("/abonne/prolongerPret/") } -> Response.jsonLiteral(renewed)
                else -> Response.json("loans.json")
            }
        }
        val results = harness.account().renew(listOf("100001_36674", "100001_55179"))

        assertEquals(
            listOf(RenewalResult.Renewed("100001_36674", Paris.instant(2026, 12, 11)), RenewalResult.Refused("100001_55179")),
            results
        )
        val renewal = harness.portal.requests.first { it.path.startsWith("/abonne/prolongerPret/") }
        assertTrue(renewal.path.endsWith("/id_pret/100001_36674;100001_55179"))
    }

    @Test
    fun preparingAHoldReadsTheLinkAndThePickupLibraries() = runTest {
        val link = "/recherche/reservation-pickup-ajax/record_id/7/copy_id/9/on_record/1"
        val options = """{"result":"CONTENT","content":"<a href=\"$link\">Réserver</a>"}"""
        val form = """{"result":"CONTENT","content":"""" +
            """<input type=\"radio\" name=\"code_annexe\" id=\"a1\" value=\"1\"><label for=\"a1\">Elsa-Triolet</label>""" +
            """<input type=\"radio\" name=\"code_annexe\" id=\"a3\" value=\"3\" checked><label for=\"a3\">PULP Nord</label>"}"""
        val harness = harness { request ->
            if (request.path.startsWith("/recherche/reserver/")) Response.jsonLiteral(options) else Response.jsonLiteral(form)
        }
        val request = harness.account().prepareHold(7)
        assertEquals("recherche/reservation-pickup-ajax/record_id/7/copy_id/9/on_record/1", request.pickupPath)
        assertEquals(listOf(Library.ELSA_TRIOLET, Library.PULP_NORD), request.pickupLibraries)
        assertEquals(Library.PULP_NORD, request.defaultPickup)
    }

    @Test
    fun placingAHoldReturnsTheSitesMessageWithoutHtml() = runTest {
        val harness = harness { Response.json("hold-confirmation.json") }
        val request = HoldRequest("recherche/reservation-pickup-ajax/record_id/7", listOf(Library.PULP_NORD), Library.PULP_NORD)
        val message = harness.account().placeHold(request, Library.PULP_NORD)

        assertTrue(message.startsWith("Votre réservation est enregistrée.\nNous vous informerons"))
        assertFalse(message.contains("<"))
        assertEquals(Library.PULP_NORD.code, harness.portal.requests.last().form["code_annexe"])
    }

    @Test
    fun aRefusedHoldSaysWhy() = runTest {
        val harness = harness {
            Response.jsonLiteral("""{"result":"CONTENT","content":"Vous avez atteint le nombre maximum de réservations."}""")
        }
        val request = HoldRequest("recherche/reservation-pickup-ajax/record_id/7", listOf(Library.PULP_NORD), Library.PULP_NORD)
        val error = runCatching { harness.account().placeHold(request, Library.PULP_NORD) }.exceptionOrNull()
        assertEquals(HoldError.Refused("Vous avez atteint le nombre maximum de réservations."), error)
    }

    @Test
    fun cancellingAHoldCallsItsDeleteLink() = runTest {
        val harness = harness { Response.htmlLiteral("<html></html>") }
        harness.account().cancelHold("100001_101613")
        assertEquals(listOf("/abonne/reservations/id_delete/100001_101613"), harness.portal.requests.map { it.path })
    }

    @Test
    fun thePatronIsReadFromTheAccount() = runTest {
        val patron = harness { Response.json("account.json") }.account().patron()
        assertEquals("Camille Martin", patron.displayName)
        assertEquals(Library.PULP_NORD, patron.homeLibrary)
        assertEquals("06 00 00 00 00", patron.phone)
        assertEquals("1 rue de l'Exemple, 94800 Villejuif", patron.postalAddress)
        assertEquals(Paris.instant(2027, 3, 14), patron.cardExpiry)
    }
}

class PublicRepositoriesTest : PortalTest() {
    @get:Rule
    val folder = TemporaryFolder()

    private fun cache() = DataCache(folder.newFolder())

    @Test
    fun searchDecodesTheResultsPage() = runTest {
        val harness = harness { Response.json("search-harry-potter.json") }
        val page = PortalCatalogRepository(harness.http, cache()).search(SearchQuery("harry potter"))
        assertTrue(page.total > 0)
        assertEquals("Joanne Kathleen Rowling", page.documents.first().authors.first())
        assertTrue(harness.portal.requests.first().path.startsWith("/recherche/simple/expressionRecherche/"))
    }

    @Test
    fun copiesComeFromTheLiveCopiesPage() = runTest {
        val harness = harness { Response.html("notice-exemplaires.html") }
        val copies = PortalCatalogRepository(harness.http, cache()).copies(745828)
        assertTrue(copies.isNotEmpty())
        assertEquals(Library.ELSA_TRIOLET, copies.first().library)
        assertEquals(listOf("/noticeajax/exemplaires/id/745828"), harness.portal.requests.map { it.path })
    }

    @Test
    fun theAvailabilityBadgeIsReadAndCachedBriefly() = runTest {
        val harness = harness { Response.html("availability.html") }
        val catalog = PortalCatalogRepository(harness.http, cache())
        assertEquals("En prêt", catalog.availability(990_001).label)
        catalog.availability(990_001)
        assertEquals(1, harness.portal.requests.size)
    }

    @Test
    fun theAgendaIsReadSortedByDate() = runTest {
        val harness = harness { Response.html("calendar.html") }
        val events = PortalAgendaRepository(harness.http, cache()).events(CachePolicy.RELOAD).value
        assertTrue(events.isNotEmpty())
        val starts = events.mapNotNull { it.start }
        assertEquals(starts.sorted(), starts)
        val ronde = events.first { it.id == 990 }
        assertEquals(Paris.instant(2026, 10, 31, 10, 30), ronde.start)
        assertEquals(Library.PULP_NORD, ronde.library)
        assertFalse(ronde.isAllDay)
    }

    @Test
    fun anEventsDetailComesFromItsICal() = runTest {
        val harness = harness { Response.calendar("event-1019.ics") }
        val event = Event(1019, "Conférence", null, null, null, null, null, false, null, null, null)
        val detail = PortalAgendaRepository(harness.http, cache()).detail(event)
        assertFalse(detail.description.isNullOrEmpty())
        assertEquals(48.792636, detail.latitude!!, 0.0)
        assertEquals(listOf("/cms/ical/id_article/1019"), harness.portal.requests.map { it.path })
    }

    @Test
    fun librariesHaveTheirHours() = runTest {
        val harness = harness { Response.html("infos-pratiques.html") }
        val libraries = PortalLibraryRepository(harness.http, cache()).libraries().value
        val elsa = libraries.first { it.library == Library.ELSA_TRIOLET }
        assertFalse(elsa.hours.isClosedAllDay(LocalDate.of(2026, 11, 3)))
        assertTrue(elsa.hours.isClosedAllDay(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun aDigitalServiceResolvesItsSignInLink() = runTest {
        val harness = harness { Response.html("sso-tout-apprendre.html") }
        val url = PortalDigitalRepository(harness.http, harness.session).target(DigitalService.TOUT_APPRENDRE)
        assertEquals("biblio.toutapprendre.com", url.host)
        assertEquals(listOf("/modules/tout-apprendre"), harness.portal.requests.map { it.path })
    }
}

class ProfileRepositoryTest : PortalTest() {
    private fun PortalHarness.profile() = PortalProfileRepository(http, session)

    /** Patron pages: the account gives the patron identifier used in their path. */
    private fun pages(pages: Map<String, Response>): (FakePortal.Request) -> Response = { request ->
        if (request.path == FakePortal.ACCOUNT_PATH) {
            Response.json("account.json")
        } else {
            pages.entries.firstOrNull { request.path == "/abonne/${it.key}/id_profil/1/id/100001" }?.value ?: Response(status = 404)
        }
    }

    @Test
    fun selectionsAreReadFromThePatronsPage() = runTest {
        val selections = harness(respond = pages(mapOf("selections" to Response.html("selections.html")))).profile().selections()
        assertEquals(listOf("Mes préférés", "Déjà lu"), selections.map { it.name })
        assertEquals(Paris.instant(2026, 10, 30), selections.first().updatedAt)
    }

    @Test
    fun recordsToRateAndReviewsAreRead() = runTest {
        val profile = harness(
            respond = pages(
                mapOf(
                    "donner-des-avis" to Response.html("records-to-rate.html"),
                    "mes-avis" to Response.html("empty-reviews.html")
                )
            )
        ).profile()
        assertTrue(profile.recordsToRate().isNotEmpty())
        assertTrue(profile.reviews().isEmpty())
    }

    @Test
    fun aPasswordChangeSendsBothFieldsAndSurfacesTheSitesError() = runTest {
        val rejection = """<div class="jumbotron_rich_content"><ul class="errors"><li>Le mot de passe est trop court</li></ul></div>"""
        val harness = harness(respond = pages(mapOf("changer-mon-mot-de-passe" to Response.htmlLiteral(rejection))))
        val error = runCatching { harness.profile().changePassword("abc") }.exceptionOrNull()
        assertEquals(ProfileError.Rejected("Le mot de passe est trop court"), error)
        val form = harness.portal.requests.first { it.method == "POST" }.form
        assertEquals("abc", form["new_password"])
        assertEquals("abc", form["confirm_new_password"])
    }

    @Test
    fun contactDetailsAreReadFromTheForm() = runTest {
        val details = harness(respond = pages(mapOf("modifier" to Response.html("contact-form.html")))).profile().contactDetails()
        assertEquals("camille.martin@exemple.fr", details.email)
    }

    @Test
    fun defaultSelectionsAreReadFromTheRecordPage() = runTest {
        val harness = harness { Response.html("notice-selections.html") }
        assertEquals(setOf(DefaultSelection.FAVORITES), harness.profile().defaultSelections(720_102))
        assertEquals(listOf("/recherche/viewnotice/id/720102"), harness.portal.requests.map { it.path })
    }

    @Test
    fun aPublicRecordPageMeansTheSessionExpired() = runTest {
        // Signed out, the page shows every selection as "add": the repository signs in again instead of emptying the heart.
        val harness = harness { request ->
            FakePortal.signIn(request)
                ?: when {
                    request.path == FakePortal.ACCOUNT_PATH -> Response.json("account.json")
                    request.afterSignIn -> Response.html("notice-selections.html")
                    else -> Response.htmlLiteral("""<html><body><a href="/auth/login">Mon compte</a></body></html>""")
                }
        }
        assertEquals(setOf(DefaultSelection.FAVORITES), harness.profile().defaultSelections(720_102))
        assertEquals(1, harness.portal.signIns)
    }

    @Test
    fun addingToASelectionChecksTheSitesAnswer() = runTest {
        val harness = harness { Response.json("selection-added.json") }
        harness.profile().setInDefaultSelection(DefaultSelection.FAVORITES, 720_102, isIn = true)
        val request = harness.portal.requests.first()
        assertTrue(request.path.startsWith("/abonne/ajouter-le-document-a-la-selection/selection_label/"))
        assertTrue(request.path.endsWith("/record_id/720102"))
    }

    @Test
    fun aToggleTheSiteDidNotApplyIsAnError() = runTest {
        // Asked to add, but the site still offers "add": nothing changed.
        val harness = harness { Response.json("selection-removed.json") }
        val error = runCatching {
            harness.profile().setInDefaultSelection(DefaultSelection.FAVORITES, 720_102, isIn = true)
        }.exceptionOrNull()
        assertEquals(ProfileError.SelectionNotChanged, error)
    }
}
