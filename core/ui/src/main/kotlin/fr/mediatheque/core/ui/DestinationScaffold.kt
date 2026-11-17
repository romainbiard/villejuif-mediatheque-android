// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.mediatheque.core.designsystem.component.EmptyState

/**
 * A top-level destination: the small top app bar, its title at the top from the start, over the content. The bar
 * stays pinned and takes on its scrolled color once the content runs under it.
 * The window insets of the navigation bar are left to the app's navigation suite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = navigationIcon,
                actions = actions,
                scrollBehavior = scrollBehavior
            )
        },
        content = content
    )
}

/** A destination whose screens come in a later version: its title, and a word about it. */
@Composable
fun ComingSoonDestination(title: String, @DrawableRes icon: Int, modifier: Modifier = Modifier) {
    DestinationScaffold(title, modifier) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            EmptyState(
                icon = icon,
                title = stringResource(R.string.coming_soon_title),
                message = stringResource(R.string.coming_soon_message),
                modifier = Modifier.widthIn(max = 560.dp).padding(16.dp)
            )
        }
    }
}

/**
 * The detail pane of a wide window before anything is chosen in the list beside it: a word on what goes there rather
 * than an empty half of the screen.
 */
@Composable
fun DetailPlaceholder(@DrawableRes icon: Int, title: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        EmptyState(icon = icon, title = title, message = message, modifier = Modifier.widthIn(max = 480.dp))
    }
}
