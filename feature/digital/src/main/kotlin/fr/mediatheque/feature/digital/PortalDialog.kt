// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import fr.mediatheque.core.designsystem.R as DesignR

/** The provider's sign-in, full screen. Closing it destroys the WebView and its profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PortalDialog(session: PortalSession, onClose: () -> Unit) {
    var progress by remember { mutableIntStateOf(0) }
    var webCanGoBack by remember { mutableStateOf(false) }
    var handedOff by remember { mutableStateOf(false) }
    var downloads by remember { mutableIntStateOf(0) }
    var controller by remember { mutableStateOf<PortalWebViewController?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val listener = remember {
        object : PortalWebViewListener {
            override fun onProgress(percent: Int) {
                progress = percent
            }

            override fun onNavigationChanged(canGoBack: Boolean) {
                webCanGoBack = canGoBack
            }

            override fun onHandedOffToApp() {
                handedOff = true
            }

            override fun onDownloadStarted() {
                downloads++
            }

            override fun onCrashed() = onClose()
        }
    }
    val downloadText = stringResource(R.string.portal_download_started)
    LaunchedEffect(downloads) { if (downloads > 0) snackbar.showSnackbar(downloadText) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(enabled = webCanGoBack && !handedOff) { controller?.webView?.goBack() }
        // Edge to edge in the dialog's own window: its status bar icons follow the theme, as the activity's do.
        val view = LocalView.current
        val lightBars = !isSystemInDarkTheme()
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(session.offer.provider)) },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.close))
                            }
                        }
                    )
                    if (progress < DONE) {
                        LinearProgressIndicator(progress = { progress / DONE.toFloat() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AndroidView(
                    factory = { context ->
                        PortalWebViewController.create(context, listener).also {
                            controller = it
                            it.load(session)
                        }.webView
                    },
                    onRelease = { controller?.destroy() },
                    modifier = Modifier.fillMaxSize()
                )
                if (handedOff) HandedOffPanel(session, onClose, Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun HandedOffPanel(session: PortalSession, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val appName = session.offer.app?.name ?: stringResource(R.string.portal_the_app)
    Surface(
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier.padding(24.dp).widthIn(max = 400.dp)
    ) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                painterResource(DesignR.drawable.ic_open_in_new),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                stringResource(R.string.portal_handed_off, appName),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Button(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}

private const val DONE = 100
