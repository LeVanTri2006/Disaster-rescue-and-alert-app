package com.example.bai1.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.bai1.rescue.repository.RescueMissionRepository
import com.example.bai1.sos.repository.SosRepository

class DisasterMapViewModelFactory(
    private val sosRepository: SosRepository,
    private val rescueMissionRepository: RescueMissionRepository,
    private val context: android.content.Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DisasterMapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DisasterMapViewModel(
                sosRepository = sosRepository,
                rescueMissionRepository = rescueMissionRepository,
                context = context
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
