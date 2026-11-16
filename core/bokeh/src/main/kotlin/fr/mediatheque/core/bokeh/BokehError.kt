// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Erreurs signalées par le portail Bokeh ou par le transport. */
sealed class BokehError(message: String) : Exception(message) {
    /**
     * La session web a expiré ou n'a jamais été ouverte
     * (`Autorisation non spécifiée` / `Jeton d'autorisation invalide`).
     */
    data object Unauthorized : BokehError("Session expirée")

    /** Erreur métier renvoyée par l'API JSON (`{"error", "message"}`). */
    data class Api(val code: String, val detail: String) : BokehError("$code : $detail")

    /** Réponse HTTP inattendue (5xx, 404…). */
    data class Http(val status: Int) : BokehError("HTTP $status")

    /** Le contenu reçu ne correspond plus au format attendu. */
    data class UnexpectedFormat(val detail: String) : BokehError(detail)

    /** Le défi anti-robots BunkerWeb devant le portail n'a pas pu être résolu. */
    data object ChallengeFailed : BokehError("Défi anti-robots non résolu")

    companion object {
        private val unauthorizedMessages = setOf("Autorisation non spécifiée", "Jeton d'autorisation invalide")

        /** Erreur Bokeh contenue dans un corps JSON, `null` si ce n'en est pas une. */
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
