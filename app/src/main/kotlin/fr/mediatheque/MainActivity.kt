// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque

import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import fr.mediatheque.core.designsystem.theme.MediathequeTheme
import fr.mediatheque.navigation.MediathequeApp
import fr.mediatheque.navigation.TopLevelDestination
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The only activity: edge to edge, the splash screen, then the app in Compose. A FragmentActivity because the
 * biometric prompt needs one.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    /** Keyboard shortcuts, forwarded to the navigation. */
    private val shortcuts = MutableSharedFlow<TopLevelDestination>(extraBufferCapacity = 1)

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        mainViewModel.restoreSession()
        enableEdgeToEdge()
        setContent {
            MediathequeTheme {
                MediathequeApp(shortcuts)
            }
        }
    }

    /**
     * Ctrl+1…5 opens a destination, Ctrl+F the search. The activity receives them whatever has the focus,
     * which a Compose key handler would not.
     */
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val destination = when (keyCode) {
            KeyEvent.KEYCODE_F -> TopLevelDestination.SEARCH
            else -> TopLevelDestination.entries.firstOrNull { it.shortcutKey == keyCode }
        } ?: return super.onKeyShortcut(keyCode, event)
        return shortcuts.tryEmit(destination)
    }

    /** Lists the app's shortcuts in the system's keyboard shortcuts helper (Meta + /). */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        val shortcuts = TopLevelDestination.entries.map { destination ->
            KeyboardShortcutInfo(getString(destination.label), destination.shortcutKey, KeyEvent.META_CTRL_ON)
        } + KeyboardShortcutInfo(getString(R.string.shortcut_search), KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON)
        data += KeyboardShortcutGroup(getString(R.string.shortcuts_navigation), shortcuts)
    }
}
