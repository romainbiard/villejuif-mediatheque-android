// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode as ScannedBarcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.BusyButton
import fr.mediatheque.core.designsystem.component.EmptyState
import fr.mediatheque.core.designsystem.component.SectionHeader
import fr.mediatheque.core.designsystem.component.SkeletonRow
import fr.mediatheque.core.designsystem.component.StateAction
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.RecordToRate
import fr.mediatheque.core.model.Review
import fr.mediatheque.core.model.SuggestionDraft
import fr.mediatheque.core.model.SuggestionKind
import fr.mediatheque.core.ui.CoverImage
import fr.mediatheque.core.ui.LoadErrorState

// My reviews

@Composable
internal fun ReviewsScreen(viewModel: ReviewsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val toRate by viewModel.toRate.collectAsStateWithLifecycle()
    val published by viewModel.published.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var rating by rememberSaveable { mutableStateOf<Int?>(null) }
    ProfileScaffold(
        title = stringResource(R.string.home_reviews),
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.reviews_to_rate)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.reviews_published)) })
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (tab == 0) {
                    toRateItems(toRate, onRetry = viewModel::load, onRate = { rating = it.recordId })
                } else {
                    publishedItems(published, onRetry = viewModel::load, onRateLatest = { tab = 0 })
                }
            }
        }
    }
    val record = rating?.let { id -> toRate.valueOrNull?.firstOrNull { it.recordId == id } }
    if (record != null) {
        RatingSheet(
            record = record,
            isSubmitting = isSubmitting,
            onSubmit = { stars, title, text -> viewModel.submit(record, stars, title, text, onSent = { rating = null }) },
            onDismiss = { rating = null }
        )
    }
}

