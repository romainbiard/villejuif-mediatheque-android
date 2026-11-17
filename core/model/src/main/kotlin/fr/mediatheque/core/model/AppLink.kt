// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

/**
 * A screen the app opens from outside: a notification, the widget, an app shortcut or the Quick Settings tile.
 * Sent to the launcher activity as the [EXTRA] string extra, the enum's [key].
 */
enum class AppLink(val key: String) {
    CARD("card"),
    LOANS("loans"),
    HOLDS("holds"),
    NOTIFICATIONS("notifications"),
    SEARCH("search"),
    AGENDA("agenda");

    companion object {
        const val EXTRA = "fr.mediatheque.extra.LINK"

        fun fromKey(key: String?): AppLink? = entries.firstOrNull { it.key == key }
    }
}
