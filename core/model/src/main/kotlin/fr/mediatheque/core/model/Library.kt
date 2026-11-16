// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

/**
 * The network's three libraries, in list order (the same as the iOS app). [code] serves as `code_annexe` (pickup location)
 * and as the `Y{code}` search facet.
 */
enum class Library(
    val code: String,
    /** Full name, as the site writes it. */
    val displayName: String,
    val shortName: String,
    /** The library's page (`/elsa_triolet`). */
    val slug: String,
    /**
     * Bundled coordinates: the site does not publish them. Geocoded from each library's address
     * (Base Adresse Nationale): 1 esplanade Pierre-Yves-Cosnier, 61 rue Pasteur, 118 rue Youri-Gagarine.
     */
    val latitude: Double,
    val longitude: Double
) {
    ELSA_TRIOLET("1", "Médiathèque Elsa-Triolet", "Elsa-Triolet", "elsa_triolet", 48.7922, 2.3630),
    PULP_SUD("2", "PULP Sud", "PULP Sud", "pulp_sud", 48.7822, 2.3570),
    PULP_NORD("3", "PULP Nord", "PULP Nord", "pulp_nord", 48.8049, 2.3663)
    ;

    val searchFacet: String get() = "Y$code"

    companion object {
        fun fromCode(code: String): Library? = entries.firstOrNull { it.code == code }

        fun fromSlug(slug: String): Library? = entries.firstOrNull { it.slug == slug }

        /** Finds a library from a label on the site ("Médiathèque Elsa-Triolet", "Pulp Nord", "PULP Sud"…). */
        fun fromLabel(label: String): Library? {
            val text = label.folded()
            return when {
                "elsa" in text -> ELSA_TRIOLET
                "pulp nord" in text -> PULP_NORD
                "pulp sud" in text -> PULP_SUD
                else -> null
            }
        }
    }
}
