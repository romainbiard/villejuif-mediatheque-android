// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.info

import android.content.Context
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.SkeletonRow
import fr.mediatheque.core.designsystem.component.StatusPill
import fr.mediatheque.core.designsystem.component.StatusStyle
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.LibraryInfo
import fr.mediatheque.core.model.OpeningHours
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.ui.DestinationScaffold
import fr.mediatheque.core.ui.ExternalActions
import fr.mediatheque.core.ui.LibraryMap
import fr.mediatheque.core.ui.LoadErrorState
import fr.mediatheque.core.ui.MapPin
import fr.mediatheque.core.ui.ParisFormat
import fr.mediatheque.core.ui.RefreshErrorBanner
import fr.mediatheque.core.ui.parisFormat
import fr.mediatheque.core.ui.rememberNow
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun InfoScreen(viewModel: InfoViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InfoContent(state, onRefresh = viewModel::refresh, onRetry = viewModel::retry, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InfoContent(
    state: InfoUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = rememberNow()
) {
    DestinationScaffold(stringResource(R.string.info_title), modifier) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.staleError?.let { error -> item { RefreshErrorBanner(error, Modifier.readable()) } }
                item {
                    LibraryMap(
                        pins = Library.entries.map { MapPin(it.latitude, it.longitude, it.shortName) },
                        // PULP Nord and PULP Sud are 2.5 km apart, north to south: the center sits a little north of
                        // their midpoint, so both pins and their names fit.
                        centerLatitude = 48.7934,
                        centerLongitude = 2.3617,
                        zoom = 12.0,
                        description = stringResource(R.string.info_map_description),
                        modifier = Modifier.readable().height(260.dp)
                    )
                }
                when (val libraries = state.libraries) {
                    LoadState.Loading -> items(3) { SkeletonRow(Modifier.readable()) }
                    is LoadState.Failed -> item { LoadErrorState(libraries.error, onRetry, Modifier.readable()) }
                    is LoadState.Loaded -> items(libraries.value, key = { it.id }) { LibraryCard(it, now, Modifier.readable()) }
                }
            }
        }
    }
}

/** Cards no wider than reads well on a tablet. */
private fun Modifier.readable() = widthIn(max = 640.dp).fillMaxWidth()

@Composable
private fun LibraryCard(library: LibraryInfo, now: Instant, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val format = parisFormat()
    val status = library.hours.status(now)
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(library.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(
                    library.addressLines.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(statusText(status, now, format), if (status is OpeningHours.Status.Open) StatusStyle.SUCCESS else StatusStyle.DANGER)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val place = library.library
                if (place != null) {
                    ActionButton(
                        stringResource(R.string.info_directions),
                        DesignR.drawable.ic_directions,
                        prominent = true,
                        Modifier.weight(1f)
                    ) {
                        context.orToast(ExternalActions.directions(context, place.latitude, place.longitude, library.name))
                    }
                }
                library.phone?.let { phone ->
                    ActionButton(stringResource(R.string.info_call), DesignR.drawable.ic_call, prominent = false, Modifier.weight(1f)) {
                        context.orToast(ExternalActions.dial(context, phone))
                    }
                }
                library.contactUrl?.let { url ->
                    ActionButton(stringResource(R.string.info_write), DesignR.drawable.ic_mail, prominent = false, Modifier.weight(1f)) {
                        context.orToast(ExternalActions.openWebPage(context, url))
                    }
                }
            }
            HoursTable(library.hourLines, now)
        }
    }
}

/** "Ouvert · ferme à 19:00", "Fermé · ouvre demain à 13:00", "Fermé · ouvre mardi à 13:00". */
@Composable
private fun statusText(status: OpeningHours.Status, now: Instant, format: ParisFormat): String = when (status) {
    is OpeningHours.Status.Open -> stringResource(R.string.info_open_closes_at, format.time(status.closesAt))

    is OpeningHours.Status.Closed -> {
        val reopens = status.reopensAt
        when {
            reopens == null -> stringResource(R.string.info_closed)

            Paris.dayOf(reopens) == Paris.dayOf(now) -> stringResource(R.string.info_closed_opens_at, format.time(reopens))

            Paris.dayOf(
                reopens
            ) == Paris.dayOf(now).plusDays(1) -> stringResource(R.string.info_closed_opens_tomorrow, format.time(reopens))

            else -> stringResource(R.string.info_closed_opens_on, format.weekday(reopens), format.time(reopens))
        }
    }
}

@Composable
private fun ActionButton(label: String, @DrawableRes icon: Int, prominent: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
    if (prominent) {
        Button(onClick, modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { content() }
    } else {
        FilledTonalButton(onClick, modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { content() }
    }
}

/**
 * The week's hours, today's row in bold. The site writes them in French ("Mardi : 13h - 19h"): the day is shown
 * in the patron's language, the hours as the site gives them.
 */
@Composable
private fun HoursTable(lines: List<String>, now: Instant) {
    val today = Paris.dayOf(now).dayOfWeek
    val todayLabel = stringResource(R.string.info_today)
    val locale =
        ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.FRANCE
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        lines.forEachIndexed { index, line ->
            val siteDay = line.substringBefore(':').trim()
            val hours = line.substringAfter(':', "").trim()
            val dayOfWeek = frenchDays[siteDay.lowercase(Locale.FRENCH)]
            val day = dayOfWeek?.getDisplayName(TextStyle.FULL, locale)?.replaceFirstChar { it.titlecase(locale) } ?: siteDay
            val isToday = dayOfWeek == today
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isToday) "$day ($todayLabel)" else day,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else null
                )
                Text(
                    hours,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else null,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (index < lines.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private val frenchDays = DayOfWeek.entries.associateBy { it.getDisplayName(TextStyle.FULL, Locale.FRENCH) }

private fun Context.orToast(handled: Boolean) {
    if (!handled) Toast.makeText(this, R.string.info_no_app, Toast.LENGTH_SHORT).show()
}
