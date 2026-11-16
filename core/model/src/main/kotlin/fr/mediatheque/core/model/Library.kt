// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

/**
 * Les trois médiathèques du réseau, dans l'ordre des listes (celui de l'app iOS). [code] sert de `code_annexe` (lieu de retrait)
 * et de facette de recherche `Y{code}`.
 */
enum class Library(
    val code: String,
    /** Nom complet, tel que le site l'écrit. */
    val displayName: String,
    val shortName: String,
    /** Page de la médiathèque (`/elsa_triolet`). */
    val slug: String,
    /**
     * Coordonnées embarquées : le site ne les publie pas. Géocodées depuis l'adresse de chaque médiathèque
     * (Base Adresse Nationale) : 1 esplanade Pierre-Yves-Cosnier, 61 rue Pasteur, 118 rue Youri-Gagarine.
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

        /** Retrouve une médiathèque depuis un libellé du site (« Médiathèque Elsa-Triolet », « Pulp Nord », « PULP Sud »…). */
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
