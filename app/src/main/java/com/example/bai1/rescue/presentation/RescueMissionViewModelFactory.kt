package com.example.bai1.rescue.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.bai1.rescue.RescueMissionAppDependencies

class RescueMissionViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RescueMissionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RescueMissionViewModel(RescueMissionAppDependencies.rescueMissionRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
