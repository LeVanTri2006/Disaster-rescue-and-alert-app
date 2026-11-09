package com.example.bai1.uiisolated

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bai1.auth.RoleSelectionViewModel
import com.example.bai1.data.model.*
import com.example.bai1.data.repository.DisasterRepository
import com.example.bai1.presentation.navigation.AppNavScreens

/** Standalone only. Actual production UI; every cloud destination keeps E06's unavailable binding. */
class ActualAccountFixtureScreens(private val app: GraphFixtureApplication) : AppNavScreens by GraphFixtureScreens(app) {
    @Composable override fun RoleSelectionScreen(onContinue: (UserRole) -> Unit) {
        val model: RoleSelectionViewModel = viewModel(factory = RoleSelectionViewModel.factory(app.repository))
        SideEffect { app.roleModel = model }
        com.example.bai1.presentation.auth.RoleSelectionScreen(onContinue, model)
    }
    @Composable override fun ProfileScreen(onNavigateToSettings: () -> Unit, onNavigateToVolunteerRegister: () -> Unit, onLogout: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        val state by app.repository.state.collectAsState()
        val role by DisasterRepository.currentUserRole.collectAsState()
        com.example.bai1.presentation.profile.ProfileScreen(onNavigateToSettings, onNavigateToVolunteerRegister, onLogout, onBottomNavNavigate, state, role)
    }
    @Composable override fun SettingsScreen(onNavigateBack: () -> Unit, onNavigateToAboutApp: () -> Unit, onLogout: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        com.example.bai1.presentation.settings.SettingsScreen(onNavigateBack, onNavigateToAboutApp, onLogout, onBottomNavNavigate)
    }
    @Composable override fun HomeScreen(onNavigateToWarningDetail: (String) -> Unit, onNavigateToCategory: (DisasterCategory) -> Unit, onNavigateToNotifications: () -> Unit, onNavigateToMap: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        com.example.bai1.presentation.home.HomeScreen(onNavigateToWarningDetail, onNavigateToCategory, onNavigateToNotifications, onNavigateToMap, onBottomNavNavigate)
    }
}
