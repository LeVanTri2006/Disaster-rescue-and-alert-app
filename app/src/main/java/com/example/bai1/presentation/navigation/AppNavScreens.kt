package com.example.bai1.presentation.navigation

import androidx.compose.runtime.Composable
import com.example.bai1.auth.AuthViewModel
import com.example.bai1.data.model.DisasterCategory
import com.example.bai1.data.model.UserRole

/** Screen construction only. Route registration and all navigation actions belong to AppNavGraph. */
interface AppNavScreens {
    @Composable fun LoginScreen(
        onLogin: ((identifier: String, password: String) -> Unit)?,
        onGoogleLogin: ((idToken: String) -> Unit)?,
        isLoading: Boolean,
        errorMessage: String?,
        onRegisterClick: () -> Unit,
        onForgotPasswordClick: () -> Unit
    )
    @Composable fun RoleSelectionScreen(
        onContinue: (UserRole) -> Unit
    )
    @Composable fun HomeScreen(
        onNavigateToWarningDetail: (String) -> Unit,
        onNavigateToCategory: (DisasterCategory) -> Unit,
        onNavigateToNotifications: () -> Unit,
        onNavigateToMap: () -> Unit,
        onBottomNavNavigate: (String) -> Unit
    )
    @Composable fun DisasterMapScreen(
        onNavigateToDetail: (String) -> Unit,
        onNavigateToSosDetail: (String) -> Unit,
        onBottomNavNavigate: (String) -> Unit
    )
    @Composable fun WarningListScreen(
        onNavigateBack: () -> Unit,
        onNavigateToDetail: (String) -> Unit
    )
    @Composable fun StormDetailScreen(
        onNavigateBack: () -> Unit
    )
    @Composable fun FloodDetailScreen(
        onNavigateBack: () -> Unit,
        onViewFloodMap: () -> Unit
    )
    @Composable fun VolunteerRegistrationScreen(
        onNavigateBack: () -> Unit,
        onNavigateHome: () -> Unit,
        onNavigateProfile: () -> Unit
    )
    @Composable fun RescueMissionListScreen(
        onNavigateToDetail: (String) -> Unit,
        onNavigateToRequestDetail: (String) -> Unit,
        onBottomNavNavigate: (String) -> Unit
    )
    @Composable fun RescueRequestDetailScreen(
        requestId: String,
        onNavigateBack: () -> Unit,
        onNavigateToMissionList: () -> Unit
    )
    @Composable fun SosDetailScreen(
        eventId: String,
        onNavigateBack: () -> Unit
    )
    @Composable fun MissionDetailScreen(
        missionId: String,
        onNavigateBack: () -> Unit,
        onNavigateToChat: () -> Unit,
        onNavigateToMap: (() -> Unit)? = null
    )
    @Composable fun RescueChatScreen(
        onNavigateBack: () -> Unit
    )
    @Composable fun GroupListScreen(
        onNavigateToChat: () -> Unit,
        onBottomNavNavigate: (String) -> Unit
    )
    @Composable fun GroupChatScreen(
        onNavigateBack: () -> Unit
    )
    @Composable fun SOSScreen(
        onNavigateBack: () -> Unit,
        onBottomNavNavigate: (String) -> Unit,
        onHoldSosSuccess: () -> Unit
    )
    @Composable fun SosRescueRequestScreen(
        onNavigateBack: () -> Unit,
        onNavigateToMap: () -> Unit
    )
    @Composable fun NotificationScreen(
        onNavigateBack: () -> Unit,
        onBottomNavNavigate: (String) -> Unit
    )
    @Composable fun ProfileScreen(
        onNavigateToSettings: () -> Unit,
        onNavigateToVolunteerRegister: () -> Unit,
        onLogout: () -> Unit,
        onBottomNavNavigate: (String) -> Unit
    )
    @Composable fun SettingsScreen(
        onNavigateBack: () -> Unit,
        onNavigateToAboutApp: () -> Unit,
        onLogout: () -> Unit,
        onBottomNavNavigate: (String) -> Unit
    )
    @Composable fun ForgotPasswordScreen(
        authViewModel: AuthViewModel,
        onNavigateBack: () -> Unit,
        onSendCode: (String) -> Unit
    )
    @Composable fun OTPScreen(
        identifier: String,
        onNavigateBack: () -> Unit,
        onVerifySuccess: (String) -> Unit
    )
    @Composable fun ResetPasswordScreen(
        identifier: String,
        otp: String,
        authViewModel: AuthViewModel,
        onNavigateBack: () -> Unit,
        onResetSuccess: () -> Unit
    )
}
