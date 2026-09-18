package com.exitsense.app.rules

/**
 * Home Wi-Fi names are stored as one comma-separated string. A comma that is part of a
 * network name is written as `\,` so it isn't mistaken for a separator.
 */
internal fun parseHomeWifiSsids(homeWifiSsid: String): List<String> {
    val names = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < homeWifiSsid.length) {
        val c = homeWifiSsid[i]
        when {
            c == '\\' && homeWifiSsid.getOrNull(i + 1) == ',' -> {
                current.append(',')
                i++
            }
            c == ',' -> {
                names += current.toString()
                current.clear()
            }
            else -> current.append(c)
        }
        i++
    }
    names += current.toString()
    return names.map { it.trim() }.filter { it.isNotEmpty() }
}

internal fun joinHomeWifiSsids(ssids: List<String>): String =
    ssids.joinToString(", ") { it.trim().replace(",", "\\,") }

internal fun matchesHomeWifiSsid(homeWifiSsid: String, connectedSsid: String?): Boolean {
    if (connectedSsid == null) return false
    return parseHomeWifiSsids(homeWifiSsid).any { it.equals(connectedSsid, ignoreCase = true) }
}
