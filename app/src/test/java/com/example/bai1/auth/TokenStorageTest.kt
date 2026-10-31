package com.example.bai1.auth

import com.example.bai1.auth.session.TokenStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenStorageTest {
    @Test
    fun `save and retrieve both tokens`() {
        val storage = InMemoryTokenStorage()

        storage.save("access", "refresh")

        assertEquals("access", storage.getAccessToken())
        assertEquals("refresh", storage.getRefreshToken())
        assertTrue(storage.isLoggedIn())
    }

    @Test
    fun `clear removes session`() {
        val storage = InMemoryTokenStorage()
        storage.save("access", "refresh")

        storage.clear()

        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertFalse(storage.isLoggedIn())
    }

    private class InMemoryTokenStorage : TokenStorage {
        private var accessToken: String? = null
        private var refreshToken: String? = null

        override fun save(accessToken: String, refreshToken: String): Boolean {
            this.accessToken = accessToken
            this.refreshToken = refreshToken
            return true
        }

        override fun getAccessToken(): String? = accessToken
        override fun getRefreshToken(): String? = refreshToken

        override fun clear() {
            accessToken = null
            refreshToken = null
        }

        override fun isLoggedIn(): Boolean = !accessToken.isNullOrBlank()
    }
}
