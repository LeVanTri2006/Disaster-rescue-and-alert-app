package com.example.bai1.rescue.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.bai1.rescue.RescueMissionAppDependencies
import com.example.bai1.sos.SosAppDependencies

class RescueMissionViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RescueMissionViewModel::class.java)) {
            val sosRepo = runCatching { SosAppDependencies.sosRepository }.getOrNull()
            @Suppress("UNCHECKED_CAST")
            return RescueMissionViewModel(
                RescueMissionAppDependencies.rescueMissionRepository,
                sosRepo
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
