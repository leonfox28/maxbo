package io.github.leonfox28.maxbo.core.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import io.github.leonfox28.maxbo.core.util.devLog
import io.github.leonfox28.maxbo.data.model.Account
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** A signed-in session as persisted between launches. */
data class StoredSession(
    val account: Account,
    /** Cookies keyed by site, then by cookie name. */
    val cookies: Map<String, Map<String, String>>,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("account", account.toJson())
        put(
            "cookies",
            JsonObject(
                cookies.mapValues { (_, values) ->
                    JsonObject(values.mapValues { JsonPrimitive(it.value) })
                }
            ),
        )
    }

    companion object {
        fun fromJson(json: JsonObject) =
            StoredSession(
                account = Account.fromJson(json.getValue("account").jsonObject),
                cookies =
                    json.getValue("cookies").jsonObject.mapValues { (_, values) ->
                        values.jsonObject.mapValues { (it.value as JsonPrimitive).content }
                    },
            )
    }
}

interface SessionStorage {
    suspend fun load(): StoredSession?

    suspend fun save(session: StoredSession)

    suspend fun clear()
}

/** Seals and opens bytes with a key that never leaves the device. */
interface SessionCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(sealed: ByteArray): ByteArray
}

/** Keeps the session in a [file], sealed by [cipher]. */
class EncryptedFileSessionStorage(private val file: File, private val cipher: SessionCipher) :
    SessionStorage {
    private val atomic = AtomicFile(file)

    override suspend fun load(): StoredSession? =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null
            try {
                val plain = cipher.decrypt(atomic.readFully())
                StoredSession.fromJson(Json.parseToJsonElement(plain.decodeToString()).jsonObject)
            } catch (e: Exception) {
                // A corrupt or outdated entry, or a key the system lost, just
                // means "signed out".
                devLog("stored session unreadable: ${e.javaClass.simpleName}")
                atomic.delete()
                null
            }
        }

    override suspend fun save(session: StoredSession) =
        withContext(Dispatchers.IO) {
            val sealed = cipher.encrypt(session.toJson().toString().encodeToByteArray())
            val out = atomic.startWrite()
            try {
                out.write(sealed)
                atomic.finishWrite(out)
            } catch (e: Exception) {
                atomic.failWrite(out)
                throw e
            }
        }

    override suspend fun clear() = withContext(Dispatchers.IO) { atomic.delete() }
}

/** AES-GCM with a key kept in the Android Keystore, made on first use. */
class KeystoreSessionCipher(private val alias: String = "maxbo.session") : SessionCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES),
        )
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
