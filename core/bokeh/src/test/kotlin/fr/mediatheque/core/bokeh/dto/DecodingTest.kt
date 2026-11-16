// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.dto

import fr.mediatheque.core.bokeh.BokehError
import fr.mediatheque.core.bokeh.testing.Fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodingTest {
    @Test
    fun searchResponse() {
        val response = SearchResponseDto.parse(Fixture.string("search-harry-potter.json"))
        assertEquals(2, response.total)
        val record = response.records.first()
        assertTrue("Harry Potter à l'école des sorciers" in record.titles)
        assertEquals(listOf("Rowling|Joanne Kathleen"), record.creators)
        assertEquals("Harry Potter", record.serial)
        assertEquals("1", record.type.code)
        assertEquals(745828, record.noticeId)
        val item = record.items.first()
        assertEquals("Médiathèque Elsa-Triolet", item.subfield("a"))
        assertEquals("SF ROW", item.shelfMark)
    }

    @Test
    fun aDigitalRecordHasNoZone995() {
        val record = SearchResponseDto.parse(Fixture.string("search-artevod.json")).records.first()
        assertEquals("ArteVod", record.type.code)
        assertTrue(record.items.first().zone995.isEmpty())
        assertEquals("A consulter sur le portail", record.items.first().activity)
    }

    @Test
    fun account() {
        val account = AccountResponseDto.parse(Fixture.string("account.json")).account
        assertEquals("0012345", account.login)
        assertEquals("PULP Nord", account.libraryDetails.label)
        assertEquals(1, account.counters.lateLoans)
        assertEquals(1, account.counters.waitingToBePulled)
        assertEquals("2027-03-14", account.card.expireAt)
        assertFalse(account.permissions.holdForbidden)
        // Identifiant d'abonné des routes /abonne/... et préférences.
        assertEquals(100001, account.cards?.first()?.id)
        assertEquals("", account.identity.nickname)
        assertEquals(0, account.preferences?.newsletterCount)
    }

    @Test
    fun loansAndHolds() {
        val loans = LoansResponseDto.parse(Fixture.string("loans.json")).loans
        assertEquals(3, loans.size)
        assertEquals("100001_36674", loans[0].id)
        assertEquals("2026-11-20", loans[0].dateDue)
        assertEquals("", loans[1].record?.thumbnail)

        val holds = HoldsResponseDto.parse(Fixture.string("holds.json")).holds
        assertEquals("Pas disponible avant le 14/11/2026", holds.first().status)
        assertEquals("PULP Nord", holds.first().library)
    }

    @Test
    fun holdConfirmationPopup() {
        val popup = PopupDto.parse(Fixture.string("hold-confirmation.json"))
        assertEquals("Réservation", popup.title)
        assertTrue(popup.content.startsWith("Votre réservation est enregistrée."))
    }

    @Test
    fun selectionToggleAnswerTellsTheNewState() {
        assertTrue(SelectionToggleDto.parse(Fixture.string("selection-added.json")).holdsRecord)
        assertFalse(SelectionToggleDto.parse(Fixture.string("selection-removed.json")).holdsRecord)
    }
}

class LenientAccountDecodingTest {
    @Test
    fun holdsWithNullFieldsAndAMalformedOne() {
        val holds = HoldsResponseDto.parse(
            """
            {"holds":[
              {"id":"100001_1","title":null,"status":null,"library":null,"record":null},
              {"title":"Sans identifiant","status":"Pas disponible avant le 14/11/2026","library":"PULP Nord"},
              {"id":"100001_2","title":"Un titre","status":"Disponible","library":"Elsa-Triolet","record":{"id":"oops"}}
            ]}
            """.trimIndent()
        ).holds

        // La réservation sans identifiant ne peut pas être annulée : c'est la seule écartée.
        assertEquals(listOf("100001_1", "100001_2"), holds.map { it.id })
        assertNull(holds[0].status)
        assertEquals("", holds[0].title)
        assertEquals("", holds[0].library)
        // Une notice mal formée ne fait perdre que la couverture, pas la réservation.
        assertNull(holds[1].record)
    }

    @Test
    fun loansWithNullFieldsAndAMalformedOne() {
        val loans = LoansResponseDto.parse(
            """
            {"loans":[
              {"id":"100001_1","title":"Un DVD","author":null,"date_due":"2026-11-20","library":null,"record":null},
              {"id":null,"title":"Sans identifiant","author":"Quelqu'un","date_due":"2026-11-20","library":"PULP Sud"},
              {"id":"100001_2","title":"Une revue","author":"","date_due":"2026-11-27","library":"PULP Sud"}
            ]}
            """.trimIndent()
        ).loans

        assertEquals(listOf("100001_1", "100001_2"), loans.map { it.id })
        assertEquals("", loans[0].author)
        assertEquals("", loans[0].library)
        assertEquals("2026-11-20", loans[0].dateDue)
    }

    @Test
    fun accountWithNullsAndNumbersAsStrings() {
        val account = AccountResponseDto.parse(
            """
            {"account":{
              "login":"0012345",
              "identity":{"first_name":"Camille","last_name":null,"nickname":null},
              "contact":{"email":null,"phone":"","mobile":null,"address":null,"zip_code":94800,"city":null},
              "card":null,
              "library_details":{"id":"3","label":"PULP Nord"},
              "permissions":{"hold_forbidden":0,"digital_resources_forbidden":null},
              "counters":{"loans":"2","late_loans":null,"waiting_to_be_pulled":1},
              "message":null,
              "preferences":null,
              "cards":[{"id":100001},{"id":null}]
            }}
            """.trimIndent()
        ).account

        assertEquals("0012345", account.login)
        assertEquals("Camille", account.identity.firstName)
        assertEquals("", account.identity.lastName)
        assertEquals("", account.contact.email)
        assertEquals("94800", account.contact.zipCode)
        assertEquals("", account.card.expireAt)
        assertFalse(account.card.expired)
        assertEquals(3, account.libraryDetails.id)
        assertFalse(account.permissions.holdForbidden)
        assertEquals(2, account.counters.loans)
        assertEquals(0, account.counters.lateLoans)
        assertEquals(1, account.counters.waitingToBePulled)
        assertEquals("", account.message.content)
        assertNull(account.preferences)
        assertEquals(listOf(100001), account.cards?.map { it.id })
    }

    @Test
    fun aTextTrueIsNotAFlag() {
        val account = AccountResponseDto.parse("""{"account":{"login":"0012345","permissions":{"hold_forbidden":"true"}}}""").account
        assertFalse(account.permissions.holdForbidden)
        assertNull(account.cards)
    }

    /** Sans numéro de carte, rien du compte ne peut fonctionner : ce champ reste strict. */
    @Test
    fun anAccountWithoutCardNumberFails() {
        assertThrows(BokehError.UnexpectedFormat::class.java) {
            AccountResponseDto.parse("""{"account":{"login":null,"identity":{"first_name":"Camille","last_name":"Martin"}}}""")
        }
        assertThrows(BokehError.UnexpectedFormat::class.java) { AccountResponseDto.parse("<html>") }
    }
}
