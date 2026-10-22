package io.github.leonfox28.maxbo.core.session

import io.github.leonfox28.maxbo.data.model.Account
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedFileSessionStorageTest {
    @get:Rule val folder = TemporaryFolder()

    /** Stands in for the Keystore, which only exists on a device. */
    private val cipher =
        object : SessionCipher {
            override fun encrypt(plain: ByteArray) = byteArrayOf(1) + plain.reversedArray()

            override fun decrypt(sealed: ByteArray): ByteArray {
                require(sealed.first() == 1.toByte()) { "not sealed by this key" }
                return sealed.drop(1).toByteArray().reversedArray()
            }
        }

    private val session =
        StoredSession(
            Account("42", "测试用户"),
            mapOf(
                "weibo.com" to mapOf("SUB" to "a", "XSRF-TOKEN" to "b"),
                "weibo.cn" to mapOf("SUB" to "c"),
            ),
        )

    private fun file() = File(folder.root, "session")

    @Test
    fun `starts with nothing`() = runTest {
        assertNull(EncryptedFileSessionStorage(file(), cipher).load())
    }

    @Test
    fun `reads back what it saved, sealed`() = runTest {
        val storage = EncryptedFileSessionStorage(file(), cipher)
        storage.save(session)

        assertEquals(session, EncryptedFileSessionStorage(file(), cipher).load())
        assertFalse(file().readText(Charsets.ISO_8859_1).contains("XSRF-TOKEN"))
    }

    @Test
    fun `clears`() = runTest {
        val storage = EncryptedFileSessionStorage(file(), cipher)
        storage.save(session)
        storage.clear()

        assertNull(storage.load())
        assertFalse(file().exists())
    }

    @Test
    fun `an entry it can't read means signed out, and goes`() = runTest {
        file().writeBytes(byteArrayOf(9, 9, 9))

        assertNull(EncryptedFileSessionStorage(file(), cipher).load())
        assertFalse(file().exists())
    }
}
