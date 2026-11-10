package com.example.bai1.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.bai1.auth.AuthAppDependencies
import com.example.bai1.auth.AuthViewModel
import com.example.bai1.auth.credentials.CredentialStateCleaner
import com.example.bai1.data.repository.DisasterRepository

/** Original production entry point; no test defaults or synthetic credentials. */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Routes.SPLASH,
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(AuthAppDependencies.repository)),
    credentialStateCleaner: CredentialStateCleaner? = null
) {
    val context = LocalContext.current
    val screens = remember(context) { ProductionAppNavScreens(context.applicationContext) }
    val defaultCleaner = remember(context) { CredentialStateCleaner {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } }
    AppNavGraph(navController, authViewModel, screens, credentialStateCleaner ?: defaultCleaner,
        DisasterRepository::setUserRole, startDestination)
}
