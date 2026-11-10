package com.example.bai1.presentation.navigation

import com.example.bai1.auth.session.AuthState

/** A revoked/failed session must not leave a protected destination in the restored back stack. */
internal object AuthNavigationPolicy {
    private val publicRoutes = setOf(Routes.SPLASH, Routes.ONBOARDING, Routes.LOGIN, Routes.REGISTER,
        Routes.FORGOT_PASSWORD, Routes.OTP_VERIFICATION, Routes.RESET_PASSWORD, Routes.ABOUT_APP)

    fun canRender(state: AuthState, route: String): Boolean =
        route in publicRoutes || state is AuthState.Authenticated

    fun destination(state: AuthState, currentRoute: String?): String? {
        if (currentRoute == null || state is AuthState.Loading) return null
        return when (state) {
            is AuthState.Authenticated -> when (currentRoute) {
                Routes.SPLASH -> Routes.HOME
                Routes.LOGIN, Routes.REGISTER -> if (state.isNewUser) Routes.ROLE_SELECTION else Routes.HOME
                else -> null
            }
            else -> if (currentRoute !in publicRoutes) Routes.LOGIN else null
        }
    }
}
