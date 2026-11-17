// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque

import android.content.Intent
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
import fr.mediatheque.core.model.AppLink
import fr.mediatheque.navigation.MediathequeApp
import fr.mediatheque.navigation.TopLevelDestination
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The only activity: edge to edge, the splash screen, then the app in Compose. A FragmentActivity because the
 * biometric prompt needs one.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    /** Keyboard shortcuts, forwarded to the navigation. */
    private val shortcuts = MutableSharedFlow<TopLevelDestination>(extraBufferCapacity = 1)

    /** Screens opened from outside, kept until the navigation reads them (the first one arrives before it starts). */
    private val links = Channel<AppLink>(Channel.CONFLATED)

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        mainViewModel.restoreSession()
        // After a configuration change or process death the link was already followed.
        if (savedInstanceState == null) handleLink(intent)
        enableEdgeToEdge()
        setContent {
            MediathequeTheme {
                MediathequeApp(shortcuts, links.receiveAsFlow(), mainViewModel.session)
            }
        }
    }

    /** A notification, the widget or a shortcut while the app is open (singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent) {
        AppLink.fromKey(intent.getStringExtra(AppLink.EXTRA))?.let(links::trySend)
        intent.removeExtra(AppLink.EXTRA)
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
