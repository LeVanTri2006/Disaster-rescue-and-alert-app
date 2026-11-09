package com.example.bai1.auth

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.bai1.auth.network.*
import com.example.bai1.auth.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoleSelectionViewModelTest {
    private class Storage : TokenStorage {
        var access: String? = null; var refresh: String? = null
        override fun save(accessToken: String, refreshToken: String): Boolean { access=accessToken;refresh=refreshToken;return true }
        override fun getAccessToken()=access
        override fun getRefreshToken()=refresh
        override fun clear(){access=null;refresh=null}
        override fun isLoggedIn()=access!=null
    }
    private class Api : AuthApiClient {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        override suspend fun login(identifier: String,password: String):AuthApiResult<AuthTokens> {
            entered.complete(Unit);withContext(NonCancellable){release.await()};return AuthApiResult.Success(AuthTokens("controlled-access","controlled-refresh"))
        }
        override suspend fun me(accessToken:String)=AuthApiResult.Success(AuthUser("controlled-user",null,null,null,"USER","ACTIVE",null))
        override suspend fun refresh(refreshToken:String)=AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNAUTHORIZED,"controlled"))
        override suspend fun register(fullName:String,email:String,phone:String,password:String)=login(email,password)
        override suspend fun googleLogin(idToken:String)=login(idToken,"")
        var reject = false
        override suspend fun updateRole(accessToken:String,role:String): AuthApiResult<AuthUser> {
            entered.complete(Unit); withContext(NonCancellable) { release.await() }
            return if (reject) AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.FORBIDDEN,"controlled")) else me(accessToken)
        }
        override suspend fun forgotPassword(identifier:String)=AuthApiResult.Success(Unit)
        override suspend fun resetPassword(identifier:String,otp:String,newPassword:String)=AuthApiResult.Success(Unit)
    }

    @Test fun submittedRoleRemainsLoadingUntilRepositoryAccepts() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api=Api(); val storage=Storage(); storage.save("controlled-access","controlled-refresh")
        val repo=AuthRepository(api,storage,invalidateRoutingSession={}); repo.restoreSession()
        val store=ViewModelStore()
        try {
            val vm=ViewModelProvider(store,RoleSelectionViewModel.factory(repo))[RoleSelectionViewModel::class.java]
            vm.selectRole("USER"); assertTrue(vm.uiState.value is RoleSelectionState.Loading)
            runCurrent(); api.entered.await(); assertTrue(vm.uiState.value is RoleSelectionState.Loading)
            api.release.complete(Unit); advanceUntilIdle()
            assertTrue(vm.uiState.value is RoleSelectionState.Success)
            assertEquals("USER", (repo.state.value as AuthState.Authenticated).user.role)
        } finally { api.release.complete(Unit); store.clear(); Dispatchers.resetMain() }
    }
    @Test fun serverRefusalIsAnErrorAndNeverAnElevatedAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api=Api(); api.reject=true; api.release.complete(Unit)
        val storage=Storage(); storage.save("controlled-access","controlled-refresh")
        val repo=AuthRepository(api,storage,invalidateRoutingSession={}); repo.restoreSession(); val store=ViewModelStore()
        try {
            val vm=ViewModelProvider(store,RoleSelectionViewModel.factory(repo))[RoleSelectionViewModel::class.java]
            vm.selectRole("RESCUER"); advanceUntilIdle()
            assertTrue(vm.uiState.value is RoleSelectionState.Error)
            assertEquals("USER", (repo.state.value as AuthState.Authenticated).user.role)
            vm.resetState(); assertTrue(vm.uiState.value is RoleSelectionState.Idle)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun roleCompletionAfterLogoutCannotPublishSuccessOrUser() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api=Api(); val storage=Storage(); storage.save("controlled-access","controlled-refresh")
        val repo=AuthRepository(api,storage,invalidateRoutingSession={}); repo.restoreSession(); val store=ViewModelStore()
        try {
            val vm=ViewModelProvider(store,RoleSelectionViewModel.factory(repo))[RoleSelectionViewModel::class.java]
            vm.selectRole("USER"); runCurrent(); api.entered.await(); repo.logout()
            api.release.complete(Unit); advanceUntilIdle()
            assertTrue(vm.uiState.value is RoleSelectionState.Error)
            assertTrue(repo.state.value is AuthState.Unauthenticated); assertFalse(storage.isLoggedIn())
        } finally { api.release.complete(Unit); store.clear(); Dispatchers.resetMain() }
    }
}
