package com.exitsense.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object WifiNamePermission {
    // Coarse is requested alongside fine because Android 12+ ignores a fine-only request
    fun requiredPermissions(apiLevel: Int = Build.VERSION.SDK_INT): Array<String> =
        when {
            apiLevel >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            apiLevel >= Build.VERSION_CODES.P ->
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            else -> emptyArray()
        }

    fun isGranted(context: Context, apiLevel: Int = Build.VERSION.SDK_INT): Boolean =
        requiredPermissions(apiLevel).all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
}
