package com.example.bai1.sos.provider

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Abstraction for reading device battery level.
 *
 * The ViewModel depends ONLY on this interface — never on Android's
 * BatteryManager directly. This ensures:
 *   - Unit testing without Android context
 *   - Easy replacement with real BatteryManager in Phase 7B
 *
 */
interface BatteryInfoProvider {
    /**
     * Returns current battery level as an integer percentage (0..100).
     * Returns -1 if unavailable.
     */
    fun getBatteryPercent(): Int
}

/**
 * Fake implementation for architecture testing and UI development.
 *
 * @param percent simulated battery level percentage. Defaults to 85.
 */
class FakeBatteryInfoProvider(
    private val percent: Int = 85
) : BatteryInfoProvider {
    override fun getBatteryPercent(): Int = percent
}

/** Reads the current battery percentage without exposing Android APIs to the ViewModel. */
class AndroidBatteryInfoProvider(context: Context) : BatteryInfoProvider {
    private val appContext = context.applicationContext

    override fun getBatteryPercent(): Int {
        val batteryManager = appContext.getSystemService(BatteryManager::class.java)
        val propertyValue = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?: Int.MIN_VALUE
        if (propertyValue in 0..100) return propertyValue

        val batteryIntent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            return ((level * 100f) / scale).toInt().coerceIn(0, 100)
        }
        return -1
    }
}