private fun LazyListScope.toRateItems(state: LoadState<List<RecordToRate>>, onRetry: () -> Unit, onRate: (RecordToRate) -> Unit) {
    when (state) {
        LoadState.Loading -> items(3) { SkeletonRow(Modifier.readable()) }

        is LoadState.Failed -> item { LoadErrorState(state.error, onRetry, Modifier.readable()) }

        is LoadState.Loaded -> if (state.value.isEmpty()) {
            item {
                EmptyState(
                    DesignR.drawable.ic_star,
                    stringResource(R.string.reviews_nothing_title),
                    stringResource(R.string.reviews_nothing_message),
                    Modifier.readable()
                )
            }
        } else {
            item {
                Text(
                    stringResource(R.string.reviews_to_rate_footer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.readable()
                )
            }
            items(state.value, key = { it.recordId }) { record ->
                Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = Modifier.readable()) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverImage(record.coverUrl, record.title, record.recordId, Modifier.size(width = 52.dp, height = 76.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(record.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            record.author?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        FilledTonalButton(onClick = { onRate(record) }) { Text(stringResource(R.string.reviews_rate)) }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.publishedItems(state: LoadState<List<Review>>, onRetry: () -> Unit, onRateLatest: () -> Unit) {
    when (state) {
        LoadState.Loading -> items(3) { SkeletonRow(Modifier.readable()) }

        is LoadState.Failed -> item { LoadErrorState(state.error, onRetry, Modifier.readable()) }

        is LoadState.Loaded -> if (state.value.isEmpty()) {
            item {
                EmptyState(
                    icon = DesignR.drawable.ic_star,
                    title = stringResource(R.string.reviews_empty_title),
                    message = stringResource(R.string.reviews_empty_message),
                    modifier = Modifier.readable(),
                    primary = StateAction(stringResource(R.string.reviews_rate_latest), onRateLatest)
                )
            }
        } else {
            items(state.value, key = { it.id }) { review ->
                Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = Modifier.readable()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        review.rating?.let { Stars(it) }
                        Text(review.title, style = MaterialTheme.typography.titleMedium)
                        if (review.text.isNotBlank()) Text(review.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** A read-only rating, read out as "4 stars out of 5". */
@Composable
private fun Stars(rating: Int) {
    val description = pluralStringResource(R.plurals.reviews_stars, rating, rating)
    Row(Modifier.semantics(mergeDescendants = true) { stateDescription = description }) {
        repeat(ReviewsViewModel.MAX_RATING) { index ->
            Icon(
                painterResource(if (index < rating) DesignR.drawable.ic_star_filled else DesignR.drawable.ic_star),
                contentDescription = null,
                tint = MediathequeColors.extended.warning,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingSheet(record: RecordToRate, isSubmitting: Boolean, onSubmit: (Int, String, String) -> Unit, onDismiss: () -> Unit) {
    var stars by rememberSaveable { mutableIntStateOf(0) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.reviews_give),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(record.title, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.selectableGroup()) {
                (1..ReviewsViewModel.MAX_RATING).forEach { value ->
                    val label = pluralStringResource(R.plurals.reviews_stars, value, value)
                    IconButton(
                        onClick = { stars = value },
                        modifier = Modifier.semantics {
                            role = Role.RadioButton
                            selected = stars == value
                        }
                    ) {
                        Icon(
                            painterResource(if (value <= stars) DesignR.drawable.ic_star_filled else DesignR.drawable.ic_star),
                            contentDescription = label,
                            tint = MediathequeColors.extended.warning
                        )
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.reviews_title_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.reviews_text_field)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.reviews_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BusyButton(
                stringResource(R.string.reviews_send),
                onClick = { onSubmit(stars, title, text) },
                modifier = Modifier.fillMaxWidth(),
                busy = isSubmitting,
                enabled = stars > 0
            )
        }
    }
}

// My purchase suggestions

@Composable
internal fun SuggestionsScreen(viewModel: SuggestionsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val sent by viewModel.sent.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ProfileScaffold(
        title = stringResource(R.string.home_suggestions),
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    stringResource(R.string.suggestions_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.readable()
                )
            }
            item {
                SuggestionForm(
                    draft = draft,
                    isSending = isSending,
                    onChange = viewModel::updateDraft,
                    onScan = { scanIsbn(context) { isbn -> viewModel.updateDraft { it.copy(isbn = isbn) } } },
                    onSend = viewModel::send,
                    modifier = Modifier.readable()
                )
            }
            item { SectionHeader(stringResource(R.string.suggestions_sent_title), Modifier.readable()) }
            when (val state = sent) {
                LoadState.Loading -> item { SkeletonRow(Modifier.readable()) }

                is LoadState.Failed -> item {
                    Text(
                        stringResource(R.string.suggestions_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.readable()
                    )
                }

                is LoadState.Loaded -> if (state.value.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.suggestions_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.readable()
                        )
                    }
                } else {
                    item {
                        GroupCard(Modifier.readable()) {
                            state.value.forEach { suggestion ->
                                InfoRow(suggestion.title, suggestion.detail.orEmpty())
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionForm(
    draft: SuggestionDraft,
    isSending: Boolean,
    onChange: ((SuggestionDraft) -> SuggestionDraft) -> Unit,
    onScan: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = stringResource(draft.kind.label),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.suggestions_kind)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SuggestionKind.entries.forEach { kind ->
                    DropdownMenuItem(
                        text = { Text(stringResource(kind.label)) },
                        onClick = {
                            onChange { it.copy(kind = kind) }
                            expanded = false
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = draft.title,
            onValueChange = { value -> onChange { it.copy(title = value) } },
            label = { Text(stringResource(R.string.suggestions_title_field)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.author,
            onValueChange = { value -> onChange { it.copy(author = value) } },
            label = { Text(stringResource(R.string.suggestions_author_field)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.isbn,
            onValueChange = { value -> onChange { it.copy(isbn = value) } },
            label = { Text(stringResource(R.string.suggestions_isbn_field)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            trailingIcon = {
                IconButton(onClick = onScan) {
                    Icon(
                        painterResource(DesignR.drawable.ic_qr_code_scanner),
                        contentDescription = stringResource(R.string.suggestions_scan)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.comment,
            onValueChange = { value -> onChange { it.copy(comment = value) } },
            label = { Text(stringResource(R.string.suggestions_comment_field)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        BusyButton(
            stringResource(R.string.suggestions_send),
            onClick = onSend,
            modifier = Modifier.fillMaxWidth(),
            busy = isSending,
            enabled = draft.isValid
        )
    }
}

private val SuggestionKind.label: Int
    get() = when (this) {
        SuggestionKind.BOOK -> R.string.kind_book
        SuggestionKind.LARGE_PRINT -> R.string.kind_large_print
        SuggestionKind.AUDIOBOOK -> R.string.kind_audiobook
        SuggestionKind.COMIC -> R.string.kind_comic
        SuggestionKind.CD -> R.string.kind_cd
        SuggestionKind.DVD -> R.string.kind_dvd
        SuggestionKind.BOARD_GAME -> R.string.kind_board_game
        SuggestionKind.LANGUAGE_METHOD -> R.string.kind_language_method
        SuggestionKind.EBOOK -> R.string.kind_ebook
    }

/** The ISBN on the back of a book, through Google's code scanner: no camera permission. */
private fun scanIsbn(context: Context, onScanned: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(ScannedBarcode.FORMAT_EAN_13, ScannedBarcode.FORMAT_UPC_A).build()
    GmsBarcodeScanning.getClient(context, options).startScan().addOnSuccessListener { barcode ->
        barcode.rawValue?.filter { it.isDigit() || it == 'X' }?.takeIf { it.isNotEmpty() }?.let(onScanned)
    }
}
