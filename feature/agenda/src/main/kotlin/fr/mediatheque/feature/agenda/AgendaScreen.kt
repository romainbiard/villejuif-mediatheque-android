// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.EmptyState
import fr.mediatheque.core.designsystem.component.SectionHeader
import fr.mediatheque.core.designsystem.component.SkeletonRow
import fr.mediatheque.core.designsystem.component.StateAction
import fr.mediatheque.core.designsystem.component.StatusPill
import fr.mediatheque.core.designsystem.component.StatusStyle
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.domain.SchoolHoliday
import fr.mediatheque.core.model.Event
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.ui.CoverImage
import fr.mediatheque.core.ui.DestinationScaffold
import fr.mediatheque.core.ui.LoadErrorState
import fr.mediatheque.core.ui.ParisFormat
import fr.mediatheque.core.ui.RefreshErrorBanner
import fr.mediatheque.core.ui.capitalizedSentence
import fr.mediatheque.core.ui.parisFormat
import java.time.LocalDate

@Composable
internal fun AgendaScreen(viewModel: AgendaViewModel, onOpenEvent: (Event) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AgendaContent(
        state = state,
        actions = AgendaActions(
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onSelectDay = viewModel::selectDay,
            onSelectPeriod = viewModel::selectPeriod,
            onApplyFilters = viewModel::applyFilters,
            onClearFilters = viewModel::clearFilters,
            onOpenEvent = onOpenEvent
        ),
        modifier = modifier
    )
}

internal class AgendaActions(
    val onRefresh: () -> Unit,
    val onRetry: () -> Unit,
    val onSelectDay: (LocalDate) -> Unit,
    val onSelectPeriod: (AgendaPeriod) -> Unit,
    val onApplyFilters: (AgendaFilters) -> Unit,
    val onClearFilters: () -> Unit,
    val onOpenEvent: (Event) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgendaContent(state: AgendaUiState, actions: AgendaActions, modifier: Modifier = Modifier) {
    var showsFilters by rememberSaveable { mutableStateOf(false) }
    val logic = state.logic
    DestinationScaffold(
        title = stringResource(R.string.agenda_title),
        modifier = modifier,
        actions = {
            val count = state.filters.count - if (state.filters.period == AgendaPeriod.ALL) 0 else 1
            val label = if (count == 0) {
                stringResource(R.string.agenda_filters)
            } else {
                pluralStringResource(R.plurals.agenda_filters_count, count, count)
            }
            IconButton(onClick = { showsFilters = true }, modifier = Modifier.semantics { contentDescription = label }) {
                BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
                    Icon(painterResource(DesignR.drawable.ic_tune), contentDescription = null)
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(state.isRefreshing, actions.onRefresh, Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { PeriodChips(state.filters.period, actions.onSelectPeriod) }
                state.staleError?.let { item { RefreshErrorBanner(it, Modifier.padding(horizontal = 16.dp)) } }
                when (val events = state.events) {
                    LoadState.Loading -> items(4) { SkeletonRow(Modifier.padding(horizontal = 16.dp)) }

                    is LoadState.Failed -> item { LoadErrorState(events.error, actions.onRetry) }

                    is LoadState.Loaded -> if (state.filters.period == AgendaPeriod.ALL) {
                        dayContent(state, logic, actions)
                    } else {
                        periodContent(state, logic, actions)
                    }
                }
            }
        }
    }
    if (showsFilters) {
        AgendaFiltersSheet(
            initial = state.filters,
            logic = logic,
            onApply = {
                actions.onApplyFilters(it)
                showsFilters = false
            },
            onDismiss = { showsFilters = false }
        )
    }
}

@Composable
private fun PeriodChips(selected: AgendaPeriod, onSelect: (AgendaPeriod) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AgendaPeriod.entries) { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(stringResource(period.label)) }
            )
        }
    }
}

internal val AgendaPeriod.label: Int
    get() = when (this) {
        AgendaPeriod.ALL -> R.string.agenda_period_day_by_day
        AgendaPeriod.WEEKEND -> R.string.agenda_period_weekend
        AgendaPeriod.WEDNESDAYS -> R.string.agenda_period_wednesdays
        AgendaPeriod.SCHOOL_HOLIDAYS -> R.string.agenda_period_holidays
    }

