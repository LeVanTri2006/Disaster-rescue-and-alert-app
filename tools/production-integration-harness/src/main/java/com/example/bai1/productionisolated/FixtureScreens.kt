package com.example.bai1.productionisolated

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.bai1.presentation.navigation.AppNavScreens
import com.example.bai1.presentation.navigation.Routes
import com.example.bai1.auth.AuthViewModel
import com.example.bai1.data.model.DisasterCategory
import com.example.bai1.data.model.UserRole

/** Actual production login/home/profile/SOS/form/map. Later phases remain unavailable bindings. */
class FixtureScreens(private val app: ProductionFixtureApplication) : AppNavScreens {
    @Composable private fun Unavailable(route: String, actions: List<Pair<String, () -> Unit>> = emptyList()) {
        
        Column {
            Text("UNAVAILABLE production UI: $route")
            actions.forEach { (label, action) -> TextButton(onClick = action) { Text(label) } }
        }
    }
    @Composable override fun LoginScreen(onLogin: ((identifier: String, password: String) -> Unit)?, onGoogleLogin: ((idToken: String) -> Unit)?, isLoading: Boolean, errorMessage: String?, onRegisterClick: () -> Unit, onForgotPasswordClick: () -> Unit) {
        com.example.bai1.presentation.auth.LoginScreen(onLogin = onLogin, onGoogleLogin = onGoogleLogin,
            onRegisterClick = onRegisterClick, onForgotPasswordClick = onForgotPasswordClick,
            isLoading = isLoading, errorMessage = errorMessage,
            onGoogleSignInClick = { Unit })
    }
    @Composable override fun RoleSelectionScreen(onContinue: (UserRole) -> Unit) {
        Unavailable(Routes.ROLE_SELECTION, listOf("Fixture continue role" to { onContinue(UserRole.USER) }))
    }
    @Composable override fun HomeScreen(onNavigateToWarningDetail: (String) -> Unit, onNavigateToCategory: (DisasterCategory) -> Unit, onNavigateToNotifications: () -> Unit, onNavigateToMap: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        com.example.bai1.presentation.home.HomeScreen(onNavigateToWarningDetail,onNavigateToCategory,onNavigateToNotifications,onNavigateToMap,onBottomNavNavigate)
    }
    @Composable override fun DisasterMapScreen(onNavigateToDetail: (String) -> Unit, onNavigateToSosDetail: (String) -> Unit, onBottomNavNavigate: (String) -> Unit) {
        val model:com.example.bai1.presentation.map.DisasterMapViewModel=androidx.lifecycle.viewmodel.compose.viewModel(factory=app.factory { com.example.bai1.presentation.map.DisasterMapViewModel(app.sessionSos(),app.rescueRepository,pollingEnabled=false,nearbyRequestsEnabled=false) })
        androidx.compose.runtime.SideEffect { app.mapModel=model }
        com.example.bai1.presentation.map.DisasterMapScreen(viewModel=model,locationProvider=app.location,requestDeviceLocation=false,onMapReady={app.sdkMap=it},onTilesLoaded={app.loadedCallbacks++;android.util.Log.i("E10_UI_MAP","onMapLoaded")},onMarkerSelected={m,n->app.selected=m;app.clicked=n},onNavigateToDetail=onNavigateToDetail,onNavigateToSosDetail=onNavigateToSosDetail,onBottomNavNavigate=onBottomNavNavigate)
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
        com.example.bai1.presentation.sos.SOSScreen(onNavigateBack,onBottomNavNavigate,onHoldSosSuccess, androidx.lifecycle.viewmodel.compose.viewModel(factory = app.factory { com.example.bai1.presentation.location.CurrentLocationViewModel(app.location) }), requestDeviceLocation=false)
    }
    @Composable override fun SosRescueRequestScreen(onNavigateBack: () -> Unit, onNavigateToMap: () -> Unit) {
        val model:com.example.bai1.presentation.sos.SosRescueRequestViewModel=androidx.lifecycle.viewmodel.compose.viewModel(factory=app.factory { com.example.bai1.presentation.sos.SosRescueRequestViewModel(app.sessionSos(),app.location,com.example.bai1.sos.provider.FakeBatteryInfoProvider(-1)) })
        androidx.compose.runtime.SideEffect { app.formModel=model }
        com.example.bai1.presentation.sos.SosRescueRequestScreen(onNavigateBack,onNavigateToMap,model,requestDeviceLocation=false,mediaEnabled=false)
    }
    @Composable override fun NotificationScreen(onNavigateBack: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        Unavailable("NotificationScreen")
    }
    @Composable override fun ProfileScreen(onNavigateToSettings: () -> Unit, onNavigateToVolunteerRegister: () -> Unit, onLogout: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        val state by app.repository.state.collectAsState()
        val role by com.example.bai1.data.repository.DisasterRepository.currentUserRole.collectAsState()
        com.example.bai1.presentation.profile.ProfileScreen(onNavigateToSettings,onNavigateToVolunteerRegister,onLogout,onBottomNavNavigate,state,role)
    }
    @Composable override fun SettingsScreen(onNavigateBack: () -> Unit, onNavigateToAboutApp: () -> Unit, onLogout: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        com.example.bai1.presentation.settings.SettingsScreen(onNavigateBack,onNavigateToAboutApp,onLogout,onBottomNavNavigate)
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
