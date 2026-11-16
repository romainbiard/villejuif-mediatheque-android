// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Errors reported by the Bokeh portal or by the transport. */
sealed class BokehError(message: String) : Exception(message) {
    /**
     * The web session expired or was never opened
     * (`Autorisation non spécifiée` / `Jeton d'autorisation invalide`).
     */
    data object Unauthorized : BokehError("Session expired")

    /** Business error returned by the JSON API (`{"error", "message"}`). */
    data class Api(val code: String, val detail: String) : BokehError("$code: $detail")

    /** Unexpected HTTP response (5xx, 404...). */
    data class Http(val status: Int) : BokehError("HTTP $status")

    /** The received content no longer matches the expected format. */
    data class UnexpectedFormat(val detail: String) : BokehError(detail)

    /** The BunkerWeb anti-bot challenge in front of the portal could not be solved. */
    data object ChallengeFailed : BokehError("Anti-bot challenge not solved")

    companion object {
        private val unauthorizedMessages = setOf("Autorisation non spécifiée", "Jeton d'autorisation invalide")

        /** Bokeh error contained in a JSON body, or `null` if it is not one. */
        fun fromJson(body: String): BokehError? {
            val json = try {
                Json.parseToJsonElement(body) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: return null
            val error = (json["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val message = (json["message"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error
            return if (message in unauthorizedMessages) Unauthorized else Api(error, message)
        }
    }
}
