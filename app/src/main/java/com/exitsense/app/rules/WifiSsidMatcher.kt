package com.exitsense.app.rules

internal fun parseHomeWifiSsids(homeWifiSsid: String): List<String> =
    homeWifiSsid.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

internal fun matchesHomeWifiSsid(homeWifiSsid: String, connectedSsid: String?): Boolean {
    if (connectedSsid == null) return false
    return parseHomeWifiSsids(homeWifiSsid).any { it.equals(connectedSsid, ignoreCase = true) }
}
