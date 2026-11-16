// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/** Bokeh document type (`type.code`), which distinguishes physical from digital. */
@Serializable
data class DocumentKind(val code: String, val label: String) {
    val isDigital: Boolean get() = code in DIGITAL_CODES
    val searchFacet: String get() = "T$code"

    companion object {
        /** Codes of resources that can be consulted online (harvested by Bokeh or Eurêka). */
        val DIGITAL_CODES: Set<String> = setOf("ArteVod", "Cafeyn", "Numilog", "ToutApprendre", "23", "24", "26", "27")

        val BOOK = DocumentKind("1", "Livre")
        val CD = DocumentKind("3", "CD")
        val DVD = DocumentKind("4", "DVD")
        val AUDIOBOOK = DocumentKind("17", "Livre audio")
    }
}

/** A catalog record. */
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
     * Copies as harvested overnight: useful for the call number and the library,
     * not for availability (see the real-time availability).
     */
    val harvestedCopies: List<HarvestedCopy>
) {
    /** Libraries that own a copy, without duplicates, in network order. */
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

/** Real-time availability of a copy. */
sealed interface CopyStatus {
    val isAvailable: Boolean

    data object Available : CopyStatus {
        override val isAvailable = true
    }

    data class OnLoan(val dueDate: Instant?) : CopyStatus {
        override val isAvailable = false
    }

    /** "En réserve", "À demander à l'accueil". */
    data object OnRequest : CopyStatus {
        override val isAvailable = true
    }

    data class Other(val label: String) : CopyStatus {
        override val isAvailable = false
    }
}

/** A copy seen in real time (`noticeajax/exemplaires`). */
data class LiveCopy(
    val library: Library?,
    val libraryName: String,
    val status: CopyStatus,
    val statusLabel: String,
    val section: String?,
    val shelfMark: String?,
    val holdCount: Int
)

/** Availability badge of a record in a results list. */
@Serializable
data class Availability(val label: String, val isAvailable: Boolean)
