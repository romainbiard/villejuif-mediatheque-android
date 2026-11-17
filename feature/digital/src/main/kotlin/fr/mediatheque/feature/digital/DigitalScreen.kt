// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.ui.DestinationScaffold
import fr.mediatheque.core.ui.ExternalActions

/** The services' secondary actions, behind a card's "more" button. */
internal class OfferActions(
    val primary: (DigitalOffer) -> Unit,
    val connect: (DigitalOffer) -> Unit,
    val web: (DigitalOffer) -> Unit,
    val howItWorks: (DigitalOffer) -> Unit,
    val tutorial: (DigitalOffer) -> Unit
)

@Composable
internal fun DigitalScreen(viewModel: DigitalViewModel, onOpenAccount: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var helpOfferId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    // Back from the Play Store or the settings: apps may have come or gone.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshInstalledApps() }

    val installedText = state.justInstalled?.app?.let { stringResource(R.string.installed_message, it.name) }
    LaunchedEffect(installedText) {
        if (installedText != null) {
            snackbar.showSnackbar(installedText)
            viewModel.installedMessageShown()
        }
    }

    val actions = OfferActions(
        primary = viewModel::primaryAction,
        connect = viewModel::connectThroughPortal,
        web = viewModel::continueOnWeb,
        howItWorks = { helpOfferId = it.id },
        tutorial = { ExternalActions.openWebPage(context, it.tutorialUrl) }
    )

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, modifier = modifier) { outer ->
        DigitalContent(
            offers = viewModel.offers,
            state = state,
            session = session,
            actions = actions,
            onOpenAccount = onOpenAccount,
            modifier = Modifier.padding(outer)
        )
    }

    helpOfferId?.let(DigitalCatalog::offer)?.let { offer ->
        HowItWorksSheet(
            offer = offer,
            state = state,
            onPrimary = {
                helpOfferId = null
                viewModel.primaryAction(offer)
            },
            onInstall = {
                helpOfferId = null
                viewModel.getApp(offer)
            },
            onTutorial = { ExternalActions.openWebPage(context, offer.tutorialUrl) },
            onDismiss = { helpOfferId = null }
        )
    }
    state.installOffer?.let { offer ->
        InstallAppSheet(
            offer = offer,
            onGet = { viewModel.getApp(offer) },
            onContinueOnWeb = { viewModel.continueOnWeb(offer) },
            onDismiss = viewModel::dismissInstallSheet
        )
    }
    state.portal?.let { portal -> PortalDialog(portal, onClose = viewModel::portalClosed) }
    state.message?.let { message ->
        MessageDialog(
            message = message,
            onOpenAccount = {
                viewModel.messageShown()
                onOpenAccount()
            },
            onDismiss = viewModel::messageShown
        )
    }
}

@Composable
private fun DigitalContent(
    offers: List<DigitalOffer>,
    state: DigitalUiState,
    session: SessionStore.State,
    actions: OfferActions,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Cards widen with the text size, so a large text size gets fewer, wider columns.
    val minCardWidth = 168.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    DestinationScaffold(title = stringResource(R.string.digital_title), modifier = modifier) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // As many columns as fit, but no more than there are cards: no empty slot at the end of a wide row.
            val columns = ((maxWidth - 32.dp + CARD_GAP) / (minCardWidth + CARD_GAP)).toInt()
                .coerceAtMost(offers.size - 1)
                .coerceAtLeast(1)
            DigitalList(offers, columns, state, session, actions, onOpenAccount)
        }
    }
}

private val CARD_GAP = 12.dp

