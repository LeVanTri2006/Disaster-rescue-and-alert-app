package com.example.bai1.presentation.auth

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bai1.auth.AuthAppDependencies
import com.example.bai1.auth.RoleSelectionViewModel
import com.example.bai1.data.model.UserRole

/** Production binding retains the original destination-scoped ViewModel/repository defaults. */
@Composable
fun RoleSelectionScreen(onContinue: (UserRole) -> Unit) {
    val model: RoleSelectionViewModel = viewModel(factory = RoleSelectionViewModel.factory(AuthAppDependencies.repository))
    RoleSelectionScreen(onContinue, model)
}
