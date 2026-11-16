// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.mediatheque.core.data.AccountRepository
import fr.mediatheque.core.data.HoldError
import fr.mediatheque.core.data.HoldRequest
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.designsystem.component.BusyButton
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.Notice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The steps of a hold: the pickup libraries are read, the patron chooses, the site confirms or refuses. */
sealed interface HoldStep {
    data object Preparing : HoldStep

    data class Choosing(val request: HoldRequest, val pickup: Library?, val isSubmitting: Boolean = false) : HoldStep

    data class Confirmed(val message: String) : HoldStep

    /** [siteMessage] is the site's own refusal, otherwise [reason] says what happened. */
    data class Failed(val reason: HoldFailure, val siteMessage: String? = null) : HoldStep
}

enum class HoldFailure { NOT_HOLDABLE, OFFLINE, SIGNED_OUT, OTHER }

@HiltViewModel(assistedFactory = HoldViewModel.Factory::class)
class HoldViewModel @AssistedInject constructor(
    @Assisted private val notice: Notice,
    @Assisted private val homeLibrary: Library?,
    private val account: AccountRepository
) : ViewModel() {
    private val _step = MutableStateFlow<HoldStep>(HoldStep.Preparing)
    val step: StateFlow<HoldStep> = _step.asStateFlow()

    init {
        prepare()
    }

    fun prepare() {
        _step.value = HoldStep.Preparing
        viewModelScope.launch {
            _step.value = try {
                val request = account.prepareHold(notice.id)
                // The patron's library by default, if it is offered; otherwise the site's choice.
                val pickup =
                    homeLibrary?.takeIf { it in request.pickupLibraries } ?: request.defaultPickup ?: request.pickupLibraries.firstOrNull()
                HoldStep.Choosing(request, pickup)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                failure(error)
            }
        }
    }

    fun choose(library: Library) = _step.update { (it as? HoldStep.Choosing)?.copy(pickup = library) ?: it }

    fun submit() {
        val choosing = _step.value as? HoldStep.Choosing ?: return
        val pickup = choosing.pickup ?: return
        _step.value = choosing.copy(isSubmitting = true)
        viewModelScope.launch {
            _step.value = try {
                HoldStep.Confirmed(account.placeHold(choosing.request, pickup))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                failure(error)
            }
        }
    }

    private fun failure(error: Exception): HoldStep.Failed = when {
        error is HoldError.NotHoldable -> HoldStep.Failed(HoldFailure.NOT_HOLDABLE)

        error is HoldError.Refused -> HoldStep.Failed(HoldFailure.OTHER, error.siteMessage)

        else -> when (LoadError.from(error)) {
            LoadError.Offline -> HoldStep.Failed(HoldFailure.OFFLINE)
            LoadError.NotLoggedIn -> HoldStep.Failed(HoldFailure.SIGNED_OUT)
            else -> HoldStep.Failed(HoldFailure.OTHER)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(notice: Notice, homeLibrary: Library?): HoldViewModel
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HoldSheet(viewModel: HoldViewModel, onDismiss: () -> Unit) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val current = step) {
                HoldStep.Preparing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.hold_preparing))
                }

                is HoldStep.Choosing -> {
                    Text(
                        stringResource(R.string.hold_where),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics {
                            heading()
                        }
                    )
                    Column(Modifier.selectableGroup()) {
                        current.request.pickupLibraries.forEach { library ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .selectable(selected = library == current.pickup, role = Role.RadioButton) {
                                        viewModel.choose(library)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = library == current.pickup, onClick = null)
                                Column(Modifier.padding(start = 16.dp)) {
                                    Text(library.displayName, style = MaterialTheme.typography.bodyLarge)
                                    if (library == current.request.defaultPickup) {
                                        Text(
                                            stringResource(R.string.hold_home_library),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    BusyButton(
                        text = stringResource(R.string.hold_confirm),
                        onClick = viewModel::submit,
                        busy = current.isSubmitting,
                        enabled = current.pickup != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is HoldStep.Confirmed -> {
                    Text(
                        stringResource(R.string.hold_done),
                        style = MaterialTheme.typography.titleLarge,
                        color = MediathequeColors.extended.success,
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        }
                    )
                    Text(current.message, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hold_close)) }
                }

                is HoldStep.Failed -> {
                    Text(
                        stringResource(R.string.hold_failed),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Assertive
                        }
                    )
                    Text(
                        current.siteMessage ?: stringResource(
                            when (current.reason) {
                                HoldFailure.NOT_HOLDABLE -> R.string.hold_not_holdable
                                HoldFailure.OFFLINE -> R.string.hold_offline
                                HoldFailure.SIGNED_OUT -> R.string.hold_signed_out
                                HoldFailure.OTHER -> R.string.hold_error
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (current.reason == HoldFailure.OFFLINE || current.reason == HoldFailure.OTHER) {
                            Button(onClick = viewModel::prepare) { Text(stringResource(R.string.hold_retry)) }
                        }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.hold_close)) }
                    }
                }
            }
        }
    }
}
