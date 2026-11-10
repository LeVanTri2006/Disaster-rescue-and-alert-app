package com.example.bai1.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.bai1.data.model.DisasterCategory
import com.example.bai1.auth.AuthViewModel
import com.example.bai1.auth.credentials.CredentialStateCleaner
import com.example.bai1.auth.session.AuthState
import com.example.bai1.presentation.auth.*
import com.example.bai1.presentation.settings.AboutAppScreen
import com.example.bai1.data.model.UserRole

@Composable
fun AppNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    screens: AppNavScreens,
    credentialStateCleaner: CredentialStateCleaner,
    onUserRoleChanged: (UserRole) -> Unit,
    startDestination: String = Routes.SPLASH
) {
    val authState by authViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val entry by navController.currentBackStackEntryAsState()

    LaunchedEffect(authState, entry?.destination?.route) {
        val currentState = authState
        if (currentState is AuthState.Authenticated) {
            val role = runCatching { UserRole.valueOf((currentState.user.role ?: "USER").uppercase()) }
                .getOrDefault(UserRole.USER)
            onUserRoleChanged(role)
        }
        val destination = AuthNavigationPolicy.destination(authState, entry?.destination?.route)
        if (destination != null) {
            navController.navigate(destination) {
                // Authentication boundaries remove every prior protected/auth form destination.
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
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
            screens.LoginScreen(
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.ROLE_SELECTION)) return@composable
            screens.RoleSelectionScreen(
                onContinue = { selectedRole ->
                    onUserRoleChanged(selectedRole)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ROLE_SELECTION) { inclusive = true }
                    }
                }
            )
        }

        // 8. Home Screen
        composable(Routes.HOME) {
            if (!AuthNavigationPolicy.canRender(authState, Routes.HOME)) return@composable
            screens.HomeScreen(
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.MAP)) return@composable
            screens.DisasterMapScreen(
                
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.WARNING_LIST)) return@composable
            screens.WarningListScreen(
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.STORM_DETAIL)) return@composable
            screens.StormDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 12. Flood Detail Screen
        composable(Routes.FLOOD_DETAIL) {
            if (!AuthNavigationPolicy.canRender(authState, Routes.FLOOD_DETAIL)) return@composable
            screens.FloodDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onViewFloodMap = { navController.navigate(Routes.MAP) }
            )
        }

        // 13, 14, 15. Volunteer Registration (3 steps)
        composable(Routes.VOLUNTEER_REGISTER) {
            if (!AuthNavigationPolicy.canRender(authState, Routes.VOLUNTEER_REGISTER)) return@composable
            screens.VolunteerRegistrationScreen(
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.MISSION_LIST)) return@composable
            screens.RescueMissionListScreen(
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.REQUEST_DETAIL)) return@composable
            val requestId = backStackEntry.arguments?.getString("requestId") ?: ""
            screens.RescueRequestDetailScreen(
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.SOS_DETAIL)) return@composable
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            screens.SosDetailScreen(
                eventId = eventId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        // 17. Rescue Mission Detail
        composable(
            route = Routes.MISSION_DETAIL,
            arguments = listOf(navArgument("missionId") { type = NavType.StringType })
        ) { backStackEntry ->
            if (!AuthNavigationPolicy.canRender(authState, Routes.MISSION_DETAIL)) return@composable
            val missionId = backStackEntry.arguments?.getString("missionId") ?: "mission_1"
            screens.MissionDetailScreen(
                missionId = missionId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { navController.navigate(Routes.GROUP_CHAT) },
                onNavigateToMap = {
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // 18. Rescue Chat
        composable(Routes.RESCUE_CHAT) {
            if (!AuthNavigationPolicy.canRender(authState, Routes.RESCUE_CHAT)) return@composable
            screens.RescueChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.GROUP_LIST) {
            if (!AuthNavigationPolicy.canRender(authState, Routes.GROUP_LIST)) return@composable
            screens.GroupListScreen(
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.GROUP_CHAT)) return@composable
            screens.GroupChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // --- COMMON SCREENS ---Emergency Screen
        composable(Routes.SOS) {
            if (!AuthNavigationPolicy.canRender(authState, Routes.SOS)) return@composable
            screens.SOSScreen(
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.SOS_RESCUE_REQUEST)) return@composable
            screens.SosRescueRequestScreen(
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.NOTIFICATION)) return@composable
            screens.NotificationScreen(
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.PROFILE)) return@composable
            screens.ProfileScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToVolunteerRegister = { navController.navigate(Routes.VOLUNTEER_REGISTER) },
                onLogout = {
                    authViewModel.logout()
                    scope.launch {
                        try {
                            credentialStateCleaner.clear()
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
            if (!AuthNavigationPolicy.canRender(authState, Routes.SETTINGS)) return@composable
            screens.SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAboutApp = { navController.navigate(Routes.ABOUT_APP) },
                onLogout = {
                    authViewModel.logout()
                    scope.launch {
                        try {
                            credentialStateCleaner.clear()
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
            screens.ForgotPasswordScreen(
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
            screens.OTPScreen(
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
            screens.ResetPasswordScreen(
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
