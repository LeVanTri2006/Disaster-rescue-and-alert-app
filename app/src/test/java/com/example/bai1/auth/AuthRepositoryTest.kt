package com.example.bai1.auth

import com.example.bai1.auth.network.AuthApiClient
import com.example.bai1.auth.network.AuthApiError
import com.example.bai1.auth.network.AuthApiErrorKind
import com.example.bai1.auth.network.AuthApiResult
import com.example.bai1.auth.network.AuthTokens
import com.example.bai1.auth.session.AuthState
import com.example.bai1.auth.session.AuthUser
import com.example.bai1.auth.session.TokenStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRepositoryTest {
    private val user = AuthUser(
        id = "user-1",
        fullName = "Nguyễn Văn A",
        email = "a@example.com",
        phone = null,
        role = "USER",
        status = "ACTIVE",
        verificationStatus = null
    )

    @Test
    fun `login saves tokens before exposing authenticated user`() = runTest {
        val storage = FakeTokenStorage()
        val repository = AuthRepository(
            apiClient = FakeAuthApiClient(
                loginResult = AuthApiResult.Success(AuthTokens("access", "refresh")),
                meResult = AuthApiResult.Success(user)
            ),
            tokenStorage = storage
        )

        repository.login("a@example.com", "password")

        assertEquals("access", storage.getAccessToken())
        assertEquals("refresh", storage.getRefreshToken())
        assertEquals(AuthState.Authenticated(user), repository.state.value)
    }

    @Test
    fun `invalid credentials expose user friendly error`() = runTest {
        val repository = AuthRepository(
            apiClient = FakeAuthApiClient(
                loginResult = AuthApiResult.Failure(
                    AuthApiError(AuthApiErrorKind.UNAUTHORIZED, "HTTP 401")
                )
            ),
            tokenStorage = FakeTokenStorage()
        )

        repository.login("a@example.com", "wrong")

        assertEquals(
            AuthState.Error("Email/số điện thoại hoặc mật khẩu không đúng."),
            repository.state.value
        )
    }

    @Test
    fun `network failure does not create a session`() = runTest {
        val storage = FakeTokenStorage()
        val repository = AuthRepository(
            apiClient = FakeAuthApiClient(
                loginResult = AuthApiResult.Failure(
                    AuthApiError(AuthApiErrorKind.NO_INTERNET, "offline")
                )
            ),
            tokenStorage = storage
        )

        repository.login("a@example.com", "password")

        assertEquals(AuthState.Error("Không có kết nối mạng. Vui lòng kiểm tra lại."), repository.state.value)
        assertNull(storage.getAccessToken())
    }

    @Test
    fun `expired access token refreshes once and restores session`() = runTest {
        val storage = FakeTokenStorage("expired", "old-refresh")
        val api = FakeAuthApiClient(
            meResult = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNAUTHORIZED, "expired")),
            refreshResult = AuthApiResult.Success(AuthTokens("new-access", "new-refresh")),
            refreshedMeResult = AuthApiResult.Success(user)
        )
        val repository = AuthRepository(api, storage)

        repository.restoreSession()

        assertEquals(AuthState.Authenticated(user), repository.state.value)
        assertEquals("new-access", storage.getAccessToken())
        assertEquals("new-refresh", storage.getRefreshToken())
        assertEquals(1, api.refreshCalls)
    }

    @Test
    fun `refresh failure clears session and returns to login`() = runTest {
        val storage = FakeTokenStorage("expired", "old-refresh")
        val repository = AuthRepository(
            FakeAuthApiClient(
                meResult = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNAUTHORIZED, "expired")),
                refreshResult = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNAUTHORIZED, "invalid refresh"))
            ),
            storage
        )

        repository.restoreSession()

        assertEquals(AuthState.Unauthenticated, repository.state.value)
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
    }

    private class FakeTokenStorage(
        private var access: String? = null,
        private var refresh: String? = null
    ) : TokenStorage {
        override fun save(accessToken: String, refreshToken: String): Boolean {
            access = accessToken
            refresh = refreshToken
            return true
        }

        override fun getAccessToken(): String? = access
        override fun getRefreshToken(): String? = refresh
        override fun clear() {
            access = null
            refresh = null
        }
        override fun isLoggedIn(): Boolean = !access.isNullOrBlank()
    }

    private inner class FakeAuthApiClient(
        private val loginResult: AuthApiResult<AuthTokens> = AuthApiResult.Failure(
            AuthApiError(AuthApiErrorKind.UNKNOWN, "not configured")
        ),
        private val meResult: AuthApiResult<AuthUser> = AuthApiResult.Failure(
            AuthApiError(AuthApiErrorKind.UNKNOWN, "not configured")
        ),
        private val refreshResult: AuthApiResult<AuthTokens> = AuthApiResult.Failure(
            AuthApiError(AuthApiErrorKind.UNKNOWN, "not configured")
        ),
        private val refreshedMeResult: AuthApiResult<AuthUser> = meResult
    ) : AuthApiClient {
        var refreshCalls = 0

        override suspend fun login(identifier: String, password: String): AuthApiResult<AuthTokens> = loginResult

        override suspend fun refresh(refreshToken: String): AuthApiResult<AuthTokens> {
            refreshCalls++
            return refreshResult
        }

        override suspend fun me(accessToken: String): AuthApiResult<AuthUser> =
            if (refreshCalls > 0) refreshedMeResult else meResult

        override suspend fun register(fullName: String, email: String, phone: String, password: String): AuthApiResult<AuthTokens> = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNKNOWN, "not implemented")) 
        override suspend fun googleLogin(idToken: String): AuthApiResult<AuthTokens> = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNKNOWN, "not implemented")) 
        override suspend fun forgotPassword(identifier: String): AuthApiResult<Unit> = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNKNOWN, "not implemented")) 
        override suspend fun resetPassword(identifier: String, otp: String, newPassword: String): AuthApiResult<Unit> = AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNKNOWN, "not implemented")) 

        override suspend fun updateRole(accessToken: String, role: String): AuthApiResult<AuthUser> = 
            AuthApiResult.Success(user)
    }
}
