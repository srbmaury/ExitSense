package com.exitsense.app.domain.model

enum class ExitSignalType(val displayName: String) {
    WIFI_DISCONNECTED("Wi-Fi Disconnected"),
    WIFI_CONNECTED_HOME("At Home Wi-Fi"),
    WIFI_UNVERIFIED("On Wi-Fi (network unknown)"),
    MOTION_WALKING("Walking"),
    MOTION_RUNNING("Running"),
    MOTION_DRIVING("Driving"),
    SCREEN_UNLOCKED("Screen Unlocked"),
    TIME_WINDOW_MATCH("Time Window"),
    BAROMETER_DESCENT("Floor Descent"),
    STEP_COUNT("Steps Detected"),
    CHARGER_UNPLUGGED("Charger Unplugged"),
    AMBIENT_LIGHT("Outdoor Light")
}
