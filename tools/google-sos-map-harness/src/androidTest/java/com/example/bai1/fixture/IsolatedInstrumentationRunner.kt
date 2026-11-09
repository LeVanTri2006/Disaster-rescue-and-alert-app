package com.example.bai1.fixture

import android.os.SystemClock
import androidx.test.runner.AndroidJUnitRunner

/** Allow the host to attach PID-scoped logcat before any test executes; no initialization override. */
class IsolatedInstrumentationRunner : AndroidJUnitRunner() {
    override fun onStart() {
        SystemClock.sleep(1500)
        super.onStart()
    }
}
