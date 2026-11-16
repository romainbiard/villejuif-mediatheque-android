// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.parser

import fr.mediatheque.core.bokeh.testing.Fixture
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {
    @Test
    fun iCalEvent() {
        val event = ICalParser.events(Fixture.string("event-1019.ics")).first()
        assertEquals("1019", event.uid)
        assertEquals("Conférence : IA - Comprendre les outils d’aujourd’hui", event.summary)
        assertEquals("Médiathèque Elsa-Triolet", event.location)
        assertEquals(48.792636, event.latitude!!, 0.0)
        assertEquals(2.362783, event.longitude!!, 0.0)
        assertTrue(event.imageUrl!!.endsWith("323onfrence-ia.jpg"))
        assertTrue(event.description!!.contains("Nasreddine Menacer, professeur"))
        assertEquals(LocalDateTime.of(2026, 11, 4, 16, 0), LocalDateTime.ofInstant(event.start, ZoneId.of("Europe/Paris")))
    }

    @Test
    fun iCalUnfoldsContinuationLines() {
        assertEquals(listOf("A:abcdef", "B:x"), ICalParser.unfold("A:abc\n def\nB:x"))
    }

    @Test
    fun iCalSplitsAtTheFirstColonOutsideQuotes() {
        val property = ICalParser.Property.parse("ATTACH;X-BKH-=IMAGE:https://exemple.fr/a.jpg")
        assertEquals("ATTACH", property.name)
        assertEquals("https://exemple.fr/a.jpg", property.value)
        assertEquals("a,b;c\n", ICalParser.unescape("a\\,b\\;c\\n"))
    }

    @Test
    fun explicitCredentialRefusal() {
        assertTrue(HtmlParsers.loginRejected(Fixture.string("login-rejected.html")))
        // Un formulaire de connexion vierge ou une autre page n'est pas un refus.
        assertFalse(HtmlParsers.loginRejected(Fixture.string("login.html")))
        assertFalse(HtmlParsers.loginRejected("<html><body><p>Service indisponible</p></body></html>"))
        assertFalse(
            HtmlParsers.loginRejected(
                """<ul class="errors element_error_messages"><li class="element_error_message">Une valeur est requise.</li></ul>"""
            )
        )
    }

    @Test
    fun calendarEvents() {
        val events = HtmlParsers.calendarEvents(Fixture.string("calendar.html"))
        assertTrue(events.size >= 10)
        val ronde = events.first { it.articleId == 990 }
        assertEquals("Ronde des histoires", ronde.title)
        assertEquals("Pulp Nord", ronde.location)
        assertEquals("31/10/2026 à 10:30", ronde.startText)
        assertEquals("31/10/2026 à 11:00", ronde.endText)
        assertEquals("Lectures", ronde.eventType)
        assertEquals("Dès 4 ans", ronde.audience)
        assertTrue(ronde.imageUrl!!.endsWith("ronde-des-histoires.jpg"))
        assertEquals(events.size, events.map { it.articleId }.toSet().size)
    }

    @Test
    fun liveCopies() {
        val copy = HtmlParsers.copies(Fixture.string("notice-exemplaires.html")).first()
        assertEquals("Médiathèque Elsa-Triolet", copy.libraryName)
        assertEquals("En prêt", copy.availability)
        assertEquals("Jeunesse", copy.section)
        assertEquals("SF ROW", copy.shelfMark)
        assertEquals("17/11/2026", copy.dueDateText)
        assertEquals("Déjà 2 réservations", copy.holdsText)
    }

    @Test
    fun availabilityBadge() {
        assertEquals(AvailabilityDto("En prêt", isAvailable = false), HtmlParsers.availability(Fixture.string("availability.html")))
    }

    @Test
    fun libraries() {
        val libraries = HtmlParsers.libraries(Fixture.string("infos-pratiques.html"))
        assertEquals(listOf("elsa_triolet", "pulp_nord", "pulp_sud"), libraries.map { it.slug }.sorted())
        val elsa = libraries.first { it.slug == "elsa_triolet" }
        assertEquals("Médiathèque Elsa-Triolet", elsa.name)
        assertEquals(listOf("1 esplanade Pierre-Yves-Cosnier", "94800 Villejuif"), elsa.addressLines)
        assertEquals("01 86 93 33 00", elsa.phone)
        assertEquals("Mardi : 13h - 19h", elsa.openingHours.first())
        assertEquals(5, elsa.openingHours.size)
        assertEquals(1, elsa.bookmarkLibraryId)
        assertEquals("https://demarches.villejuif.fr/villejuif-et-moi/contact-services/?service_id=7", elsa.contactUrl)
    }

    @Test
    fun csrfToken() {
        assertTrue(HtmlParsers.csrfToken(Fixture.string("login.html")).length >= 32)
    }

    @Test
    fun pickupOptions() {
        val html = """<form><label><input type="radio" name="code_annexe" value="1">Médiathèque Elsa-Triolet</label>""" +
            """<label><input type="radio" name="code_annexe" value="3" checked>PULP Nord</label>""" +
            """<label><input type="radio" name="code_annexe" value="2">PULP Sud</label></form>"""
        val options = HtmlParsers.pickupOptions(html)
        assertEquals(listOf("1", "3", "2"), options.map { it.code })
        assertEquals("PULP Nord", options.first { it.isSelected }.label)
    }

    @Test
    fun ssoTarget() {
        assertEquals("biblio.toutapprendre.com", HtmlParsers.ssoTarget(Fixture.string("sso-tout-apprendre.html"))?.host)
    }

    @Test
    fun holdRank() {
        val html = "<div>État de la réservation: Pas disponible avant le 14/11/2026 " +
            "Le prochain document disponible est pour vous. Rang 1</div>"
        assertEquals(listOf(1), HtmlParsers.holdRanks(html))
    }
}

