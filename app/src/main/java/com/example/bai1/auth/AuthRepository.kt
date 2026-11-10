package com.example.bai1.auth

import com.example.bai1.auth.network.*
import com.example.bai1.auth.session.*
import com.example.bai1.auth.firebase.FirebaseAuthSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

class AuthRepository(
    private val apiClient: AuthApiClient,
    private val tokenStorage: TokenStorage,
    private val firebaseAuthSession: FirebaseAuthSession? = null,
    private val invalidateRoutingSession: () -> Unit
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val _firebaseCleanupPending = MutableStateFlow(false)
    /** Adapter cleanup uncertainty; false does not certify the real Firebase SDK. */
    val firebaseCleanupPending: StateFlow<Boolean> = _firebaseCleanupPending.asStateFlow()
    private val lock = Any()
    private var epoch = 0L
    private var pendingJob: Job? = null
    // Serialize SDK effects, not HTTP requests. A stale sign-in must finish and be cleaned
    // before a newer account can sign in. Never hold the storage/state monitor while suspending.
    private val firebaseEffects = Mutex()
    private data class Operation(val epoch: Long, val context: CoroutineContext, val access: String?, val refresh: String?)

    private fun begin(context: CoroutineContext, clearTokens: Boolean, loading: Boolean = true): Operation {
        context.ensureActive()
        return synchronized(lock) {
            context.ensureActive()
            val operation = Operation(++epoch, context, tokenStorage.getAccessToken(), tokenStorage.getRefreshToken())
            pendingJob = context[Job]
            invalidateRoutingSession()
            if (clearTokens) tokenStorage.clear()
            if (loading) {
                _state.value = AuthState.Loading
                _firebaseCleanupPending.value = firebaseAuthSession != null
            }
            operation
        }
    }
    private fun current(operation: Operation) = synchronized(lock) { epoch == operation.epoch }
    private fun change(operation: Operation, action: () -> Unit): Boolean {
        operation.context.ensureActive()
        return synchronized(lock) {
            operation.context.ensureActive()
            if (epoch != operation.epoch) false else { action(); true }
        }
    }
    private fun revokeLocked(): Long {
        epoch++
        pendingJob = null
        tokenStorage.clear()
        invalidateRoutingSession()
        _state.value = AuthState.Unauthenticated
        _firebaseCleanupPending.value = firebaseAuthSession != null
        return epoch
    }
    /** Only a ViewModel that owns the in-flight operation may revoke it when cleared.
     * A completed saved session survives ordinary Activity/process recreation.
     */
    fun cancelPendingOperations(ownedJobs: Set<Job>) = synchronized(lock) {
        if (pendingJob != null && pendingJob in ownedJobs) revokeLocked()
        Unit
    }
    private suspend fun cleanupAdapterLocked() {
        val adapter = firebaseAuthSession ?: return
        try { adapter.signOut(); _firebaseCleanupPending.value = false }
        catch (_: Exception) { _firebaseCleanupPending.value = true }
    }
    private suspend fun cleanupIfCurrent(expected: Long) = withContext(NonCancellable) {
        firebaseEffects.withLock {
            if (synchronized(lock) { epoch == expected }) cleanupAdapterLocked()
        }
    }
    private suspend fun <T> operation(clearTokens: Boolean, loading: Boolean = true, block: suspend (Operation) -> T): T? {
        val ticket = begin(currentCoroutineContext(), clearTokens, loading)
        try { return block(ticket) }
        catch (cancelled: CancellationException) {
            val cleanupEpoch = synchronized(lock) {
                if (epoch == ticket.epoch) revokeLocked()
                else if (_state.value is AuthState.Unauthenticated) epoch else null
            }
            if (cleanupEpoch != null) cleanupIfCurrent(cleanupEpoch)
            throw cancelled
        } catch (_: Exception) {
            if (change(ticket) {
                tokenStorage.clear(); pendingJob = null
                _state.value = AuthState.Error("Không thể đăng nhập lúc này.")
                _firebaseCleanupPending.value = firebaseAuthSession != null
            }) cleanupIfCurrent(ticket.epoch)
            return null
        }
    }
    private suspend fun fail(ticket: Operation, state: AuthState, clearTokens: Boolean = true) {
        if (change(ticket) {
            if (clearTokens) tokenStorage.clear()
            pendingJob = null; _state.value = state
            _firebaseCleanupPending.value = firebaseAuthSession != null
        }) cleanupIfCurrent(ticket.epoch)
    }
    private suspend fun synchronizeFirebase(ticket: Operation, accessToken: String) {
        val adapter = firebaseAuthSession ?: return
        if (!current(ticket)) return
        val custom = apiClient.firebaseCustomToken(accessToken)
        firebaseEffects.withLock {
            ticket.context.ensureActive()
            if (!current(ticket)) return@withLock
            if (_firebaseCleanupPending.value) withContext(NonCancellable) { cleanupAdapterLocked() }
            // Do not sign another account into an adapter whose cleanup is uncertain.
            // HTTP authentication semantics remain independent of SDK synchronization.
            if (_firebaseCleanupPending.value || !current(ticket)) return@withLock
            if (custom !is AuthApiResult.Success) {
                withContext(NonCancellable) { cleanupAdapterLocked() }; return@withLock
            }
            try {
                // Keep the effect lane until completion even if its caller is cancelled.
                // This lets us compensate an eventual stale adapter success before releasing it.
                val signedIn = withContext(NonCancellable) { adapter.signInWithCustomToken(custom.value) }
                if (!signedIn || !current(ticket) || !ticket.context.isActive)
                    withContext(NonCancellable) { cleanupAdapterLocked() }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { cleanupAdapterLocked() }; throw cancelled
            } catch (_: Exception) {
                withContext(NonCancellable) { cleanupAdapterLocked() }
            }
        }
    }
    private suspend fun publishUser(ticket: Operation, access: String, user: AuthUser, tokens: AuthTokens? = null, isNewUser: Boolean = false, storageError: String = "Không thể lưu phiên đăng nhập.") {
        if (!current(ticket)) return
        synchronizeFirebase(ticket, access)
        var saveFailed = false
        if (change(ticket) {
            if (tokens != null && !tokenStorage.save(tokens.accessToken, tokens.refreshToken)) {
                tokenStorage.clear(); _state.value = AuthState.Error(storageError)
                _firebaseCleanupPending.value = firebaseAuthSession != null; saveFailed = true
            } else _state.value = AuthState.Authenticated(user, isNewUser)
            pendingJob = null
        } && saveFailed) cleanupIfCurrent(ticket.epoch)
    }
    private suspend fun authenticate(storageError: String, newUser: (AuthTokens) -> Boolean, fetch: suspend () -> AuthApiResult<AuthTokens>) {
        operation(clearTokens = true) { ticket ->
            when (val result = fetch()) {
                is AuthApiResult.Failure -> fail(ticket, AuthState.Error(result.error.userMessage()))
                is AuthApiResult.Success -> if (current(ticket)) {
                    when (val me = apiClient.me(result.value.accessToken)) {
                        is AuthApiResult.Failure -> fail(ticket, AuthState.Error(me.error.userMessage()))
                        is AuthApiResult.Success -> publishUser(ticket, result.value.accessToken, me.value, result.value, newUser(result.value), storageError)
                    }
                }
            }
        }
    }
    suspend fun register(fullName: String, email: String, phone: String, password: String) =
        authenticate("Không thể lưu phiên đăng ký.", { true }) { apiClient.register(fullName, email, phone, password) }
    suspend fun login(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            operation(clearTokens = true) { fail(it, AuthState.Error("Vui lòng nhập email/số điện thoại và mật khẩu.")) }
            return
        }
        authenticate("Không thể lưu phiên đăng nhập.", { false }) { apiClient.login(identifier.trim(), password) }
    }
    suspend fun googleLogin(firebaseIdToken: String) =
        authenticate("Không thể lưu phiên đăng nhập.", { it.isNewUser }) { apiClient.googleLogin(firebaseIdToken) }
    suspend fun restoreSession() {
        operation(clearTokens = false) { ticket ->
            val access = ticket.access?.takeIf { it.isNotBlank() }
            if (access == null) { fail(ticket, AuthState.Unauthenticated); return@operation }
            when (val me = apiClient.me(access)) {
                is AuthApiResult.Success -> publishUser(ticket, access, me.value)
                is AuthApiResult.Failure -> if (me.error.kind == AuthApiErrorKind.UNAUTHORIZED) refreshOnce(ticket)
                    else fail(ticket, AuthState.Error("Không thể khôi phục phiên đăng nhập."), clearTokens = false)
            }
        }
    }
    private suspend fun refreshOnce(ticket: Operation) {
        if (!current(ticket)) return
        val refresh = ticket.refresh?.takeIf { it.isNotBlank() }
        if (refresh == null) { fail(ticket, AuthState.Unauthenticated); return }
        when (val result = apiClient.refresh(refresh)) {
            is AuthApiResult.Failure -> fail(ticket, AuthState.Unauthenticated)
            is AuthApiResult.Success -> if (current(ticket)) {
                when (val me = apiClient.me(result.value.accessToken)) {
                    is AuthApiResult.Success -> publishUser(ticket, result.value.accessToken, me.value, result.value)
                    is AuthApiResult.Failure -> fail(ticket, AuthState.Unauthenticated)
                }
            }
        }
    }
    suspend fun logout() {
        val revoked = synchronized(lock) { revokeLocked() }
        cleanupIfCurrent(revoked)
    }
    suspend fun updateRole(role: String): Result<Unit> {
        return operation(clearTokens = false, loading = false) { ticket ->
            val access = ticket.access?.takeIf { it.isNotBlank() }
                ?: return@operation Result.failure<Unit>(Exception("Phiên đăng nhập không hợp lệ."))
            when (val result = apiClient.updateRole(access, role)) {
                is AuthApiResult.Success -> if (change(ticket) { pendingJob = null; _state.value = AuthState.Authenticated(result.value) }) Result.success(Unit)
                    else Result.failure(Exception("Phiên đăng nhập không hợp lệ."))
                is AuthApiResult.Failure -> Result.failure(Exception(result.error.userMessage()))
            }
        } ?: Result.failure(Exception("Phiên đăng nhập không hợp lệ."))
    }
    suspend fun forgotPassword(identifier: String): Result<Unit> = when (val result = apiClient.forgotPassword(identifier.trim())) {
        is AuthApiResult.Success -> Result.success(Unit)
        is AuthApiResult.Failure -> Result.failure(Exception(result.error.userMessage()))
    }
    suspend fun resetPassword(identifier: String, otp: String, newPassword: String): Result<Unit> = when (val result = apiClient.resetPassword(identifier.trim(), otp.trim(), newPassword)) {
        is AuthApiResult.Success -> Result.success(Unit)
        is AuthApiResult.Failure -> Result.failure(Exception(result.error.userMessage()))
    }
}
