package com.example.bai1.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.bai1.data.model.DisasterCategory
import com.example.bai1.auth.AuthAppDependencies
import com.example.bai1.auth.AuthViewModel
import com.example.bai1.auth.session.AuthState
import com.example.bai1.sos.SosAppDependencies
import com.example.bai1.presentation.auth.*
import com.example.bai1.presentation.home.HomeScreen
import com.example.bai1.presentation.map.DisasterMapScreen
import com.example.bai1.presentation.notification.NotificationScreen
import com.example.bai1.presentation.profile.ProfileScreen
import com.example.bai1.presentation.rescue.RescueChatScreen
import com.example.bai1.presentation.rescue.RescueRequestDetailScreen
import com.example.bai1.presentation.rescue.GroupListScreen
import com.example.bai1.presentation.rescue.GroupChatScreen
import com.example.bai1.presentation.rescue.RescueMissionListScreen
import com.example.bai1.presentation.rescue.MissionDetailScreen
import com.example.bai1.presentation.rescue.VolunteerRegistrationScreen
import com.example.bai1.presentation.settings.AboutAppScreen
import com.example.bai1.presentation.settings.SettingsScreen
import com.example.bai1.presentation.sos.SOSScreen
import com.example.bai1.presentation.sos.SosRescueRequestScreen
import com.example.bai1.presentation.warning.FloodDetailScreen
import com.example.bai1.presentation.warning.StormDetailScreen
import com.example.bai1.presentation.warning.WarningListScreen
import com.example.bai1.data.model.UserRole
import com.example.bai1.data.repository.DisasterRepository

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Routes.SPLASH
) {
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(AuthAppDependencies.repository)
    )
    val authState by authViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(authState) {
        val currentState = authState
        if (currentState is AuthState.Authenticated) {
            // Sync user role
            val roleStr = currentState.user.role ?: "USER"
            val userRole = try { UserRole.valueOf(roleStr.uppercase()) } catch (e: Exception) { UserRole.USER }
            DisasterRepository.setUserRole(userRole)

            when (navController.currentDestination?.route) {
                Routes.SPLASH -> navController.navigate(Routes.HOME) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
                Routes.LOGIN, Routes.REGISTER -> navController.navigate(if (currentState.isNewUser) Routes.ROLE_SELECTION else Routes.HOME) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 1. Splash Screen
        composable(Routes.SPLASH) {
            SplashScreen(
                onNext = {
                    when (authState) {
                        is AuthState.Authenticated -> navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                        AuthState.Loading -> Unit
                        else -> navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }

        // 2, 3, 4. Onboarding Screens
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinishOnboarding = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // 5. Login Screen
        composable(Routes.LOGIN) {
            LoginScreen(
                onLogin = authViewModel::login,
                onGoogleLogin = authViewModel::googleLogin,
                isLoading = authState is AuthState.Loading,
                errorMessage = (authState as? AuthState.Error)?.message,
                onRegisterClick = {
                    navController.navigate(Routes.REGISTER)
                },
                onForgotPasswordClick = {
                    navController.navigate(Routes.FORGOT_PASSWORD)
                }
            )
        }

        // 6. Register Screen
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSubmit = { fullName, email, phone, password ->
                    authViewModel.register(fullName, email, phone, password)
                },
                onBackToLogin = {
                    navController.popBackStack()
                },
                isLoading = authState is AuthState.Loading,
                errorMessage = (authState as? AuthState.Error)?.message
            )
        }

        // 7. Role Selection Screen
        composable(Routes.ROLE_SELECTION) {
            RoleSelectionScreen(
                onContinue = { selectedRole ->
                    DisasterRepository.setUserRole(selectedRole)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // 8. Home Screen
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToWarningDetail = { id ->
                    if (id.contains("flood")) {
                        navController.navigate(Routes.FLOOD_DETAIL)
                    } else {
                        navController.navigate(Routes.STORM_DETAIL)
                    }
                },
                onNavigateToCategory = { category ->
                    when (category) {
                        DisasterCategory.RESCUE -> navController.navigate(Routes.MISSION_LIST)
                        DisasterCategory.FLOOD -> navController.navigate(Routes.FLOOD_DETAIL)
                        else -> navController.navigate(Routes.WARNING_LIST)
                    }
                },
                onNavigateToNotifications = {
                    navController.navigate(Routes.NOTIFICATION)
                },
                onNavigateToMap = {
                    navController.navigate(Routes.MAP)
                },
                onBottomNavNavigate = { route ->
                    if (route != Routes.HOME) {
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }

        // 9. Disaster Map Screen
        composable(Routes.MAP) {
            // Observe local and Firebase-projected SOS events through the repository.
            val sosEvents by SosAppDependencies.sosRepository.observeSos()
                .collectAsState(initial = emptyList())

            DisasterMapScreen(
                
                onNavigateToDetail = { eventId ->
                    navController.navigate(Routes.sosDetail(eventId))
                },
                onNavigateToSosDetail = { eventId ->
                    navController.navigate(Routes.sosDetail(eventId))
                },
                onBottomNavNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // 10. Warning List Screen
        composable(Routes.WARNING_LIST) {
            WarningListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { id ->
                    if (id.contains("flood")) {
                        navController.navigate(Routes.FLOOD_DETAIL)
                    } else {
                        navController.navigate(Routes.STORM_DETAIL)
                    }
                }
            )
        }

        // 11. Storm Detail Screen
        composable(Routes.STORM_DETAIL) {
            StormDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 12. Flood Detail Screen
        composable(Routes.FLOOD_DETAIL) {
            FloodDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onViewFloodMap = { navController.navigate(Routes.MAP) }
            )
        }

        // 13, 14, 15. Volunteer Registration (3 steps)
        composable(Routes.VOLUNTEER_REGISTER) {
            VolunteerRegistrationScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onNavigateProfile = {
                    navController.navigate(Routes.PROFILE) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }

        // 16. Rescue Mission List
        composable(Routes.MISSION_LIST) {
            RescueMissionListScreen(
                onNavigateToDetail = { missionId ->
                    navController.navigate(Routes.missionDetail(missionId))
                },
                onNavigateToRequestDetail = { requestId ->
                    navController.navigate(Routes.sosDetail(requestId))
                },
                onBottomNavNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }


        // Rescue Request Detail (Self-Accept Flow)
        composable(
            route = Routes.REQUEST_DETAIL,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType })
        ) { backStackEntry ->
            val requestId = backStackEntry.arguments?.getString("requestId") ?: ""
            RescueRequestDetailScreen(
                requestId = requestId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMissionList = {
                    navController.navigate(Routes.MISSION_LIST) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }


        composable(
            route = Routes.SOS_DETAIL,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            com.example.bai1.presentation.sos.SosDetailScreen(
                eventId = eventId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        // 17. Rescue Mission Detail
        composable(
            route = Routes.MISSION_DETAIL,
            arguments = listOf(navArgument("missionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val missionId = backStackEntry.arguments?.getString("missionId") ?: "mission_1"
            MissionDetailScreen(
                missionId = missionId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { navController.navigate(Routes.GROUP_CHAT) }
            )
        }

        // 18. Rescue Chat
        composable(Routes.RESCUE_CHAT) {
            RescueChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.GROUP_LIST) {
            GroupListScreen(
                onNavigateToChat = { navController.navigate(Routes.GROUP_CHAT) }, // Changed to GROUP_CHAT
                onBottomNavNavigate = { route -> 
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(Routes.GROUP_CHAT) {
            GroupChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // --- COMMON SCREENS ---Emergency Screen
        composable(Routes.SOS) {
            SOSScreen(
                onNavigateBack = { navController.popBackStack() },
                onBottomNavNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onHoldSosSuccess = { navController.navigate(Routes.SOS_RESCUE_REQUEST) }
            )
        }

        composable(Routes.SOS_RESCUE_REQUEST) {
            SosRescueRequestScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMap = {
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // 20. Notifications Screen
        composable(Routes.NOTIFICATION) {
            NotificationScreen(
                onNavigateBack = { navController.popBackStack() },
                onBottomNavNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // 21. Profile Screen
        composable(Routes.PROFILE) {
            ProfileScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToVolunteerRegister = { navController.navigate(Routes.VOLUNTEER_REGISTER) },
                onLogout = {
                    authViewModel.logout()
                    scope.launch {
                        try {
                            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBottomNavNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // 22. Settings Screen
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAboutApp = { navController.navigate(Routes.ABOUT_APP) },
                onLogout = {
                    authViewModel.logout()
                    scope.launch {
                        try {
                            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBottomNavNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // 23. Forgot Password Screen
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                authViewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSendCode = { identifier -> navController.navigate(Routes.otpVerification(identifier)) }
            )
        }

        // 24. OTP Verification Screen
        composable(
            route = Routes.OTP_VERIFICATION,
            arguments = listOf(navArgument("identifier") { type = NavType.StringType })
        ) { backStackEntry ->
            val identifier = backStackEntry.arguments?.getString("identifier") ?: ""
            OTPScreen(
                identifier = identifier,
                onNavigateBack = { navController.popBackStack() },
                onVerifySuccess = { otp ->
                    navController.navigate(Routes.resetPassword(identifier, otp))
                }
            )
        }
        
        // 24.5 Reset Password Screen
        composable(
            route = Routes.RESET_PASSWORD,
            arguments = listOf(
                navArgument("identifier") { type = NavType.StringType },
                navArgument("otp") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val identifier = backStackEntry.arguments?.getString("identifier") ?: ""
            val otp = backStackEntry.arguments?.getString("otp") ?: ""
            ResetPasswordScreen(
                identifier = identifier,
                otp = otp,
                authViewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onResetSuccess = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // 25. About App Screen
        composable(Routes.ABOUT_APP) {
            AboutAppScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
