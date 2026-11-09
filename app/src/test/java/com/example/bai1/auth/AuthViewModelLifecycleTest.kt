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
class AuthViewModelLifecycleTest {
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
        override suspend fun updateRole(accessToken:String,role:String)=me(accessToken)
        override suspend fun forgotPassword(identifier:String)=AuthApiResult.Success(Unit)
        override suspend fun resetPassword(identifier:String,otp:String,newPassword:String)=AuthApiResult.Success(Unit)
    }
    @Test fun clearedViewModelRevokesPendingNonCooperativeRequest()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api=Api();val storage=Storage();val repo=AuthRepository(api,storage,invalidateRoutingSession={})
        val store=ViewModelStore()
        try {
            val vm=ViewModelProvider(store,AuthViewModel.factory(repo))[AuthViewModel::class.java];runCurrent()
            vm.login("controlled-user","controlled-password");runCurrent();api.entered.await()
            store.clear();val immediate=repo.state.value is AuthState.Unauthenticated
            api.release.complete(Unit);advanceUntilIdle()
            assertTrue(immediate);assertTrue(repo.state.value is AuthState.Unauthenticated);assertFalse(storage.isLoggedIn())
        } finally { api.release.complete(Unit);store.clear();Dispatchers.resetMain() }
    }
    @Test fun viewModelLogoutWinsItsPendingLogin()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api=Api();val storage=Storage();val repo=AuthRepository(api,storage,invalidateRoutingSession={});val store=ViewModelStore()
        try {
            val vm=ViewModelProvider(store,AuthViewModel.factory(repo))[AuthViewModel::class.java];runCurrent()
            vm.login("controlled-user","controlled-password");runCurrent();api.entered.await()
            vm.logout();runCurrent();api.release.complete(Unit);advanceUntilIdle()
            assertTrue(repo.state.value is AuthState.Unauthenticated);assertFalse(storage.isLoggedIn())
        } finally { api.release.complete(Unit);store.clear();Dispatchers.resetMain() }
    }
    @Test fun clearingOldViewModelCannotRevokeNewViewModelOperation()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api=Api();val storage=Storage();val repo=AuthRepository(api,storage,invalidateRoutingSession={})
        val first=ViewModelStore();val second=ViewModelStore()
        try {
            val a=ViewModelProvider(first,AuthViewModel.factory(repo))[AuthViewModel::class.java];runCurrent()
            a.login("controlled-user","controlled-password");runCurrent();api.entered.await()
            val b=ViewModelProvider(second,AuthViewModel.factory(repo))[AuthViewModel::class.java];runCurrent()
            b.login("controlled-user","controlled-password");runCurrent();first.clear()
            assertTrue(repo.state.value is AuthState.Loading)
            api.release.complete(Unit);advanceUntilIdle()
            assertTrue(repo.state.value is AuthState.Authenticated);assertTrue(storage.isLoggedIn())
        } finally { api.release.complete(Unit);first.clear();second.clear();Dispatchers.resetMain() }
    }
}