// Day by day

private fun LazyListScope.dayContent(state: AgendaUiState, logic: AgendaLogic, actions: AgendaActions) {
    item { DayStrip(logic, state.selectedDay, actions.onSelectDay) }
    val events = logic.eventsOn(state.selectedDay)
    val ongoing = logic.ongoingOn(state.selectedDay)
    if (events.isEmpty()) {
        item { EmptyDay(state, logic, actions) }
    } else {
        item { SectionHeader(stringResource(R.string.agenda_this_day), Modifier.padding(horizontal = 16.dp)) }
        items(events, key = { "day-${it.id}" }) { EventRow(it, actions.onOpenEvent) }
    }
    if (ongoing.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.agenda_ongoing), Modifier.padding(horizontal = 16.dp)) }
        items(ongoing, key = { "ongoing-${it.id}" }) { EventRow(it, actions.onOpenEvent) }
    }
}

/** The next 14 days: a dot under the days with events, the days the libraries are closed greyed. */
@Composable
private fun DayStrip(logic: AgendaLogic, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val format = parisFormat()
    val hasEvents = stringResource(R.string.agenda_has_events)
    val closed = stringResource(R.string.agenda_libraries_closed)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(logic.days) { day ->
            val isSelected = day == selected
            val isClosed = AgendaLogic.isLibraryClosingDay(day)
            val dayHasEvents = day in logic.daysWithEvents
            val background = if (isSelected) MaterialTheme.colorScheme.primary else MediathequeColors.extended.card
            val foreground = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    // A minimum, not a fixed size: the day grows with the text size instead of clipping it.
                    .sizeIn(minWidth = 56.dp, minHeight = 76.dp)
                    .background(background, RoundedCornerShape(18.dp))
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(day) })
                    .semantics {
                        contentDescription = format.weekdayDayMonth(day)
                        stateDescription = listOfNotNull(hasEvents.takeIf { dayHasEvents }, closed.takeIf { isClosed }).joinToString(", ")
                    }
                    .padding(vertical = 8.dp, horizontal = 6.dp)
                    .alpha(if (isClosed && !isSelected) 0.55f else 1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(format.shortWeekday(day), style = MaterialTheme.typography.labelMedium, color = foreground)
                Text("${day.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = foreground)
                Box(
                    Modifier
                        .size(6.dp)
                        .background(
                            if (dayHasEvents) MediathequeColors.extended.magenta else Color.Transparent,
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun EmptyDay(state: AgendaUiState, logic: AgendaLogic, actions: AgendaActions) {
    val format = parisFormat()
    val next = logic.nextDayWithEvents(state.selectedDay)
    EmptyState(
        icon = DesignR.drawable.ic_calendar_month,
        title = stringResource(R.string.agenda_empty_day_title),
        message = stringResource(
            if (AgendaLogic.isLibraryClosingDay(state.selectedDay)) R.string.agenda_empty_day_closed else R.string.agenda_empty_day_message
        ),
        primary = next?.let {
            StateAction(stringResource(R.string.agenda_next_day, format.weekdayDayMonth(it))) { actions.onSelectDay(it) }
        },
        secondary = if (state.filters.isEmpty) null else StateAction(stringResource(R.string.agenda_clear_filters), actions.onClearFilters),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

// A period

private fun LazyListScope.periodContent(state: AgendaUiState, logic: AgendaLogic, actions: AgendaActions) {
    item { PeriodSummary(state) }
    if (logic.periodDays.isEmpty() && logic.periodOngoing.isEmpty()) {
        item {
            EmptyState(
                icon = DesignR.drawable.ic_calendar_month,
                title = stringResource(R.string.agenda_empty_period_title),
                message = stringResource(R.string.agenda_empty_period_message),
                primary = StateAction(stringResource(R.string.agenda_see_all)) { actions.onSelectPeriod(AgendaPeriod.ALL) },
                secondary = if (state.filters.count >
                    1
                ) {
                    StateAction(stringResource(R.string.agenda_clear_filters), actions.onClearFilters)
                } else {
                    null
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
    logic.periodDays.forEach { periodDay ->
        item(key = "header-${periodDay.day}") { DayHeader(periodDay.day) }
        items(periodDay.events, key = { "${periodDay.day}-${it.id}" }) { EventRow(it, actions.onOpenEvent) }
    }
    if (logic.periodOngoing.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.agenda_during_period), Modifier.padding(horizontal = 16.dp)) }
        items(logic.periodOngoing, key = { "period-ongoing-${it.id}" }) { EventRow(it, actions.onOpenEvent) }
    }
}

@Composable
private fun DayHeader(day: LocalDate) {
    SectionHeader(parisFormat().weekdayDayMonth(day).capitalizedSentence(), Modifier.padding(horizontal = 16.dp))
}

/** "Samedi 31 octobre et dimanche 1er novembre", "Vacances de Noël, du 19 décembre au 3 janvier"… */
@Composable
private fun PeriodSummary(state: AgendaUiState) {
    val format = parisFormat()
    val days = state.filters.period.days(state.today)
    val text = when (state.filters.period) {
        AgendaPeriod.ALL -> null

        AgendaPeriod.WEEKEND -> if (days.size == 2) {
            stringResource(
                R.string.agenda_weekend_summary,
                format.weekdayDayMonth(days[0]),
                format.weekdayDayMonth(days[1])
            ).capitalizedSentence()
        } else {
            days.firstOrNull()?.let { format.weekdayDayMonth(it).capitalizedSentence() }
        }

        AgendaPeriod.WEDNESDAYS -> pluralStringResource(
            R.plurals.agenda_wednesdays_summary,
            AgendaPeriod.WEDNESDAY_WEEKS,
            AgendaPeriod.WEDNESDAY_WEEKS
        )

        AgendaPeriod.SCHOOL_HOLIDAYS -> AgendaLogic.schoolHoliday(state.today)?.let { holiday ->
            stringResource(
                R.string.agenda_holidays_summary,
                stringResource(holiday.kind.label),
                format.dayMonth(holiday.start),
                format.dayMonth(holiday.end.minusSeconds(1))
            )
        }
    } ?: return
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

internal val SchoolHoliday.Kind.label: Int
    get() = when (this) {
        SchoolHoliday.Kind.ALL_SAINTS -> R.string.agenda_holiday_all_saints
        SchoolHoliday.Kind.CHRISTMAS -> R.string.agenda_holiday_christmas
        SchoolHoliday.Kind.WINTER -> R.string.agenda_holiday_winter
        SchoolHoliday.Kind.SPRING -> R.string.agenda_holiday_spring
        SchoolHoliday.Kind.ASCENSION_BRIDGE -> R.string.agenda_holiday_ascension
        SchoolHoliday.Kind.SUMMER -> R.string.agenda_holiday_summer
    }

// An event

@Composable
internal fun EventRow(event: Event, onOpen: (Event) -> Unit, modifier: Modifier = Modifier) {
    val format = parisFormat()
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MediathequeColors.extended.card,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(event) }
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoverImage(event.imageUrl, event.title, event.id, Modifier.size(width = 72.dp, height = 72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(schedule(event, format), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                event.place?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                event.audience?.let { StatusPill(it, StatusStyle.ACCENT) }
            }
        }
    }
}

/** "10:30 – 11:00", "Jusqu'au 27 mars" for an exhibition. */
@Composable
internal fun schedule(event: Event, format: ParisFormat): String {
    val start = event.start ?: return ""
    if (event.isAllDay) {
        return event.end?.let { stringResource(R.string.agenda_until, format.dayMonth(it)) } ?: format.dayMonth(start)
    }
    val end = event.end
    return if (end != null && end != start && Paris.dayOf(end) == Paris.dayOf(start)) {
        stringResource(R.string.agenda_time_range, format.time(start), format.time(end))
    } else {
        format.time(start)
    }
}