@Composable
private fun DigitalList(
    offers: List<DigitalOffer>,
    columns: Int,
    state: DigitalUiState,
    session: SessionStore.State,
    actions: OfferActions,
    onOpenAccount: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.digital_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val patron = (session as? SessionStore.State.LoggedIn)?.patron
        when {
            session is SessionStore.State.LoggedOut || session is SessionStore.State.Locked -> item(span = {
                GridItemSpan(maxLineSpan)
            }) {
                SignInBanner(onOpenAccount)
            }

            patron?.permissions?.digitalResourcesForbidden == true -> item(span = { GridItemSpan(maxLineSpan) }) {
                SuspendedBanner()
            }

            else -> Unit
        }
        offers.firstOrNull()?.let { featured ->
            item(key = featured.id, span = { GridItemSpan(maxLineSpan) }) {
                FeaturedOfferCard(featured, state, actions)
            }
        }
        // A row of cards at a time, all as tall as the tallest, their buttons aligned at the bottom.
        offers.drop(1).chunked(columns).forEach { row ->
            item(key = row.first().id, span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                    row.forEach { offer -> OfferTile(offer, state, actions, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SignInBanner(onOpenAccount: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(painterResource(DesignR.drawable.ic_account_circle), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.digital_sign_in_banner),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            )
            TextButton(onClick = onOpenAccount) { Text(stringResource(R.string.digital_sign_in)) }
        }
    }
}

@Composable
private fun SuspendedBanner(modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(DesignR.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                stringResource(R.string.digital_suspended),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** The main button's label: install, open the app, or the web verb. */
@Composable
internal fun primaryLabel(offer: DigitalOffer, state: DigitalUiState, compact: Boolean): String {
    val app = offer.app
    return when {
        state.needsInstall(offer) -> stringResource(if (compact) R.string.digital_install else R.string.digital_install_app)
        !compact && app != null && state.access(offer) == ServiceAccess.AppInstalled -> stringResource(R.string.digital_open_app, app.name)
        else -> stringResource(offer.webActionLabel)
    }
}

@Composable
private fun AccessLine(access: ServiceAccess, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val text = when (access) {
        ServiceAccess.AppInstalled -> stringResource(R.string.access_app_installed)
        ServiceAccess.InApp -> stringResource(R.string.access_in_app)
        is ServiceAccess.Reader -> stringResource(R.string.access_reader, access.appName)
        ServiceAccess.Web -> stringResource(R.string.access_web)
    }
    val color = when {
        onDark -> MediathequeColors.extended.cyan
        access == ServiceAccess.AppInstalled -> MediathequeColors.extended.success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (access == ServiceAccess.AppInstalled) {
            Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun ServiceMenu(
    offer: DigitalOffer,
    state: DigitalUiState,
    actions: OfferActions,
    modifier: Modifier = Modifier,
    onDark: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(DesignR.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.menu_more, stringResource(offer.category)),
                tint = if (onDark) Color.White else MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            fun pick(action: (DigitalOffer) -> Unit) {
                expanded = false
                action(offer)
            }
            if (state.canConnectApp(offer)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_connect)) },
                    leadingIcon = { Icon(painterResource(DesignR.drawable.ic_key), contentDescription = null) },
                    onClick = { pick(actions.connect) }
                )
            }
            // For a required app the web is the alternative; elsewhere it is already the main button.
            if (offer.app?.role == PartnerApp.Role.REQUIRED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_web)) },
                    leadingIcon = { Icon(painterResource(DesignR.drawable.ic_public), contentDescription = null) },
                    onClick = { pick(actions.web) }
                )
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_how)) },
                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_help), contentDescription = null) },
                onClick = { pick(actions.howItWorks) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_tutorial)) },
                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_description), contentDescription = null) },
                onClick = { pick(actions.tutorial) }
            )
        }
    }
}

/** The colors of a service's badge, from the City's palette. */
@Composable
internal fun offerColors(seed: Int): Pair<Color, Color> {
    val extended = MediathequeColors.extended
    return when (seed % OFFER_PALETTE_SIZE) {
        0 -> extended.magenta to Color.White
        1 -> extended.cyan to INK
        2 -> extended.yellow to INK
        3 -> extended.brandSurface to extended.onBrandSurface
        4 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> INK to Color.White
    }
}

@Composable
internal fun OfferBadge(offer: DigitalOffer, modifier: Modifier = Modifier) {
    val (background, foreground) = offerColors(offer.colorSeed)
    Box(
        modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(offer.icon), contentDescription = null, tint = foreground)
    }
}

