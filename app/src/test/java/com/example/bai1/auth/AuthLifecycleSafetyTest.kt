package com.example.bai1.auth

import com.example.bai1.auth.network.*
import com.example.bai1.auth.session.*
import com.example.bai1.auth.firebase.FirebaseAuthSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Same deterministic orchestration tests run on JVM and in the USB instrumentation process.
 * Deferred barriers establish ordering; timeouts are deadlock bounds, never race synchronizers.
 */
class AuthLifecycleSafetyTest {
    private class Gate {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        suspend fun waitHere() { entered.complete(Unit); withContext(NonCancellable) { release.await() } }
    }
    private class Store : TokenStorage {
        var access: String? = null; var refresh: String? = null; var failSave = false; var saves = 0
        override fun save(accessToken: String, refreshToken: String): Boolean { access=accessToken; refresh=refreshToken; saves++; return !failSave }
        override fun getAccessToken() = access
        override fun getRefreshToken() = refresh
        override fun clear() { access=null; refresh=null }
        override fun isLoggedIn() = access != null
    }
    private class Cloud : FirebaseAuthSession {
        var signedIn = false; var signOuts = 0; var signIns = 0; var throwSignOut = false; var acceptSignIn = true
        var gate: Gate? = null
        override suspend fun signInWithCustomToken(customToken: String): Boolean {
            signIns++; gate?.waitHere(); signedIn = acceptSignIn; return acceptSignIn
        }
        override suspend fun signOut() { signOuts++; if (throwSignOut) error("Controlled adapter cleanup failure"); signedIn=false }
    }
    private fun tokens(id: String) = AuthTokens("$id-access", "$id-refresh")
    private fun user(id: String) = AuthUser(id.substringBefore('-'), "Fixture", null, null, "USER", "ACTIVE", null)
    private fun failure(kind: AuthApiErrorKind = AuthApiErrorKind.UNAUTHORIZED) = AuthApiResult.Failure(AuthApiError(kind,"Controlled failure"))
    private inner class Api : AuthApiClient {
        var loginHandler: suspend (String) -> AuthApiResult<AuthTokens> = { AuthApiResult.Success(tokens(it)) }
        var meHandler: suspend (String) -> AuthApiResult<AuthUser> = { AuthApiResult.Success(user(it)) }
        var refreshHandler: suspend () -> AuthApiResult<AuthTokens> = { AuthApiResult.Success(tokens("fresh")) }
        override suspend fun login(identifier: String, password: String) = loginHandler(identifier)
        override suspend fun register(fullName: String, email: String, phone: String, password: String) = loginHandler(email)
        override suspend fun googleLogin(idToken: String) = loginHandler(idToken)
        override suspend fun me(accessToken: String) = meHandler(accessToken)
        override suspend fun refresh(refreshToken: String) = refreshHandler()
        override suspend fun firebaseCustomToken(accessToken: String) = AuthApiResult.Success("controlled-custom")
        override suspend fun updateRole(accessToken: String, role: String) = meHandler(accessToken)
        override suspend fun forgotPassword(identifier: String) = AuthApiResult.Success(Unit)
        override suspend fun resetPassword(identifier: String, otp: String, newPassword: String) = AuthApiResult.Success(Unit)
    }
    private inner class Rig {
        val api=Api(); val store=Store(); val cloud=Cloud(); var invalidations=0
        val repo=AuthRepository(api,store,cloud) { invalidations++ }
        suspend fun login(id: String = "a") { repo.login(id,"controlled-password") }
        fun assertLoggedOut() {
            assertTrue(repo.state.value is AuthState.Unauthenticated)
            assertTrue(store.access==null && store.refresh==null)
        }
        fun assertUser(id: String) { assertEquals(id,(repo.state.value as? AuthState.Authenticated)?.user?.id) }
    }
    @Test fun lateLoginAfterLogoutCannotSaveOrAuthenticate() = runBlocking { withTimeout(5000) {
        val r=Rig(); val g=Gate(); r.api.loginHandler={g.waitHere();AuthApiResult.Success(tokens("a"))}
        val a=async(start=CoroutineStart.UNDISPATCHED){r.login()}; g.entered.await(); r.repo.logout(); val count=r.invalidations
        g.release.complete(Unit);a.await();r.assertLoggedOut();assertEquals(count,r.invalidations)
    } }
    @Test fun lateRefreshAfterLogoutCannotSave() = runBlocking { withTimeout(5000) {
        val r=Rig();r.store.save("expired-access","old-refresh");r.api.meHandler={if(it.startsWith("expired"))failure()else AuthApiResult.Success(user(it))};val g=Gate()
        r.api.refreshHandler={g.waitHere();AuthApiResult.Success(tokens("fresh"))}
        val a=async(start=CoroutineStart.UNDISPATCHED){r.repo.restoreSession()};g.entered.await();r.repo.logout();val before=r.store.saves;g.release.complete(Unit);a.await();r.assertLoggedOut();assertEquals(before,r.store.saves)
    } }
    @Test fun lateRestoreAfterLogoutCannotPublishUser() = runBlocking { withTimeout(5000) {
        val r=Rig();r.store.save("old-access","old-refresh");val g=Gate();r.api.meHandler={g.waitHere();AuthApiResult.Success(user("old"))}
        val a=async(start=CoroutineStart.UNDISPATCHED){r.repo.restoreSession()};g.entered.await();r.repo.logout();g.release.complete(Unit);a.await();r.assertLoggedOut()
    } }
    @Test fun newestLoginWinsReverseCompletionOrder() = runBlocking { withTimeout(5000) {
        val r=Rig();val g=Gate();r.api.loginHandler={if(it=="a")g.waitHere();AuthApiResult.Success(tokens(it))}
        val a=async(start=CoroutineStart.UNDISPATCHED){r.login("a")};g.entered.await();r.login("b");g.release.complete(Unit);a.await();r.assertUser("b");assertTrue(r.store.access?.startsWith("b-")==true)
    } }
    @Test fun staleFailureCannotOverwriteNewLoginSuccess() = runBlocking { withTimeout(5000) {
        val r=Rig();val g=Gate();r.api.loginHandler={if(it=="a"){g.waitHere();failure()}else AuthApiResult.Success(tokens(it))}
        val a=async(start=CoroutineStart.UNDISPATCHED){r.login("a")};g.entered.await();r.login("b");g.release.complete(Unit);a.await();r.assertUser("b")
    } }
    @Test fun oldRefreshCannotOverwriteNewLogin() = runBlocking { withTimeout(5000) {
        val r=Rig();r.store.save("expired-access","old-refresh");r.api.meHandler={if(it.startsWith("expired"))failure()else AuthApiResult.Success(user(it))};val g=Gate()
        r.api.refreshHandler={g.waitHere();AuthApiResult.Success(tokens("old"))}
        val a=async(start=CoroutineStart.UNDISPATCHED){r.repo.restoreSession()};g.entered.await();r.login("b");g.release.complete(Unit);a.await();r.assertUser("b")
    } }
    @Test fun nonCooperativeCancelledLoginCannotCommit() = runBlocking { withTimeout(5000) {
        val r=Rig();val g=Gate();r.api.loginHandler={g.waitHere();AuthApiResult.Success(tokens("a"))}
        val a=launch(start=CoroutineStart.UNDISPATCHED){r.login()};g.entered.await();a.cancel();g.release.complete(Unit);a.join();r.assertLoggedOut()
    } }
    @Test fun cancelledOldRequestCannotClearNewSession() = runBlocking { withTimeout(5000) {
        val r=Rig();val g=Gate();r.api.loginHandler={if(it=="a")g.waitHere();AuthApiResult.Success(tokens(it))}
        val a=launch(start=CoroutineStart.UNDISPATCHED){r.login("a")};g.entered.await();r.login("b");a.cancel();g.release.complete(Unit);a.join();r.assertUser("b")
    } }
    @Test fun logoutPublishesUnauthenticatedEvenWhenAdapterThrows() = runBlocking {
        val r=Rig();r.login();r.cloud.throwSignOut=true
        val outcome=runCatching{r.repo.logout()};r.assertLoggedOut();assertTrue(outcome.isSuccess)
    }
    @Test fun refreshRejectedCleansAdapterAndStorage() = runBlocking {
        val r=Rig();r.login();r.api.meHandler={failure()};r.api.refreshHandler={failure()};r.repo.restoreSession();r.assertLoggedOut();assertFalse(r.cloud.signedIn)
    }
    @Test fun missingSavedTokenCleansOldAdapterSession() = runBlocking {
        val r=Rig();r.cloud.signedIn=true;r.repo.restoreSession();r.assertLoggedOut();assertFalse(r.cloud.signedIn)
    }
    @Test fun repeatedLogoutIsSafeAndRetriesCleanup() = runBlocking {
        val r=Rig();r.login();r.repo.logout();r.repo.logout();r.assertLoggedOut();assertFalse(r.cloud.signedIn);assertTrue(r.cloud.signOuts>=2)
    }
    @Test fun pendingAdapterSignInCannotSurviveLogout() = runBlocking { withTimeout(5000) {
        val r=Rig();val g=Gate();r.cloud.gate=g
        val a=async(start=CoroutineStart.UNDISPATCHED){r.login()};g.entered.await()
        val out=async(start=CoroutineStart.UNDISPATCHED){r.repo.logout()}
        val locallyRevoked=r.repo.state.value is AuthState.Unauthenticated && !r.store.isLoggedIn()
        g.release.complete(Unit);a.await();out.await();assertTrue(locallyRevoked);r.assertLoggedOut();assertFalse(r.cloud.signedIn)
    } }
    @Test fun failedDurableStorageNeverExposesAuthenticated() = runBlocking {
        val r=Rig();r.store.failSave=true;r.login();assertFalse(r.repo.state.value is AuthState.Authenticated);assertFalse(r.store.isLoggedIn());assertFalse(r.cloud.signedIn)
    }
    @Test fun accountSwitchRevokesPriorAdapterBeforeNewCredentials() = runBlocking {
        val r=Rig();r.login("a");val before=r.cloud.signOuts;r.login("b");r.assertUser("b");assertTrue(r.cloud.signOuts>before)
    }
    @Test fun refreshErrorsNeverRetainAdapterSession() = runBlocking {
        for(kind in listOf(AuthApiErrorKind.UNAUTHORIZED,AuthApiErrorKind.FORBIDDEN,AuthApiErrorKind.TIMEOUT,AuthApiErrorKind.NO_INTERNET)) {
            val r=Rig();r.login();r.api.meHandler={failure()};r.api.refreshHandler={failure(kind)};r.repo.restoreSession();r.assertLoggedOut();assertFalse(r.cloud.signedIn)
        }
    }
    @Test fun cleanupFailureIsObservableAndPreventsAnotherAdapterSignIn() = runBlocking {
        val r=Rig();r.login("a");val count=r.cloud.signIns;r.cloud.throwSignOut=true;r.login("b")
        r.assertUser("b");assertTrue(r.repo.firebaseCleanupPending.value);assertEquals(count,r.cloud.signIns)
        r.repo.logout();r.assertLoggedOut();assertTrue(r.repo.firebaseCleanupPending.value)
        r.cloud.throwSignOut=false;r.repo.logout();assertFalse(r.repo.firebaseCleanupPending.value);assertFalse(r.cloud.signedIn)
    }
    @Test fun adapterSignInRejectionKeepsHttpSemanticsButCleansAdapter() = runBlocking {
        val r=Rig();r.cloud.acceptSignIn=false;r.login();r.assertUser("a")
        assertFalse(r.cloud.signedIn);assertTrue(r.cloud.signOuts>0);assertFalse(r.repo.firebaseCleanupPending.value)
    }
    @Test fun lateRoleUpdateCannotRestoreLoggedOutUser() = runBlocking { withTimeout(5000) {
        val r=Rig();r.login();val g=Gate();r.api.meHandler={g.waitHere();AuthApiResult.Success(user("a"))}
        val a=async(start=CoroutineStart.UNDISPATCHED){r.repo.updateRole("USER")};g.entered.await();r.repo.logout();g.release.complete(Unit)
        assertTrue(a.await().isFailure);r.assertLoggedOut()
    } }
    @Test fun newerAccountWaitsForStaleAdapterCompensation() = runBlocking { withTimeout(5000) {
        val r=Rig();val g=Gate();r.cloud.gate=g
        val a=async(start=CoroutineStart.UNDISPATCHED){r.login("a")};g.entered.await();r.cloud.gate=null
        val b=async(start=CoroutineStart.UNDISPATCHED){r.login("b")};g.release.complete(Unit);a.await();b.await()
        r.assertUser("b");assertTrue(r.cloud.signedIn);assertEquals(2,r.cloud.signIns);assertFalse(r.repo.firebaseCleanupPending.value)
    } }
}
