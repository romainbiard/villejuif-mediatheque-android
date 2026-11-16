// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.datastore

import com.google.crypto.tink.Aead
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One value kept on the device: the credentials, the last known account, the snapshot, the inbox. */
interface ValueStore<T : Any> {
    /** The stored value, `null` if there is none or it can no longer be read. */
    suspend fun load(): T?

    suspend fun save(value: T)

    suspend fun clear()
}

/**
 * A value in a JSON file of the app's private storage, written atomically (temporary file, then rename).
 * Private storage is encrypted by the system and readable once the device has been unlocked after boot.
 * When [aead] is set, the file is also encrypted with it: for the credentials and the patron's details.
 */
class FileValueStore<T : Any>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val aead: (suspend () -> Aead)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ValueStore<T> {
    private val mutex = Mutex()

    /** Ties the ciphertext to this file: a copy of another vault file cannot be swapped in. */
    private val associatedData = file.name.toByteArray()

    override suspend fun load(): T? = access {
        if (!file.exists()) return@access null
        try {
            val bytes = file.readBytes()
            val plain = aead?.invoke()?.decrypt(bytes, associatedData) ?: bytes
            json.decodeFromString(serializer, plain.decodeToString())
        } catch (_: GeneralSecurityException) {
            // The key is gone (restored from another device, lock screen removed…): the value is lost for good.
            file.delete()
            null
        } catch (_: SerializationException) {
            file.delete()
            null
        } catch (_: IllegalArgumentException) {
            file.delete()
            null
        }
    }

    override suspend fun save(value: T) {
        access { write(value) }
    }

    private suspend fun write(value: T) {
        val plain = json.encodeToString(serializer, value).toByteArray()
        val bytes = aead?.invoke()?.encrypt(plain, associatedData) ?: plain
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeBytes(bytes)
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            temporary.delete()
            throw error
        }
    }

    override suspend fun clear() {
        access { file.delete() }
    }

    private suspend fun <R> access(block: suspend () -> R): R = withContext(ioDispatcher) { mutex.withLock { block() } }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** In memory only: for tests, previews and the demo mode. */
class InMemoryValueStore<T : Any>(initial: T? = null) : ValueStore<T> {
    @Volatile
    var value: T? = initial
        private set

    override suspend fun load(): T? = value

    override suspend fun save(value: T) {
        this.value = value
    }

    override suspend fun clear() {
        value = null
    }
}
