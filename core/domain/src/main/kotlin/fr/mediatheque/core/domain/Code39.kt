// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.domain

/**
 * Code 39 ("3 of 9"), the symbology printed on the libraries' cards
 * (checked on a card on 10/31/2026: 7 digits, no check character).
 */
object Code39 {
    /** One barcode module: a bar or a space, narrow or wide. */
    data class Element(val isBar: Boolean, val isWide: Boolean)

    /** The 9 elements of each character, bar first: `n` narrow, `w` wide. */
    internal val patterns: Map<Char, String> = mapOf(
        '0' to "nnnwwnwnn", '1' to "wnnwnnnnw", '2' to "nnwwnnnnw", '3' to "wnwwnnnnn",
        '4' to "nnnwwnnnw", '5' to "wnnwwnnnn", '6' to "nnwwwnnnn", '7' to "nnnwnnwnw",
        '8' to "wnnwnnwnn", '9' to "nnwwnnwnn",
        'A' to "wnnnnwnnw", 'B' to "nnwnnwnnw", 'C' to "wnwnnwnnn", 'D' to "nnnnwwnnw",
        'E' to "wnnnwwnnn", 'F' to "nnwnwwnnn", 'G' to "nnnnnwwnw", 'H' to "wnnnnwwnn",
        'I' to "nnwnnwwnn", 'J' to "nnnnwwwnn", 'K' to "wnnnnnnww", 'L' to "nnwnnnnww",
        'M' to "wnwnnnnwn", 'N' to "nnnnwnnww", 'O' to "wnnnwnnwn", 'P' to "nnwnwnnwn",
        'Q' to "nnnnnnwww", 'R' to "wnnnnnwwn", 'S' to "nnwnnnwwn", 'T' to "nnnnwnwwn",
        'U' to "wwnnnnnnw", 'V' to "nwwnnnnnw", 'W' to "wwwnnnnnn", 'X' to "nwnnwnnnw",
        'Y' to "wwnnwnnnn", 'Z' to "nwwnwnnnn",
        '-' to "nwnnnnwnw", '.' to "wwnnnnwnn", ' ' to "nwwnnnwnn", '$' to "nwnwnwnnn",
        '/' to "nwnwnnnwn", '+' to "nwnnnwnwn", '%' to "nnnwnwnwn", '*' to "nwnnwnwnn"
    )

    /**
     * Elements of [value] framed by the start and stop `*`, with a narrow space between characters.
     * `null` if a character does not exist in Code 39.
     */
    fun elements(value: String): List<Element>? {
        val characters = "*${value.uppercase()}*"
        val elements = mutableListOf<Element>()
        for ((index, character) in characters.withIndex()) {
            val pattern = patterns[character] ?: return null
            if (character == '*' && index != 0 && index != characters.lastIndex) return null
            pattern.forEachIndexed { position, width -> elements += Element(isBar = position % 2 == 0, isWide = width == 'w') }
            if (index < characters.lastIndex) elements += Element(isBar = false, isWide = false)
        }
        return elements
    }

    /** Total width in narrow modules, for a given wide-to-narrow ratio. */
    fun width(elements: List<Element>, wideRatio: Double): Double = elements.sumOf { if (it.isWide) wideRatio else 1.0 }
}
