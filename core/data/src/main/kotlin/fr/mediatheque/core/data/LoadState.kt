// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

/** Loading state that the ViewModels expose. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data class Loaded<T>(val value: T) : LoadState<T>

    data class Failed(val error: LoadError) : LoadState<Nothing>

    val valueOrNull: T? get() = (this as? Loaded<T>)?.value
}
