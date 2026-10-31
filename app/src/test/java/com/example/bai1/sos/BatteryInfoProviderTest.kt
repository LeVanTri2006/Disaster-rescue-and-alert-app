package com.example.bai1.sos

import com.example.bai1.sos.provider.FakeBatteryInfoProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryInfoProviderTest {
    @Test
    fun `fake battery provider returns a valid percentage`() {
        assertEquals(73, FakeBatteryInfoProvider(73).getBatteryPercent())
    }
}
