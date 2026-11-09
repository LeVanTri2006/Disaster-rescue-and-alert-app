package com.example.bai1.presentation.navigation

import com.example.bai1.auth.session.*
import org.junit.Test
import org.junit.Assert.*

class AuthNavigationPolicyTest {
    private val user = AuthUser("id", null, null, null, "USER", null, null)
    @Test fun revokedSessionLeavesEveryProtectedDestination() {
        for (route in listOf(Routes.HOME, Routes.PROFILE, Routes.SETTINGS, Routes.MAP, Routes.SOS,
            Routes.MISSION_LIST, Routes.ROLE_SELECTION, Routes.SOS_DETAIL)) {
            assertEquals(route, Routes.LOGIN, AuthNavigationPolicy.destination(AuthState.Unauthenticated, route))
            assertEquals(route, Routes.LOGIN, AuthNavigationPolicy.destination(AuthState.Error("expired"), route))
        }
    }
    @Test fun pendingRestoreDoesNotExposeOrRedirectAnUnresolvedSession() {
        assertNull(AuthNavigationPolicy.destination(AuthState.Loading, Routes.HOME))
        assertFalse(AuthNavigationPolicy.canRender(AuthState.Loading, Routes.HOME))
        assertFalse(AuthNavigationPolicy.canRender(AuthState.Unauthenticated, Routes.MAP))
        assertTrue(AuthNavigationPolicy.canRender(AuthState.Authenticated(user), Routes.HOME))
        assertNull(AuthNavigationPolicy.destination(AuthState.Unauthenticated, null))
    }
    @Test fun publicRecoveryAndOnboardingRemainReachableWithoutSession() {
        for (route in listOf(Routes.SPLASH, Routes.ONBOARDING, Routes.LOGIN, Routes.REGISTER,
            Routes.FORGOT_PASSWORD, Routes.OTP_VERIFICATION, Routes.RESET_PASSWORD, Routes.ABOUT_APP)) {
            assertNull(route, AuthNavigationPolicy.destination(AuthState.Unauthenticated, route))
            assertNull(route, AuthNavigationPolicy.destination(AuthState.Error("bad login"), route))
        }
    }
    @Test fun existingAndNewAccountDestinationsPreserveRoleSelectionSemantics() {
        for (route in listOf(Routes.LOGIN, Routes.REGISTER)) {
            assertEquals(Routes.HOME, AuthNavigationPolicy.destination(AuthState.Authenticated(user), route))
            assertEquals(Routes.ROLE_SELECTION, AuthNavigationPolicy.destination(AuthState.Authenticated(user, true), route))
        }
        assertEquals(Routes.HOME, AuthNavigationPolicy.destination(AuthState.Authenticated(user, true), Routes.SPLASH))
        assertNull(AuthNavigationPolicy.destination(AuthState.Authenticated(user), Routes.ABOUT_APP))
        assertNull(AuthNavigationPolicy.destination(AuthState.Authenticated(user), Routes.HOME))
    }
}
