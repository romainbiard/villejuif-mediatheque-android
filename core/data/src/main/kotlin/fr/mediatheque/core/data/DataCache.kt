// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Read from the cache, or force a reload (pull to refresh). */
enum class CachePolicy { USE_CACHE, RELOAD }

/** A value from the cache or the portal. [refreshError] when the portal failed and the value is the last one saved. */
data class Cached<T>(val value: T, val refreshError: LoadError? = null)

/**
 * Cache for the portal's public data (agenda, opening hours, searches, availability).
 * Never account data here: it lives in the account snapshot, erased on sign-out.
 *
 * - Always in memory; also on disk (in the app's cache folder) unless `persist = false`.
 *   Android may clear that folder at any time: the cache is only a speed-up.
 * - A fresh value (younger than `maxAge`) avoids the request; a stale value is the fallback
 *   when the network or the site does not answer.
 */
class DataCache(
    private val directory: File?,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private class Entry(val savedAt: Instant, val value: Any?)

    @Serializable
    private class StoredEntry(val savedAtMillis: Long, val json: String)

    private val mutex = Mutex()
    private val memory = mutableMapOf<String, Entry>()

    /** The cached value if it is fresh, otherwise [load] and store the result; a stale value rather than the error. */
    suspend fun <T> value(
        key: String,
        maxAge: Duration,
        serializer: KSerializer<T>,
        policy: CachePolicy = CachePolicy.USE_CACHE,
        persist: Boolean = true,
        load: suspend () -> T
    ): T = cached(key, maxAge, serializer, policy, persist, load).value

    /** Same as [value], and says when the value is a stale one served because [load] failed. */
    suspend fun <T> cached(
        key: String,
        maxAge: Duration,
        serializer: KSerializer<T>,
        policy: CachePolicy = CachePolicy.USE_CACHE,
        persist: Boolean = true,
        load: suspend () -> T
    ): Cached<T> {
        val entry = entry(key, serializer, persist)
        val now = clock.instant()
        if (policy == CachePolicy.USE_CACHE && entry != null && entry.savedAt.plusMillis(maxAge.inWholeMilliseconds) > now) {
            @Suppress("UNCHECKED_CAST")
            return Cached(entry.value as T)
        }
        val value = try {
            load()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            @Suppress("UNCHECKED_CAST")
            if (entry != null) return Cached(entry.value as T, refreshError = LoadError.from(error))
            throw error
        }
        store(key, Entry(clock.instant(), value), serializer, persist)
        return Cached(value)
    }

    private suspend fun <T> entry(key: String, serializer: KSerializer<T>, persist: Boolean): Entry? = mutex.withLock {
        memory[key]?.let { return@withLock it }
        if (!persist) return@withLock null
        val file = file(key) ?: return@withLock null
        withContext(ioDispatcher) {
            try {
                val stored = json.decodeFromString(StoredEntry.serializer(), file.readText())
                Entry(Instant.ofEpochMilli(stored.savedAtMillis), json.decodeFromString(serializer, stored.json))
            } catch (_: IOException) {
                null
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }?.also { memory[key] = it }
    }

    private suspend fun <T> store(key: String, entry: Entry, serializer: KSerializer<T>, persist: Boolean) = mutex.withLock {
        memory[key] = entry
        if (!persist) return@withLock
        val file = file(key) ?: return@withLock
        withContext(ioDispatcher) {
            try {
                @Suppress("UNCHECKED_CAST")
                val stored = StoredEntry(entry.savedAt.toEpochMilli(), json.encodeToString(serializer, entry.value as T))
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(StoredEntry.serializer(), stored))
            } catch (_: IOException) {
                // A cache that cannot write is only slower.
            }
        }
    }

    private fun file(key: String): File? {
        val name = key.map { if (it.isLetterOrDigit() || it == '-') it else '_' }.joinToString("")
        return directory?.let { File(it, "$name.json") }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
