package com.example.bai1.uiisolated

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.bai1.presentation.navigation.AppNavScreens
import com.example.bai1.presentation.navigation.Routes
import com.example.bai1.auth.AuthViewModel
import com.example.bai1.data.model.DisasterCategory
import com.example.bai1.data.model.UserRole

/** Only Login uses real production UI here. Every other factory method is explicitly unavailable UI. */
class GraphFixtureScreens(private val app: GraphFixtureApplication) : AppNavScreens {
    @Composable private fun Unavailable(route: String, actions: List<Pair<String, () -> Unit>> = emptyList()) {
        androidx.compose.runtime.SideEffect { app.unavailableRenders.add(route) }
        Column {
            Text("UNAVAILABLE production UI: $route")
            actions.forEach { (label, action) -> TextButton(onClick = action) { Text(label) } }
        }
    }
    @Composable override fun LoginScreen(onLogin: ((identifier: String, password: String) -> Unit)?, onGoogleLogin: ((idToken: String) -> Unit)?, isLoading: Boolean, errorMessage: String?, onRegisterClick: () -> Unit, onForgotPasswordClick: () -> Unit) {
        com.example.bai1.presentation.auth.LoginScreen(onLogin = onLogin, onGoogleLogin = onGoogleLogin,
            onRegisterClick = onRegisterClick, onForgotPasswordClick = onForgotPasswordClick,
            isLoading = isLoading, errorMessage = errorMessage,
            onGoogleSignInClick = { app.credentialLaunches.incrementAndGet() })
    }
    @Composable override fun RoleSelectionScreen(onContinue: (UserRole) -> Unit) {
        Unavailable(Routes.ROLE_SELECTION, listOf("Fixture continue role" to { onContinue(UserRole.USER) }))
    }
    @Composable override fun HomeScreen(onNavigateToWarningDetail: (String) -> Unit, onNavigateToCategory: (DisasterCategory) -> Unit, onNavigateToNotifications: () -> Unit, onNavigateToMap: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        Unavailable(Routes.HOME, listOf("Fixture profile" to { onBottomNavNavigate(Routes.PROFILE) }, "Fixture map" to onNavigateToMap, "Fixture SOS" to { onBottomNavNavigate(Routes.SOS) }, "Fixture rescue" to { onBottomNavNavigate(Routes.MISSION_LIST) }))
    }
    @Composable override fun DisasterMapScreen(onNavigateToDetail: (String) -> Unit, onNavigateToSosDetail: (String) -> Unit, onBottomNavNavigate: (String) -> Unit) {
        Unavailable("DisasterMapScreen")
    }
    @Composable override fun WarningListScreen(onNavigateBack: () -> Unit, onNavigateToDetail: (String) -> Unit) {
        Unavailable("WarningListScreen")
    }
    @Composable override fun StormDetailScreen(onNavigateBack: () -> Unit) {
        Unavailable("StormDetailScreen")
    }
    @Composable override fun FloodDetailScreen(onNavigateBack: () -> Unit, onViewFloodMap: () -> Unit) {
        Unavailable("FloodDetailScreen")
    }
    @Composable override fun VolunteerRegistrationScreen(onNavigateBack: () -> Unit, onNavigateHome: () -> Unit, onNavigateProfile: () -> Unit) {
        Unavailable("VolunteerRegistrationScreen")
    }
    @Composable override fun RescueMissionListScreen(onNavigateToDetail: (String) -> Unit, onNavigateToRequestDetail: (String) -> Unit, onBottomNavNavigate: (String) -> Unit) {
        Unavailable("RescueMissionListScreen")
    }
    @Composable override fun RescueRequestDetailScreen(requestId: String, onNavigateBack: () -> Unit, onNavigateToMissionList: () -> Unit) {
        Unavailable("RescueRequestDetailScreen")
    }
    @Composable override fun SosDetailScreen(eventId: String, onNavigateBack: () -> Unit) {
        Unavailable("SosDetailScreen")
    }
    @Composable override fun MissionDetailScreen(missionId: String, onNavigateBack: () -> Unit, onNavigateToChat: () -> Unit) {
        Unavailable("MissionDetailScreen")
    }
    @Composable override fun RescueChatScreen(onNavigateBack: () -> Unit) {
        Unavailable("RescueChatScreen")
    }
    @Composable override fun GroupListScreen(onNavigateToChat: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        Unavailable("GroupListScreen")
    }
    @Composable override fun GroupChatScreen(onNavigateBack: () -> Unit) {
        Unavailable("GroupChatScreen")
    }
    @Composable override fun SOSScreen(onNavigateBack: () -> Unit, onBottomNavNavigate: (String) -> Unit, onHoldSosSuccess: () -> Unit) {
        Unavailable("SOSScreen")
    }
    @Composable override fun SosRescueRequestScreen(onNavigateBack: () -> Unit, onNavigateToMap: () -> Unit) {
        Unavailable("SosRescueRequestScreen")
    }
    @Composable override fun NotificationScreen(onNavigateBack: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        Unavailable("NotificationScreen")
    }
    @Composable override fun ProfileScreen(onNavigateToSettings: () -> Unit, onNavigateToVolunteerRegister: () -> Unit, onLogout: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        Unavailable(Routes.PROFILE, listOf("Fixture settings" to onNavigateToSettings, "Fixture logout" to onLogout))
    }
    @Composable override fun SettingsScreen(onNavigateBack: () -> Unit, onNavigateToAboutApp: () -> Unit, onLogout: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        Unavailable(Routes.SETTINGS, listOf("Fixture about" to onNavigateToAboutApp, "Fixture logout" to onLogout, "Fixture back" to onNavigateBack))
    }
    @Composable override fun ForgotPasswordScreen(authViewModel: AuthViewModel, onNavigateBack: () -> Unit, onSendCode: (String) -> Unit) {
        Unavailable("ForgotPasswordScreen")
    }
    @Composable override fun OTPScreen(identifier: String, onNavigateBack: () -> Unit, onVerifySuccess: (String) -> Unit) {
        Unavailable("OTPScreen")
    }
    @Composable override fun ResetPasswordScreen(identifier: String, otp: String, authViewModel: AuthViewModel, onNavigateBack: () -> Unit, onResetSuccess: () -> Unit) {
        Unavailable("ResetPasswordScreen")
    }
}
