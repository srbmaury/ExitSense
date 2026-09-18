package com.exitsense.app.service

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.exitsense.app.presentation.MainActivity
import com.exitsense.app.util.WifiNamePermission
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Starts the real monitoring service and checks what the system reports for it.
 * Requires Android 14+ for the service-type assertion.
 */
@RunWith(AndroidJUnit4::class)
class ExitMonitoringServiceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName

    @Before
    fun grantPermissions() {
        val permissions = WifiNamePermission.requiredPermissions() +
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else emptyArray()
        permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(packageName, it) }
    }

    @After
    fun stopService() {
        context.stopService(Intent(context, ExitMonitoringService::class.java))
    }

    @Test
    fun serviceRunsInForegroundWithLocationTypeWhileAppIsVisible() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(ExitMonitoringService.start(context))

            val record = waitForServiceRecord()
            assertTrue("service should be foreground:\n$record", "isForeground=true" in record)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val types = Regex("types=([0-9a-f]+)").find(record)?.groupValues?.get(1)?.toInt(16)
                    ?: fail("no service type in:\n$record").let { 0 }
                val location = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                val specialUse = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                assertTrue("expected location type, got 0x${types.toString(16)}", types and location != 0)
                assertTrue("expected special-use type, got 0x${types.toString(16)}", types and specialUse != 0)
            }
        }
    }

    @Test
    fun stopServiceEndsMonitoring() {
        ActivityScenario.launch(MainActivity::class.java).use {
            ExitMonitoringService.start(context)
            waitForServiceRecord()

            context.stopService(Intent(context, ExitMonitoringService::class.java))

            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                if ("ExitMonitoringService" !in dumpServices()) return
                Thread.sleep(200)
            }
            fail("service still running:\n${dumpServices()}")
        }
    }

    private fun waitForServiceRecord(): String {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val dump = dumpServices()
            if ("ExitMonitoringService" in dump && "isForeground=true" in dump) return dump
            Thread.sleep(200)
        }
        return dumpServices()
    }

    private fun dumpServices(): String {
        val fd = instrumentation.uiAutomation.executeShellCommand("dumpsys activity services $packageName")
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }
}
