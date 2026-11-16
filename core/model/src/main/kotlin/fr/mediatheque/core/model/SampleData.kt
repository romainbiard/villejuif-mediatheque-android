// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

/**
 * Fictitious data for previews, the demo mode, and tests: the demo patron "Camille MARTIN", card 0012345.
 * Never real patron data (docs/PLAN-PORTAGE.md § 7).
 */
object SampleData {
    val patron = Patron(
        cardNumber = "0012345",
        firstName = "Camille",
        lastName = "MARTIN",
        email = "camille.martin@exemple.fr",
        phone = null,
        homeLibrary = Library.PULP_NORD,
        cardExpiry = Paris.instant(2027, 3, 14),
        cardExpired = false,
        cardAboutToExpire = false,
        cardExpiresInDays = 135,
        permissions = Patron.Permissions(holdForbidden = false, digitalResourcesForbidden = false),
        counters = Patron.Counters(loans = 4, lateLoans = 1, waitingToBePulled = 1),
        message = null,
        hasUnreadMessage = false
    )

    val loans = listOf(
        Loan(
            id = "100001_1",
            title = "Le Petit Prince",
            author = "Antoine de Saint-Exupéry",
            dueDate = Paris.instant(2026, 10, 28),
            library = Library.ELSA_TRIOLET,
            libraryName = "Médiathèque Elsa-Triolet",
            recordId = null,
            coverUrl = null
        ),
        Loan(
            id = "100001_2",
            title = "Sway",
            author = "Gérald Toto",
            dueDate = Paris.instant(2026, 11, 5),
            library = Library.ELSA_TRIOLET,
            libraryName = "Médiathèque Elsa-Triolet",
            recordId = 474169,
            coverUrl = null
        ),
        Loan(
            id = "100001_3",
            title = "Chien pourri",
            author = "Colas Gutman",
            dueDate = Paris.instant(2026, 11, 19),
            library = Library.PULP_NORD,
            libraryName = "PULP Nord",
            recordId = null,
            coverUrl = null
        )
    )

    val holds = listOf(
        Hold(
            id = "100001_10",
            title = "Murmuration",
            statusText = "Disponible jusqu'au 06/11/2026",
            pickupLibrary = Library.ELSA_TRIOLET,
            pickupLibraryName = "Médiathèque Elsa-Triolet",
            recordId = 857057,
            coverUrl = null
        ),
        Hold(
            id = "100001_11",
            title = "Astérix n° 39 · Astérix et le griffon",
            statusText = "Pas disponible avant le 14/11/2026",
            pickupLibrary = Library.PULP_NORD,
            pickupLibraryName = "PULP Nord",
            recordId = 909376,
            coverUrl = null,
            rank = 1
        )
    )
}
