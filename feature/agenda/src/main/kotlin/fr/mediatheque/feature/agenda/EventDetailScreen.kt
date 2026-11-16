// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.agenda

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.StatusPill
import fr.mediatheque.core.designsystem.component.StatusStyle
import fr.mediatheque.core.ui.CoverImage
import fr.mediatheque.core.ui.ExternalActions
import fr.mediatheque.core.ui.LibraryMap
import fr.mediatheque.core.ui.MapPin
import fr.mediatheque.core.ui.parisFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EventDetailScreen(viewModel: EventDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val event = viewModel.event
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val format = parisFormat()
    val noApp = stringResource(R.string.event_no_app)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.event_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!ExternalActions.share(
                                context,
                                event.title,
                                viewModel.pageUrl
                            )
                        ) {
                            Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(painterResource(DesignR.drawable.ic_share), contentDescription = stringResource(R.string.event_share))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CoverImage(event.imageUrl, event.title, event.id, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Text(event.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(
                    event.dateDescription ?: schedule(event, format),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                event.place?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    event.type?.let { StatusPill(it, StatusStyle.NEUTRAL) }
                    event.audience?.let { StatusPill(it, StatusStyle.ACCENT) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val added = ExternalActions.addToCalendar(
                                context,
                                event.title,
                                event.start,
                                event.end,
                                event.isAllDay,
                                event.place,
                                viewModel.pageUrl
                            )
                            if (!added) Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painterResource(DesignR.drawable.ic_calendar_add_on),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(stringResource(R.string.event_add_to_calendar), Modifier.padding(start = 8.dp))
                    }
                    FilledTonalButton(onClick = { ExternalActions.share(context, event.title, viewModel.pageUrl) }) {
                        Icon(painterResource(DesignR.drawable.ic_share), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.event_share), Modifier.padding(start = 8.dp))
                    }
                }
                when (val loaded = detail) {
                    LoadState.Loading -> Unit

                    is LoadState.Failed -> Text(
                        stringResource(R.string.event_detail_unavailable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    is LoadState.Loaded -> {
                        loaded.value.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        val latitude = loaded.value.latitude
                        val longitude = loaded.value.longitude
                        if (latitude != null && longitude != null) {
                            val label = event.place ?: event.title
                            LibraryMap(
                                pins = listOf(MapPin(latitude, longitude, label)),
                                centerLatitude = latitude,
                                centerLongitude = longitude,
                                zoom = 15.0,
                                description = stringResource(R.string.event_map_description, label),
                                modifier = Modifier.fillMaxWidth().height(220.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
