package com.example.bai1.presentation.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.bai1.auth.AuthAppDependencies
import com.example.bai1.data.repository.DisasterRepository

/** Production binding: original Auth and in-memory role sources, no synthetic defaults. */
@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToVolunteerRegister: () -> Unit,
    onLogout: () -> Unit,
    onBottomNavNavigate: (String) -> Unit
) {
    val state by AuthAppDependencies.repository.state.collectAsState()
    val role by DisasterRepository.currentUserRole.collectAsState()
    ProfileScreen(onNavigateToSettings, onNavigateToVolunteerRegister, onLogout, onBottomNavNavigate, state, role)
}
