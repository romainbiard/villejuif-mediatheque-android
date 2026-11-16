// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/** Type de document Bokeh (`type.code`), qui distingue le physique du numérique. */
@Serializable
data class DocumentKind(val code: String, val label: String) {
    val isDigital: Boolean get() = code in DIGITAL_CODES
    val searchFacet: String get() = "T$code"

    companion object {
        /** Codes des ressources consultables en ligne (moissonnées par Bokeh ou Eurêka). */
        val DIGITAL_CODES: Set<String> = setOf("ArteVod", "Cafeyn", "Numilog", "ToutApprendre", "23", "24", "26", "27")

        val BOOK = DocumentKind("1", "Livre")
        val CD = DocumentKind("3", "CD")
        val DVD = DocumentKind("4", "DVD")
        val AUDIOBOOK = DocumentKind("17", "Livre audio")
    }
}

/** Une notice du catalogue. */
@Serializable
data class Notice(
    val id: Int,
    val title: String,
    val authors: List<String>,
    val year: String?,
    val publisher: String?,
    val kind: DocumentKind,
    val coverUrl: String?,
    val series: String?,
    val volume: String?,
    val permalink: String?,
    /**
     * Exemplaires tels que moissonnés la nuit : utiles pour la cote et la médiathèque,
     * pas pour la disponibilité (voir la disponibilité en temps réel).
     */
    val harvestedCopies: List<HarvestedCopy>
) {
    /** Médiathèques qui possèdent un exemplaire, sans doublon, dans l'ordre du réseau. */
    val libraries: List<Library>
        get() {
            val owned = harvestedCopies.mapNotNull { it.library }.toSet()
            return Library.entries.filter { it in owned }
        }
}

@Serializable
data class HarvestedCopy(val library: Library?)

@Serializable
data class SearchPage(val total: Int, val documents: List<Notice>)

data class SearchQuery(
    val text: String,
    val kinds: List<DocumentKind> = emptyList(),
    val libraries: List<Library> = emptyList(),
    val sort: Sort = Sort.RELEVANCE,
    val page: Int = 1
) {
    enum class Sort(val value: String) {
        RELEVANCE("*"),
        NEWEST("date_creation desc")
    }

    val facets: List<String> get() = kinds.map { it.searchFacet } + libraries.map { it.searchFacet }
}

/** Disponibilité d'un exemplaire en temps réel. */
sealed interface CopyStatus {
    val isAvailable: Boolean

    data object Available : CopyStatus {
        override val isAvailable = true
    }

    data class OnLoan(val dueDate: Instant?) : CopyStatus {
        override val isAvailable = false
    }

    /** « En réserve », « À demander à l'accueil ». */
    data object OnRequest : CopyStatus {
        override val isAvailable = true
    }

    data class Other(val label: String) : CopyStatus {
        override val isAvailable = false
    }
}

/** Un exemplaire vu en temps réel (`noticeajax/exemplaires`). */
data class LiveCopy(
    val library: Library?,
    val libraryName: String,
    val status: CopyStatus,
    val statusLabel: String,
    val section: String?,
    val shelfMark: String?,
    val holdCount: Int
)

/** Badge de disponibilité d'une notice dans une liste de résultats. */
@Serializable
data class Availability(val label: String, val isAvailable: Boolean)
