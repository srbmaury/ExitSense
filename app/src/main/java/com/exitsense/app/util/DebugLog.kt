package com.exitsense.app.util

import android.util.Log
import com.exitsense.app.BuildConfig

/** Logcat output for debug builds only. Filter with `adb logcat -s ExitSense`. */
object DebugLog {
    private const val TAG = "ExitSense"

    fun d(area: String, message: () -> String) {
        if (!BuildConfig.DEBUG) return
        // android.util.Log isn't available in plain JVM unit tests
        runCatching { Log.d(TAG, "[$area] ${message()}") }
    }
}
