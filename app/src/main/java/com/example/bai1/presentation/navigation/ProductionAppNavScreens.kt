package com.example.bai1.presentation.navigation

import androidx.compose.runtime.Composable
import com.example.bai1.auth.AuthViewModel
import com.example.bai1.data.model.DisasterCategory
import com.example.bai1.data.model.UserRole

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import com.example.bai1.sos.SosAppDependencies
import com.example.bai1.rescue.RescueMissionAppDependencies

/** Production defaults. Cloud dependencies are constructed only on entry to a dependent destination. */
internal class ProductionAppNavScreens(private val context: Context) : AppNavScreens {
    private fun ensureRescueDependencies() {
        SosAppDependencies.initialize(context)
        RescueMissionAppDependencies.initialize(context)
    }
    @Composable override fun LoginScreen(
        onLogin: ((identifier: String, password: String) -> Unit)?,
        onGoogleLogin: ((idToken: String) -> Unit)?,
        isLoading: Boolean,
        errorMessage: String?,
        onRegisterClick: () -> Unit,
        onForgotPasswordClick: () -> Unit
    ) {
        com.example.bai1.presentation.auth.LoginScreen(onLogin = onLogin, onGoogleLogin = onGoogleLogin, isLoading = isLoading, errorMessage = errorMessage, onRegisterClick = onRegisterClick, onForgotPasswordClick = onForgotPasswordClick)
    }
    @Composable override fun RoleSelectionScreen(
        onContinue: (UserRole) -> Unit
    ) {
        com.example.bai1.presentation.auth.RoleSelectionScreen(onContinue = onContinue)
    }
    @Composable override fun HomeScreen(
        onNavigateToWarningDetail: (String) -> Unit,
        onNavigateToCategory: (DisasterCategory) -> Unit,
        onNavigateToNotifications: () -> Unit,
        onNavigateToMap: () -> Unit,
        onBottomNavNavigate: (String) -> Unit
    ) {
        com.example.bai1.presentation.home.HomeScreen(onNavigateToWarningDetail = onNavigateToWarningDetail, onNavigateToCategory = onNavigateToCategory, onNavigateToNotifications = onNavigateToNotifications, onNavigateToMap = onNavigateToMap, onBottomNavNavigate = onBottomNavNavigate)
    }
    @Composable override fun DisasterMapScreen(
        onNavigateToDetail: (String) -> Unit,
        onNavigateToSosDetail: (String) -> Unit,
        onBottomNavNavigate: (String) -> Unit
    ) {
        ensureRescueDependencies()
        com.example.bai1.presentation.map.DisasterMapScreen(viewModel = viewModel(factory = com.example.bai1.presentation.map.DisasterMapViewModelFactory(SosAppDependencies.sosRepository, RescueMissionAppDependencies.rescueMissionRepository, context.applicationContext)), locationProvider = remember { com.example.bai1.presentation.location.AndroidLocationProvider(context) }, onNavigateToDetail = onNavigateToDetail, onNavigateToSosDetail = onNavigateToSosDetail, onBottomNavNavigate = onBottomNavNavigate)
    }
    @Composable override fun WarningListScreen(
        onNavigateBack: () -> Unit,
        onNavigateToDetail: (String) -> Unit
    ) {
        com.example.bai1.presentation.warning.WarningListScreen(onNavigateBack = onNavigateBack, onNavigateToDetail = onNavigateToDetail)
    }
    @Composable override fun StormDetailScreen(
        onNavigateBack: () -> Unit
    ) {
        com.example.bai1.presentation.warning.StormDetailScreen(onNavigateBack = onNavigateBack)
    }
    @Composable override fun FloodDetailScreen(
        onNavigateBack: () -> Unit,
        onViewFloodMap: () -> Unit
    ) {
        com.example.bai1.presentation.warning.FloodDetailScreen(onNavigateBack = onNavigateBack, onViewFloodMap = onViewFloodMap)
    }
    @Composable override fun VolunteerRegistrationScreen(
        onNavigateBack: () -> Unit,
        onNavigateHome: () -> Unit,
        onNavigateProfile: () -> Unit
    ) {
        com.example.bai1.presentation.rescue.VolunteerRegistrationScreen(onNavigateBack = onNavigateBack, onNavigateHome = onNavigateHome, onNavigateProfile = onNavigateProfile)
    }
    @Composable override fun RescueMissionListScreen(
        onNavigateToDetail: (String) -> Unit,
        onNavigateToRequestDetail: (String) -> Unit,
        onBottomNavNavigate: (String) -> Unit
    ) {
        ensureRescueDependencies()
        com.example.bai1.presentation.rescue.RescueMissionListScreen(onNavigateToDetail = onNavigateToDetail, onNavigateToRequestDetail = onNavigateToRequestDetail, onBottomNavNavigate = onBottomNavNavigate)
    }
    @Composable override fun RescueRequestDetailScreen(
        requestId: String,
        onNavigateBack: () -> Unit,
        onNavigateToMissionList: () -> Unit
    ) {
        ensureRescueDependencies()
        com.example.bai1.presentation.rescue.RescueRequestDetailScreen(requestId = requestId, onNavigateBack = onNavigateBack, onNavigateToMissionList = onNavigateToMissionList)
    }
    @Composable override fun SosDetailScreen(
        eventId: String,
        onNavigateBack: () -> Unit
    ) {
        ensureRescueDependencies()
        com.example.bai1.presentation.sos.SosDetailScreen(eventId = eventId, onNavigateBack = onNavigateBack)
    }
    @Composable override fun MissionDetailScreen(
        missionId: String,
        onNavigateBack: () -> Unit,
        onNavigateToChat: () -> Unit,
        onNavigateToMap: (() -> Unit)?
    ) {
        ensureRescueDependencies()
        com.example.bai1.presentation.rescue.MissionDetailScreen(
            missionId = missionId,
            onNavigateBack = onNavigateBack,
            onNavigateToChat = onNavigateToChat,
            onNavigateToMap = onNavigateToMap
        )
    }
    @Composable override fun RescueChatScreen(
        onNavigateBack: () -> Unit
    ) {
        ensureRescueDependencies()
        com.example.bai1.presentation.rescue.RescueChatScreen(onNavigateBack = onNavigateBack)
    }
    @Composable override fun GroupListScreen(
        onNavigateToChat: () -> Unit,
        onBottomNavNavigate: (String) -> Unit
    ) {
        ensureRescueDependencies()
        com.example.bai1.presentation.rescue.GroupListScreen(onNavigateToChat = onNavigateToChat, onBottomNavNavigate = onBottomNavNavigate)
    }
    @Composable override fun GroupChatScreen(
        onNavigateBack: () -> Unit
    ) {
        ensureRescueDependencies()
        com.example.bai1.presentation.rescue.GroupChatScreen(onNavigateBack = onNavigateBack)
    }
    @Composable override fun SOSScreen(
        onNavigateBack: () -> Unit,
        onBottomNavNavigate: (String) -> Unit,
        onHoldSosSuccess: () -> Unit
    ) {
        ensureRescueDependencies()
        com.example.bai1.presentation.sos.SOSScreen(locationViewModel = viewModel(factory = com.example.bai1.presentation.location.CurrentLocationViewModelFactory(context)), onNavigateBack = onNavigateBack, onBottomNavNavigate = onBottomNavNavigate, onHoldSosSuccess = onHoldSosSuccess)
    }
    @Composable override fun SosRescueRequestScreen(
        onNavigateBack: () -> Unit,
        onNavigateToMap: () -> Unit
    ) {
        ensureRescueDependencies()
        com.example.bai1.presentation.sos.SosRescueRequestScreen(viewModelOverride = viewModel(factory = com.example.bai1.presentation.sos.SosRescueRequestViewModelFactory(context)), onNavigateBack = onNavigateBack, onNavigateToMap = onNavigateToMap)
    }
    @Composable override fun NotificationScreen(
        onNavigateBack: () -> Unit,
        onBottomNavNavigate: (String) -> Unit
    ) {
        com.example.bai1.presentation.notification.NotificationScreen(onNavigateBack = onNavigateBack, onBottomNavNavigate = onBottomNavNavigate)
    }
    @Composable override fun ProfileScreen(
        onNavigateToSettings: () -> Unit,
        onNavigateToVolunteerRegister: () -> Unit,
        onLogout: () -> Unit,
        onBottomNavNavigate: (String) -> Unit
    ) {
        com.example.bai1.presentation.profile.ProfileScreen(onNavigateToSettings = onNavigateToSettings, onNavigateToVolunteerRegister = onNavigateToVolunteerRegister, onLogout = onLogout, onBottomNavNavigate = onBottomNavNavigate)
    }
    @Composable override fun SettingsScreen(
        onNavigateBack: () -> Unit,
        onNavigateToAboutApp: () -> Unit,
        onLogout: () -> Unit,
        onBottomNavNavigate: (String) -> Unit
    ) {
        com.example.bai1.presentation.settings.SettingsScreen(onNavigateBack = onNavigateBack, onNavigateToAboutApp = onNavigateToAboutApp, onLogout = onLogout, onBottomNavNavigate = onBottomNavNavigate)
    }
    @Composable override fun ForgotPasswordScreen(
        authViewModel: AuthViewModel,
        onNavigateBack: () -> Unit,
        onSendCode: (String) -> Unit
    ) {
        com.example.bai1.presentation.auth.ForgotPasswordScreen(authViewModel = authViewModel, onNavigateBack = onNavigateBack, onSendCode = onSendCode)
    }
    @Composable override fun OTPScreen(
        identifier: String,
        onNavigateBack: () -> Unit,
        onVerifySuccess: (String) -> Unit
    ) {
        com.example.bai1.presentation.auth.OTPScreen(identifier = identifier, onNavigateBack = onNavigateBack, onVerifySuccess = onVerifySuccess)
    }
    @Composable override fun ResetPasswordScreen(
        identifier: String,
        otp: String,
        authViewModel: AuthViewModel,
        onNavigateBack: () -> Unit,
        onResetSuccess: () -> Unit
    ) {
        com.example.bai1.presentation.auth.ResetPasswordScreen(identifier = identifier, otp = otp, authViewModel = authViewModel, onNavigateBack = onNavigateBack, onResetSuccess = onResetSuccess)
    }
}
