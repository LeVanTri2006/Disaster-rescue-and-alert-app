package com.example.bai1.presentation.location
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
class CurrentLocationViewModelFactory(private val context: Context): ViewModelProvider.Factory {
 @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T = CurrentLocationViewModel(AndroidLocationProvider(context.applicationContext)) as T
}
