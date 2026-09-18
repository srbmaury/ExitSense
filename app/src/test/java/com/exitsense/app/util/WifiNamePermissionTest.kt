package com.exitsense.app.util

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class WifiNamePermissionTest {
    @Test
    fun `android 13 and newer require nearby wifi devices and location`() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            WifiNamePermission.requiredPermissions(apiLevel = 33)
        )
    }

    @Test
    fun `android 9 through 12 require location`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            WifiNamePermission.requiredPermissions(apiLevel = 28)
        )
        assertArrayEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            WifiNamePermission.requiredPermissions(apiLevel = 32)
        )
    }

    @Test
    fun `older versions do not require a runtime wifi name permission`() {
        assertArrayEquals(emptyArray<String>(), WifiNamePermission.requiredPermissions(apiLevel = 27))
    }
}
