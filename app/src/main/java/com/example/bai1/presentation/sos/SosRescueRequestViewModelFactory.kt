package com.example.bai1.presentation.sos
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.bai1.presentation.location.AndroidLocationProvider
import com.example.bai1.sos.SosAppDependencies
import com.example.bai1.sos.provider.AndroidBatteryInfoProvider
class SosRescueRequestViewModelFactory(private val context:Context):ViewModelProvider.Factory {
 @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T {
  SosAppDependencies.initialize(context.applicationContext)
  return SosRescueRequestViewModel(SosAppDependencies.sosRepository,AndroidLocationProvider(context.applicationContext),AndroidBatteryInfoProvider(context.applicationContext),com.example.bai1.sos.repository.R2SosPhotoUploader(context.applicationContext,SosAppDependencies.apiClient)) as T
 }
}
