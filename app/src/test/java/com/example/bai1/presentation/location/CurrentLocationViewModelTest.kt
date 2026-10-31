package com.example.bai1.presentation.location

import com.example.bai1.sos.presentation.LocationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLocationViewModelTest {
    @Test
    fun `exposes the newest successful location`() {
        val callbacks = mutableListOf<(LocationResult) -> Unit>()
        val provider = object : LocationProvider {
            override fun getCurrentLocation(onResult: (LocationResult) -> Unit) {
                callbacks += onResult
            }
        }
        val viewModel = CurrentLocationViewModel(provider)

        viewModel.refresh()
        viewModel.refresh()
        callbacks[0](LocationResult.Success(1.0, 2.0, 100f, 1L))
        assertTrue(viewModel.state.value is LocationUiState.Loading)
        callbacks[1](LocationResult.Success(16.05, 108.20, 4f, 2L))

        val state = viewModel.state.value as LocationUiState.Success
        assertEquals(16.05, state.latitude, 0.0001)
        assertEquals(108.20, state.longitude, 0.0001)
        assertEquals(4f, state.accuracyMeters)
    }

    @Test
    fun `maps permission denial to an actionable error`() {
        val viewModel = CurrentLocationViewModel(FakeLocationProvider())

        viewModel.permissionDenied()

        val state = viewModel.state.value as LocationUiState.Error
        assertTrue(state.message.contains("quyền vị trí"))
    }
}
