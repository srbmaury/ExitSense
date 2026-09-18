package com.exitsense.app.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiSsidMatcherTest {

    @Test
    fun `plain names split on commas`() {
        assertEquals(listOf("A", "B", "C"), parseHomeWifiSsids(" A, B ,C,, "))
    }

    @Test
    fun `escaped comma stays part of the name`() {
        assertEquals(listOf("Home, 5G", "Office"), parseHomeWifiSsids("Home\\, 5G, Office"))
    }

    @Test
    fun `join escapes commas so names round-trip`() {
        val names = listOf("Home, 5G", "Mesh,Upstairs", "Plain")
        assertEquals(names, parseHomeWifiSsids(joinHomeWifiSsids(names)))
    }

    @Test
    fun `network with a comma in its name matches`() {
        assertTrue(matchesHomeWifiSsid(joinHomeWifiSsids(listOf("Home, 5G")), "Home, 5G"))
    }

    @Test
    fun `backslash not before a comma is kept`() {
        assertEquals(listOf("A\\B"), parseHomeWifiSsids("A\\B"))
    }
}
