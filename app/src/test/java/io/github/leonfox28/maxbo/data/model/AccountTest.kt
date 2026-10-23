package io.github.leonfox28.maxbo.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountTest {
    private fun config(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `reads the user from CONFIG`() {
        val account =
            Account.tryFromPageConfig(
                config("""{"user":{"id":42,"idstr":"42","screen_name":"测试用户"},"uid":42}""")
            )

        assertEquals("42", account?.uid)
        assertEquals("测试用户", account?.screenName)
    }

    @Test
    fun `falls back to user idstr, then user id`() {
        assertEquals("7", Account.tryFromPageConfig(config("""{"user":{"idstr":"7"}}"""))?.uid)
        assertEquals("8", Account.tryFromPageConfig(config("""{"user":{"id":8}}"""))?.uid)
    }

    @Test
    fun `returns null for visitors`() {
        assertNull(Account.tryFromPageConfig(config("""{"uid":0}""")))
        assertNull(Account.tryFromPageConfig(config("""{"uid":"","user":null}""")))
        assertNull(Account.tryFromPageConfig(config("""{"serverTime":1}""")))
    }

    @Test
    fun `round-trips through JSON`() {
        val account = Account(uid = "42", screenName = "n")
        assertEquals(account, Account.fromJson(account.toJson()))
        assertEquals(Account("42"), Account.fromJson(Account("42").toJson()))
    }
}
