// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import fr.mediatheque.MainActivity
import fr.mediatheque.core.model.AppLink

/** The "Ma carte" Quick Settings tile: the library card full screen, for the scanner at the desk. */
class CardTileService : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated") // The Intent variant is the only one before Android 14.
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(AppLink.EXTRA, AppLink.CARD.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