class PatronSpaceParserTest {
    @Test
    fun selectionsWithAndWithoutBasket() {
        val selections = HtmlParsers.selections(Fixture.string("selections.html"))
        assertEquals(listOf("Mes préférés", "Déjà lu"), selections.map { it.title })

        val favorites = selections.first()
        assertEquals(9001, favorites.id)
        assertEquals(501, favorites.basketId)
        assertEquals(2, favorites.count)
        assertEquals("30/10/2026", favorites.updatedText)
        assertEquals(1, favorites.coverUrls.size)
        assertEquals(listOf("Astérix le Gaulois"), favorites.previewTitles)

        val read = selections[1]
        assertEquals(9002, read.id)
        assertEquals(null, read.basketId)
        assertEquals(0, read.count)
    }

    @Test
    fun recordsToRateCarryTheReviewForm() {
        val record = HtmlParsers.recordsToRate(Fixture.string("records-to-rate.html")).first()
        assertEquals(123456, record.recordId)
        assertEquals("LEPETITPRINCE--SAINTEXUPERYA-", record.recordKey)
        assertEquals("Le Petit Prince", record.title)
        assertEquals("Antoine de Saint-Exupéry", record.author)
        assertTrue(record.coverUrl!!.endsWith("/123456.jpe"))
    }

    @Test
    fun emptyReviewsPage() {
        assertTrue(HtmlParsers.reviews(Fixture.string("empty-reviews.html")).isEmpty())
    }

    @Test
    fun contactFormValues() {
        val html = Fixture.string("contact-form.html")
        assertEquals(null, HtmlParsers.fieldValue("pseudo", html))
        assertEquals("camille.martin@exemple.fr", HtmlParsers.fieldValue("mail", html))
        assertEquals("1", HtmlParsers.checkedValue("favoriteSendingChannel", html))
    }

    @Test
    fun bookmarkedLibrariesComeFromTheRemoveLinks() {
        assertEquals(setOf(3), HtmlParsers.bookmarkedLibraryIds(Fixture.string("selections.html")))
    }

    @Test
    fun formErrorsAreRead() {
        val html = """<div class="jumbotron_rich_content"><ul class="errors"><li>Les mots de passe ne correspondent pas</li></ul></div>"""
        assertEquals(listOf("Les mots de passe ne correspondent pas"), HtmlParsers.formErrors(html))
        assertTrue(HtmlParsers.formErrors(Fixture.string("contact-form.html")).isEmpty())
    }

    @Test
    fun theRecordPageSaysWhichDefaultSelectionsHoldIt() {
        val html = Fixture.string("notice-selections.html")
        assertTrue(HtmlParsers.isSignedIn(html))
        assertEquals(setOf("Mes préférés"), HtmlParsers.defaultSelectionsHolding(html))
        // Page publique (session expirée) : mêmes liens, tous « ajouter », et pas de lien de déconnexion.
        assertFalse(HtmlParsers.isSignedIn("""<html><body><a href="/auth/login">Mon compte</a></body></html>"""))
    }
}
