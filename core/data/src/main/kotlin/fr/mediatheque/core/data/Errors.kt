// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.bokeh.BokehError
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/** Sign-in errors. */
sealed class AuthError(message: String) : Exception(message) {
    /** The portal said so explicitly ("L'identifiant ou le mot de passe est invalide."). */
    data object InvalidCredentials : AuthError("Invalid credentials")

    data object NotLoggedIn : AuthError("Not logged in")

    /**
     * The form went through but no session opened, without the portal refusing the credentials:
     * a temporary failure, never a reason to forget the stored password.
     */
    data object SessionNotOpened : AuthError("Session not opened")
}

sealed class HoldError(message: String) : Exception(message) {
    /** The record has no copy that can be held. */
    data object NotHoldable : HoldError("Not holdable")

    /** The site refused the hold, with its message. */
    data class Refused(val siteMessage: String) : HoldError(siteMessage)
}

sealed class ProfileError(message: String) : Exception(message) {
    /** The site rejected the form, with its message. */
    data class Rejected(val siteMessage: String) : ProfileError(siteMessage)

    data object MissingPatronId : ProfileError("Missing patron id")

    /** The site answered, but the record is not in (or out of) the selection as asked. */
    data object SelectionNotChanged : ProfileError("Selection not changed")
}

/** An error the screens can show: what happened, and so what the patron can do. */
sealed interface LoadError {
    data object Offline : LoadError

    data object SiteUnavailable : LoadError

    /** The site answered, but not in the expected format (a field changed, `null` where a value was expected). */
    data object UnreadableAnswer : LoadError

    data object NotLoggedIn : LoadError

    data class Other(val detail: String) : LoadError

    companion object {
        fun from(error: Throwable): LoadError = when (error) {
            is UnknownHostException, is NoRouteToHostException -> Offline

            is ConnectException -> if (error.message.orEmpty().contains("unreachable", ignoreCase = true)) Offline else SiteUnavailable

            is IOException -> SiteUnavailable

            AuthError.NotLoggedIn, AuthError.InvalidCredentials, BokehError.Unauthorized -> NotLoggedIn

            // Temporary: never shown as signed out (that would sign the patron out and wipe the vault).
            AuthError.SessionNotOpened, BokehError.ChallengeFailed -> SiteUnavailable

            is BokehError.Http -> SiteUnavailable

            is BokehError.UnexpectedFormat -> UnreadableAnswer

            else -> Other(error.message ?: error::class.simpleName.orEmpty())
        }
    }
}
