// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.bokeh.BokehError
import fr.mediatheque.core.bokeh.Endpoint
import fr.mediatheque.core.bokeh.PortalHttpClient
import fr.mediatheque.core.bokeh.dto.AccountResponseDto
import fr.mediatheque.core.bokeh.dto.HoldsResponseDto
import fr.mediatheque.core.bokeh.dto.LoansResponseDto
import fr.mediatheque.core.bokeh.dto.PopupDto
import fr.mediatheque.core.bokeh.parser.HtmlParsers
import fr.mediatheque.core.model.Hold
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.model.Patron
import fr.mediatheque.core.model.RenewalResult
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A hold to place: the pickup form to submit and the libraries it offers. */
data class HoldRequest(val pickupPath: String, val pickupLibraries: List<Library>, val defaultPickup: Library?)

/** The patron's account. Every call goes through [PortalSession.perform], which signs in again if needed. */
interface AccountRepository {
    suspend fun patron(): Patron

    suspend fun loans(): List<Loan>

    suspend fun holds(): List<Hold>

    /** The site does not say whether a loan can be renewed: we try, then compare the due dates. */
    suspend fun renew(loanIds: List<String>): List<RenewalResult>

    suspend fun cancelHold(holdId: String)

    suspend fun prepareHold(recordId: Int): HoldRequest

    /** Returns the site's confirmation message. */
    suspend fun placeHold(request: HoldRequest, pickup: Library): String
}

class PortalAccountRepository @Inject constructor(private val http: PortalHttpClient, private val session: PortalSession) :
    AccountRepository {
    override suspend fun patron(): Patron = session.perform {
        http.decode(Endpoint.ACCOUNT, AccountResponseDto::parse).account.toPatron()
    }

    override suspend fun loans(): List<Loan> = session.perform {
        http.decode(Endpoint.LOANS, LoansResponseDto::parse).loans.mapNotNull { it.toLoan() }
    }

    override suspend fun holds(): List<Hold> = session.perform {
        val dtos = http.decode(Endpoint.HOLDS, HoldsResponseDto::parse).holds
        val patronId = dtos.firstOrNull()?.id?.substringBefore('_') ?: return@perform emptyList()
        // The rank only exists in the HTML; the holds still show without it.
        val ranks = try {
            HtmlParsers.holdRanks(http.string(Endpoint.holdsPage(patronId)))
        } catch (error: BokehError.Unauthorized) {
            throw error
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
        dtos.mapIndexed { index, dto -> dto.toHold(rank = ranks.getOrNull(index)) }
    }

    override suspend fun renew(loanIds: List<String>): List<RenewalResult> {
        val patronId = loanIds.firstOrNull()?.substringBefore('_') ?: return emptyList()
        val before = loans().associate { it.id to it.dueDate }
        session.perform { http.bytes(Endpoint.renewLoans(patronId, loanIds)) }
        val after = loans().associate { it.id to it.dueDate }
        return loanIds.map { id ->
            val newDue = after[id]
            val oldDue = before[id]
            if (newDue != null && oldDue != null && newDue > oldDue) {
                RenewalResult.Renewed(id, newDue)
            } else {
                RenewalResult.Refused(id)
            }
        }
    }

    override suspend fun cancelHold(holdId: String) {
        session.perform { http.bytes(Endpoint.cancelHold(holdId)) }
    }

    override suspend fun prepareHold(recordId: Int): HoldRequest = session.perform {
        val options = http.decode(Endpoint.holdOptions(recordId), PopupDto::parse)
        val paths = HtmlParsers.holdPickupPaths(options.content)
        // The "on_record" link holds the record itself (the first copy to come back).
        val path = paths.firstOrNull { "on_record" in it } ?: paths.firstOrNull() ?: throw HoldError.NotHoldable
        val form = http.decode(Endpoint.pickupForm(path), PopupDto::parse)
        val pickups = HtmlParsers.pickupOptions(form.content)
        HoldRequest(
            pickupPath = path,
            pickupLibraries = pickups.mapNotNull { Library.fromCode(it.code) },
            defaultPickup = pickups.firstOrNull { it.isSelected }?.let { Library.fromCode(it.code) }
        )
    }

    override suspend fun placeHold(request: HoldRequest, pickup: Library): String = session.perform {
        val popup = http.decode(Endpoint.submitPickup(request.pickupPath, pickup.code), PopupDto::parse)
        val message = popup.content.replace("<br>", "\n").replace(Regex("<[^>]+>"), "")
        // The site confirms with "Votre réservation est enregistrée."
        if (!message.contains("enregistrée", ignoreCase = true)) throw HoldError.Refused(message)
        message
    }

    /** Loans and holds side by side: each list is one or two requests to the portal. */
    suspend fun loansAndHolds(): Pair<List<Loan>, List<Hold>> = coroutineScope {
        val loans = async { loans() }
        val holds = async { holds() }
        loans.await() to holds.await()
    }
}
