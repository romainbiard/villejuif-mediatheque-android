// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh

import fr.mediatheque.core.bokeh.testing.Fixture
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointTest {
    private val base = PortalHttpClient.VILLEJUIF_BASE_URL

    @Test
    fun searchEncodesTheQueryAndTheSortWildcard() {
        assertEquals(
            "https://mediatheque.villejuif.fr/recherche/simple/expressionRecherche/harry%20potter/tri/%2A/format/json",
            Endpoint.search("harry potter").url(base).toString()
        )
    }

    @Test
    fun searchCombinesFacetsAndPage() {
        assertEquals(
            "/recherche/simple/expressionRecherche/dune/tri/%2A/facettes/T1-Y2/format/json/page/3",
            Endpoint.search("dune", facets = listOf("T1", "Y2"), page = 3).url(base).encodedPath
        )
    }

    @Test
    fun renewJoinsLoanIdsWithASemicolon() {
        assertEquals(
            "/abonne/prolongerPret/id_profil/1/id/100001/id_pret/100001_1;100001_2",
            Endpoint.renewLoans("100001", listOf("100001_1", "100001_2")).url(base).encodedPath
        )
    }

    @Test
    fun calendarUsesTheServerRenderedAgendaPage() {
        assertEquals("/accueil/agenda/place/3/custom_field_3/12", Endpoint.calendar(placeId = 3, audienceId = 12).url(base).encodedPath)
    }

    @Test
    fun suggestUsesTheQueryString() {
        assertEquals(
            "https://mediatheque.villejuif.fr/recherche/suggestajax?startsWith=asterix%20et",
            Endpoint.suggest("asterix et").url(base).toString()
        )
    }

    @Test
    fun loginIsAFormEncodedPost() {
        val request = PortalHttpClient().request(Endpoint.login(cardNumber = "0012345", password = "a b&c", csrfToken = "tok"))
        assertEquals("POST", request.method)
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertEquals("username=0012345&password=a+b%26c&login=Se+connecter&csrftoken=tok", body)
        assertEquals("https://mediatheque.villejuif.fr", request.header("Origin"))
    }

    @Test
    fun ajaxEndpointsSendTheRequestedWithHeader() {
        val request = PortalHttpClient().request(Endpoint.holdOptions(recordId = 909376))
        assertEquals("XMLHttpRequest", request.header("X-Requested-With"))
        assertEquals(PortalHttpClient.DEFAULT_USER_AGENT, request.header("User-Agent"))
    }

    @Test
    fun theUnauthorizedMessageIsRecognised() {
        assertEquals(BokehError.Unauthorized, BokehError.fromJson(Fixture.string("api-error.json")))
        assertEquals(BokehError.Api("refused", "Non"), BokehError.fromJson("""{"error":"refused","message":"Non"}"""))
        assertEquals(null, BokehError.fromJson("""{"total":2}"""))
        assertEquals(null, BokehError.fromJson("<html></html>"))
    }

    @Test
    fun patronSpaceRoutes() {
        assertEquals("/abonne/selections/id_profil/1/id/100001", Endpoint.patronPage("selections", "100001").url(base).encodedPath)
        assertEquals(
            "/recherche/simple/id_panier/501/format/json/page/2",
            Endpoint.selectionRecords(basketId = 501, page = 2).url(base).encodedPath
        )
        assertEquals(
            "/abonne/supprimer-de-la-selection/id_profil/1/id/100001/selection_id/9001/record_id/123456/delete/1",
            Endpoint.removeFromSelection("100001", selectionId = 9001, recordId = 123456).url(base).encodedPath
        )

        val review = Endpoint.submitReview(123456, "LEPETITPRINCE--SAINTEXUPERYA-", rating = 4, title = "Beau", text = "")
        assertEquals(Endpoint.Method.POST, review.method)
        assertEquals(listOf("note", "entete", "avis"), review.form.map { it.name })
        assertEquals("4", review.form.first().value)
    }

    @Test
    fun toggleLinksWriteTheLabelAsTheSiteDoes() {
        val add = Endpoint.toggleDefaultSelection("Mes préférés", "like", "dislike", recordId = 720_102, adding = true)
        val remove = Endpoint.toggleDefaultSelection("Mes préférés", "like", "dislike", recordId = 720_102, adding = false)
        assertEquals(
            "/abonne/ajouter-le-document-a-la-selection/selection_label/Mes+pr%C3%A9f%C3%A9r%C3%A9s/image/like/revert-image/dislike/record_id/720102",
            add.url(base).encodedPath
        )
        assertEquals(
            "/abonne/supprimer-de-la-selection/selection_label/Mes+pr%C3%A9f%C3%A9r%C3%A9s/image/like/revert-image/dislike/record_id/720102/ajax/true",
            remove.url(base).encodedPath
        )
        assertTrue(add.isAjax)
    }

    @Test
    fun pickupPathsBecomeSegments() {
        assertEquals(
            listOf("reservation-pickup-ajax", "id", "1"),
            Endpoint.pickupForm("/reservation-pickup-ajax/id/1").pathSegments
        )
    }
}
