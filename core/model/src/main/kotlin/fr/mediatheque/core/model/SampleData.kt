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

    val documents = listOf(
        Notice(
            id = 745828,
            title = "Harry Potter à l'école des sorciers",
            authors = listOf("Joanne Kathleen Rowling"),
            year = "2023",
            publisher = "Gallimard jeunesse",
            kind = DocumentKind.BOOK,
            coverUrl = null,
            series = "Harry Potter",
            volume = "1",
            permalink = null,
            harvestedCopies = listOf(HarvestedCopy(Library.ELSA_TRIOLET), HarvestedCopy(Library.PULP_SUD))
        ),
        Notice(
            id = 474169,
            title = "Sway",
            authors = listOf("Gérald Toto"),
            year = "2018",
            publisher = "Idol",
            kind = DocumentKind.CD,
            coverUrl = null,
            series = null,
            volume = null,
            permalink = null,
            harvestedCopies = listOf(HarvestedCopy(Library.ELSA_TRIOLET))
        ),
        Notice(
            id = 871798,
            title = "Terres de cinéma – Sur les traces de Harry Potter",
            authors = listOf("Jean Froment"),
            year = "2017",
            publisher = null,
            kind = DocumentKind("ArteVod", "Cinéma en ligne"),
            coverUrl = null,
            series = null,
            volume = null,
            permalink = null,
            harvestedCopies = emptyList()
        )
    )

    val copies = listOf(
        LiveCopy(
            library = Library.ELSA_TRIOLET,
            libraryName = "Médiathèque Elsa-Triolet",
            status = CopyStatus.Available,
            statusLabel = "Disponible",
            section = "Jeunesse",
            shelfMark = "SF ROW",
            holdCount = 0
        ),
        LiveCopy(
            library = Library.PULP_NORD,
            libraryName = "PULP Nord",
            status = CopyStatus.OnLoan(Paris.instant(2026, 11, 17)),
            statusLabel = "En prêt",
            section = "Jeunesse",
            shelfMark = "SF ROW",
            holdCount = 2
        )
    )

    val selections = listOf(
        Selection(1, basketId = 11, name = "Mes préférés", count = 3, updatedAt = Paris.instant(2026, 10, 30), coverUrls = emptyList()),
        Selection(2, basketId = null, name = "Déjà lu", count = 0, updatedAt = Paris.instant(2026, 10, 30), coverUrls = emptyList()),
        Selection(3, basketId = 13, name = "À lire", count = 1, updatedAt = Paris.instant(2026, 10, 12), coverUrls = emptyList()),
        Selection(4, basketId = 14, name = "Idées cadeaux", count = 2, updatedAt = Paris.instant(2026, 9, 2), coverUrls = emptyList())
    )

    val recordsToRate = listOf(
        RecordToRate(474169, "SWAY--TOTOG-", "Sway", "Gérald Toto", null),
        RecordToRate(909376, "ASTERIXETLEGRIFFON--FERRIJ-39", "Astérix et le griffon", "Jean-Yves Ferri", null)
    )

    val inbox = Inbox(
        listOf(
            InboxItem(
                id = "hold-100001_10",
                kind = InboxItem.Kind.HOLD_READY,
                date = Paris.instant(2026, 10, 30, 9),
                documentTitle = "Murmuration",
                place = "Elsa-Triolet",
                statusText = "Disponible jusqu'au 06/11/2026"
            ),
            InboxItem(
                id = "late-100001_1",
                kind = InboxItem.Kind.LOAN_LATE,
                date = Paris.instant(2026, 10, 29),
                loanId = "100001_1",
                documentTitle = "Le Petit Prince",
                dueDate = Paris.instant(2026, 10, 28),
                place = "Elsa-Triolet"
            ),
            InboxItem(
                id = "message-1",
                kind = InboxItem.Kind.LIBRARY_MESSAGE,
                date = Paris.instant(2026, 10, 26),
                isRead = true,
                message = "Fermeture exceptionnelle de PULP Sud le 11 novembre."
            )
        )
    )

    val events = listOf(
        Event(
            id = 990,
            title = "Ronde des histoires",
            imageUrl = null,
            place = "Pulp Nord",
            library = Library.PULP_NORD,
            start = Paris.instant(2026, 10, 31, 10, 30),
            end = Paris.instant(2026, 10, 31, 11),
            isAllDay = false,
            type = "Lectures",
            audience = "Dès 4 ans",
            dateDescription = "Samedi 31 octobre 2026 de 10:30 à 11:00"
        ),
        Event(
            id = 1019,
            title = "Conférence : IA - Comprendre les outils d’aujourd’hui",
            imageUrl = null,
            place = "Médiathèque Elsa-Triolet",
            library = Library.ELSA_TRIOLET,
            start = Paris.instant(2026, 11, 4, 16),
            end = Paris.instant(2026, 11, 4, 17),
            isAllDay = false,
            type = "Rencontre",
            audience = "Dès 13 ans",
            dateDescription = "Mercredi 4 novembre 2026 de 16:00 à 17:00"
        ),
        Event(
            id = 983,
            title = "Prix des Lecteurs et Lectrices de Villejuif - Cap sur l’imaginaire !",
            imageUrl = null,
            place = null,
            library = null,
            start = Paris.instant(2026, 9, 26),
            end = Paris.instant(2027, 3, 27),
            isAllDay = true,
            type = null,
            audience = null,
            dateDescription = "Du samedi 26 septembre 2026 au samedi 27 mars 2027"
        )
    )

    private val elsaHours =
        listOf("Mardi : 13h - 19h", "Mercredi : 10h - 19h", "Jeudi : 13h - 19h", "Vendredi : 13h - 19h", "Samedi : 10h - 18h")
    private val pulpHours = listOf("Mardi : 15h - 19h", "Mercredi : 13h - 19h", "Vendredi : 15h - 19h", "Samedi : 10h - 18h")

    /** The libraries' public details, as on the website. */
    val libraries = listOf(
        LibraryInfo(
            library = Library.ELSA_TRIOLET,
            slug = "elsa_triolet",
            name = "Médiathèque Elsa-Triolet",
            addressLines = listOf("1 esplanade Pierre-Yves-Cosnier", "94800 Villejuif"),
            phone = "01 86 93 33 00",
            contactUrl = null,
            hours = OpeningHours.parse(elsaHours),
            hourLines = elsaHours
        ),
        LibraryInfo(
            library = Library.PULP_NORD,
            slug = "pulp_nord",
            name = "PULP Nord",
            addressLines = listOf("61 rue Pasteur", "94800 Villejuif"),
            phone = "01 86 93 33 15",
            contactUrl = null,
            hours = OpeningHours.parse(pulpHours),
            hourLines = pulpHours
        ),
        LibraryInfo(
            library = Library.PULP_SUD,
            slug = "pulp_sud",
            name = "PULP Sud",
            addressLines = listOf("118 rue Youri Gagarine", "94800 Villejuif"),
            phone = "01 86 93 33 16",
            contactUrl = null,
            hours = OpeningHours.parse(pulpHours),
            hourLines = pulpHours
        )
    )
}
