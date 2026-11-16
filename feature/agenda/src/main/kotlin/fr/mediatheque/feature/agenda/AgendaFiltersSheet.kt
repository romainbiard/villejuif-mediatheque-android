// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.mediatheque.core.designsystem.R as DesignR

/**
 * Filters by place, kind of event and audience, edited as a draft: the button says how many events they would show,
 * and nothing changes until it is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgendaFiltersSheet(initial: AgendaFilters, logic: AgendaLogic, onApply: (AgendaFilters) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.agenda_filters),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                TextButton(
                    onClick = { draft = AgendaFilters(period = draft.period) },
                    enabled =
                        draft.count > (if (draft.period == AgendaPeriod.ALL) 0 else 1)
                ) {
                    Text(stringResource(R.string.agenda_filter_reset))
                }
            }
            FilterGroup(stringResource(R.string.agenda_filter_place), logic.availablePlaces, draft.places) {
                draft = draft.copy(places = it)
            }
            FilterGroup(stringResource(R.string.agenda_filter_type), logic.availableTypes, draft.types) { draft = draft.copy(types = it) }
            FilterGroup(stringResource(R.string.agenda_filter_audience), logic.availableAudiences, draft.audiences) {
                draft =
                    draft.copy(audiences = it)
            }
            val count = logic.upcomingCount(draft)
            Button(onClick = { onApply(draft) }, enabled = count > 0, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    if (count ==
                        0
                    ) {
                        stringResource(R.string.agenda_no_event)
                    } else {
                        pluralStringResource(R.plurals.agenda_show_events, count, count)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, values: List<String>, selection: Set<String>, onChange: (Set<String>) -> Unit) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                val selected = value in selection
                FilterChip(
                    selected = selected,
                    onClick = { onChange(if (selected) selection - value else selection + value) },
                    label = { Text(value) },
                    leadingIcon = if (selected) {
                        { Icon(painterResource(DesignR.drawable.ic_check), null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}
