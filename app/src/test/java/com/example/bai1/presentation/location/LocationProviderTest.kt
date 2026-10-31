package com.example.bai1.presentation.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationProviderTest {
    @Test
    fun `fake provider returns a valid location`() {
        var result: LocationResult? = null
        FakeLocationProvider().getCurrentLocation { result = it }

        val success = result as LocationResult.Success
        assertEquals(10.0, success.latitude, 0.0)
        assertEquals(20.0, success.longitude, 0.0)
        assertTrue(success.accuracyMeters >= 0f)
        assertTrue(success.timestamp > 0L)
    }

    @Test
    fun `provider errors remain distinguishable`() {
        val errors = listOf(
            LocationError.UNAVAILABLE,
            LocationError.PERMISSION_DENIED,
            LocationError.TIMEOUT,
            LocationError.INVALID
        )

        errors.forEach { expected ->
            var result: LocationResult? = null
            FakeLocationProvider(LocationResult.Error(expected)).getCurrentLocation { result = it }
            assertEquals(expected, (result as LocationResult.Error).reason)
        }
    }
}