@Composable
private fun FeaturedOfferCard(offer: DigitalOffer, state: DigitalUiState, actions: OfferActions, modifier: Modifier = Modifier) {
    val access = state.access(offer)
    val magenta = MediathequeColors.extended.magenta
    val violet = MediathequeColors.extended.brandSurface
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 230.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(INK)
    ) {
        // Decoration pinned to the top-end corner, behind the text.
        Canvas(Modifier.matchParentSize()) {
            drawCircle(violet, radius = 130.dp.toPx(), center = Offset(size.width + 20.dp.toPx(), 0f))
            drawCircle(magenta, radius = 52.dp.toPx(), center = Offset(size.width - 72.dp.toPx(), 72.dp.toPx()))
        }
        Icon(
            painterResource(DesignR.drawable.ic_play_circle_filled),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 52.dp).size(40.dp)
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.padding(end = 116.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(offer.category).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    stringResource(offer.provider),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.semantics { heading() }
                )
                Text(stringResource(offer.quota), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                if (access == ServiceAccess.AppInstalled) AccessLine(access, onDark = true)
            }
            Spacer(Modifier.size(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { actions.primary(offer) },
                    enabled = state.resolving == null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = INK),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) {
                    ButtonContent(primaryLabel(offer, state, compact = false), state.needsInstall(offer), state.resolving == offer.id)
                }
                ServiceMenu(offer, state, actions, Modifier.clip(CircleShape).background(violet), onDark = true)
            }
        }
    }
}

@Composable
private fun OfferTile(offer: DigitalOffer, state: DigitalUiState, actions: OfferActions, modifier: Modifier = Modifier) {
    val access = state.access(offer)
    Surface(shape = RoundedCornerShape(24.dp), color = MediathequeColors.extended.card, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                OfferBadge(offer)
                Spacer(Modifier.weight(1f))
                ServiceMenu(offer, state, actions)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(offer.category),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics {
                        heading()
                    }
                )
                Text(
                    "${stringResource(offer.provider)} · ${stringResource(offer.quota)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AccessLine(access, Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            val colors = when {
                access == ServiceAccess.AppInstalled -> ButtonDefaults.buttonColors()

                state.needsInstall(offer) -> ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                else -> ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
            Button(
                onClick = { actions.primary(offer) },
                enabled = state.resolving == null,
                colors = colors,
                modifier = Modifier.fillMaxWidth()
            ) {
                ButtonContent(primaryLabel(offer, state, compact = true), state.needsInstall(offer), state.resolving == offer.id)
            }
        }
    }
}

@Composable
private fun ButtonContent(label: String, needsInstall: Boolean, resolving: Boolean) {
    if (resolving) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    } else {
        if (needsInstall) {
            Icon(painterResource(DesignR.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(label)
    }
}

@Composable
private fun MessageDialog(message: DigitalMessage, onOpenAccount: () -> Unit, onDismiss: () -> Unit) {
    val text = when (message) {
        DigitalMessage.NotSignedIn -> stringResource(R.string.error_not_signed_in)
        DigitalMessage.Suspended -> stringResource(R.string.digital_suspended)
        DigitalMessage.Offline -> stringResource(R.string.error_offline)
        is DigitalMessage.Failed -> stringResource(R.string.error_failed, stringResource(message.offer.provider))
        DigitalMessage.StoreUnavailable -> stringResource(R.string.error_store)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.error_title)) },
        text = { Text(text) },
        confirmButton = {
            if (message == DigitalMessage.NotSignedIn) {
                TextButton(onClick = onOpenAccount) { Text(stringResource(R.string.go_to_account)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            }
        },
        dismissButton = if (message == DigitalMessage.NotSignedIn) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
        } else {
            null
        }
    )
}

/** The dark ink of the featured card and of text on light badges: the same in both themes. */
internal val INK = Color(0xFF17161C)
private const val OFFER_PALETTE_SIZE = 6
