// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import kotlinx.coroutines.launch

/** "How it works": the first-use steps, what to copy, the companion app, and the full tutorial. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HowItWorksSheet(
    offer: DigitalOffer,
    state: DigitalUiState,
    onPrimary: () -> Unit,
    onInstall: () -> Unit,
    onTutorial: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OfferBadge(offer)
                Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                    Text(
                        stringResource(offer.category).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(stringResource(R.string.menu_how), style = MaterialTheme.typography.headlineSmall)
                }
            }
            Text(stringResource(offer.summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
            offer.firstUseSteps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(step), Modifier.weight(1f))
                }
            }
            offer.copyableHint?.let { CopyRow(it) }
            // A companion app (reader, music, comics): installed or to install, from here.
            offer.app?.takeIf { it.role != PartnerApp.Role.REQUIRED }?.let { app ->
                CompanionRow(app, installed = state.isInstalled(app), onInstall = onInstall)
            }
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(primaryLabel(offer, state, compact = false))
            }
            TextButton(onClick = onTutorial, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.menu_tutorial)) }
        }
    }
}

@Composable
private fun CopyRow(hint: CopyableHint) {
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var copied by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(hint.label)
    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(hint.value, style = MaterialTheme.typography.headlineSmall)
            }
            FilledTonalButton(onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, hint.value))) }
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                copied = true
            }) {
                Icon(
                    painterResource(if (copied) DesignR.drawable.ic_check else DesignR.drawable.ic_content_copy),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(if (copied) R.string.how_copied else R.string.how_copy))
            }
        }
    }
}

@Composable
private fun CompanionRow(app: PartnerApp, installed: Boolean, onInstall: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(INK), contentAlignment = Alignment.Center) {
                Icon(painterResource(DesignR.drawable.ic_download), contentDescription = null, tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.how_free, app.publisher),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (installed) {
                Text(
                    stringResource(R.string.access_app_installed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MediathequeColors.extended.success
                )
            } else {
                FilledTonalButton(onClick = onInstall) { Text(stringResource(R.string.digital_install)) }
            }
        }
    }
}

/** "Install the app", before the Play Store: why the app, and the web as an alternative. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstallAppSheet(offer: DigitalOffer, onGet: () -> Unit, onContinueOnWeb: () -> Unit, onDismiss: () -> Unit) {
    val app = offer.app ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(17.dp)).background(INK), contentAlignment = Alignment.Center) {
                    Icon(painterResource(offer.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(app.name, style = MaterialTheme.typography.titleLarge)
                    Text(app.publisher, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.install_free),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val title = when (offer.id) {
                "films" -> R.string.install_title_films
                "presse" -> R.string.install_title_press
                "formation" -> R.string.install_title_learning
                else -> R.string.install_title_default
            }
            Text(
                stringResource(title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 22.dp).semantics { heading() }
            )
            Text(
                stringResource(R.string.install_message, stringResource(offer.provider)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (app.highlights.isNotEmpty()) {
                Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    app.highlights.forEach { highlight ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(highlight.icon),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(stringResource(highlight.text), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            Button(onClick = onGet, modifier = Modifier.padding(top = 24.dp).fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(painterResource(DesignR.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.install_get))
            }
            TextButton(onClick = onContinueOnWeb, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.install_continue_web))
            }
        }
    }
}
