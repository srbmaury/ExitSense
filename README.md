# ExitSense

**A smart exit reminder for Android — local-first, no cloud account, and no continuous GPS tracking.**

ExitSense watches your sensors and figures out when you're walking out the door. When it's confident enough, it sends you a notification with your personal checklist so you never forget your keys, badge, or laptop again.

---

## How it knows you're leaving

The app scores seven independent signals every time something meaningful changes. When the total crosses a threshold (default 75), the notification fires.

| Signal | Points | When it fires |
|---|---:|---|
| Left home Wi-Fi | +50 | Disconnected, or connected to a different network |
| Still on home Wi-Fi | **0 (hard cap)** | Short-circuits everything — you're still home |
| Driving detected | +25 | Accelerometer variance pattern matches vehicle |
| Walking detected | +15 | Accelerometer cadence matches footsteps |
| Running detected | +15 | |
| Charger unplugged | +10 | Within the last 30 minutes |
| Barometer descent | +10 | Pressure dropped ≥ 0.3 hPa — roughly one floor |
| Steps in last 60 s | +10 | ≥ 20 steps detected via step detector |
| Screen recently unlocked | +5 | Unlocked within the last 3 minutes |
| Outdoor ambient light | +5 | Sensor reading above outdoor lux threshold |
| Within profile schedule | +5 | Current time falls inside a profile's active window |

**Threshold is adjustable** (40–100) in Settings. At 75 you need at least two strong signals — Wi-Fi alone isn't enough.

**Examples at threshold 75:**
- Wi-Fi left (+50) + Walking (+15) + Charger unplugged (+10) = **75** ✓
- Wi-Fi left (+50) + Driving (+25) = **75** ✓
- Wi-Fi left (+50) + Walking (+15) + Steps (+10) = **75** ✓

---

## Wi-Fi detection and permissions

ExitSense uses the home Wi-Fi identity as a strong departure signal, but modern Android versions redact SSID and network identity unless the app has the permissions required by the platform.

- On Android 12+, Wi-Fi identity access requests both coarse and fine location; requesting fine location alone is not sufficient.
- On newer Android versions, `NEARBY_WIFI_DEVICES` is also requested where applicable.
- Background monitoring uses a location-type foreground service when the system permits it so Wi-Fi identity can remain available while monitoring is active.
- If the phone is connected to Wi-Fi but Android does not expose enough identity information to verify the network, ExitSense treats the device as still at home rather than combining unrelated sensor signals into a false exit.

The app does **not** use continuous GPS tracking for exit detection. Location access is also used by the optional weather integration when enabled.

---

## Monitoring modes

ExitSense runs detection in two modes simultaneously:

| Mode | How | Interval |
|---|---|---|
| **Passive** | WorkManager periodic job | Every 15 minutes |
| **Active** | Foreground service, reacts to sensor changes | 2-second debounce |

The foreground service is optional — turn it on from the Home screen toggle for faster reaction times. The periodic job runs regardless.

---

## Profiles

A profile is a named checklist tied to a schedule. You can have as many as you want.

- **Schedule:** Weekdays, Weekends, Every day, or Custom days
- **Time window:** e.g. 08:00–10:00 — notifications only fire inside this window
- **Items:** anything to remember (Laptop, Keys, Wallet, Badge…)
- **Active toggle:** enable or disable a profile without deleting it

Each profile can notify **once per active schedule window**. For overnight windows (for example 22:00–02:00), the after-midnight portion belongs to the previous day's schedule. This replaces the old 24-hour cooldown, which could suppress the next day's reminder when you left slightly earlier.

---

## Notifications

When an exit is detected, the notification shows:

- Your profile's checklist items
- Weather alert if rain is forecast (optional, needs Location)
- Upcoming calendar events in the next 3 hours (optional, needs Calendar permission)

**Actions on the notification:**
- **Confirm** — marks that you took your items; recorded for the learning system
- **Snooze** — re-shows after a configurable delay (default 2 minutes, range 1–15 min)

---

## Learning system

Every time you confirm a notification, the app records which items you actually acknowledged. Over time it adjusts a priority multiplier (range 0.2–2.0) per item — items you consistently confirm rank higher, items you always ignore rank lower.

---

## Optional integrations

Both are off by default and require additional permissions.

**Weather** — Fetches a brief forecast from [Open-Meteo](https://open-meteo.com/) (free, no API key). Adds a rain/heat/cold alert line to the notification body. Requires `ACCESS_FINE_LOCATION`.

**Calendar** — Reads upcoming events from your locally-synced device calendar and includes them in the notification. Requires `READ_CALENDAR`. No sign-in, no cloud sync.

---

## Screens

| Screen | What you do there |
|---|---|
| Home | Toggle monitoring, see live sensor readings, run a manual detection check |
| Profiles | View, enable/disable, edit, or delete reminder profiles |
| Add / Edit Profile | Set the name, schedule, active days, time window, and checklist items |
| History | Browse past exit events and confidence scores |
| Settings | Change home Wi-Fi, home floor, notification toggle, snooze duration, confidence threshold |
| Integrations | Enable/disable Weather and Calendar, grant required permissions |
| Setup Wizard | First-launch flow — Wi-Fi, floor, first profile, permissions |

---

## Building the app

### Prerequisites

| | Required |
|---|---|
| Android Studio | Meerkat 2024.3 or newer |
| JDK | 21 — use Android Studio's bundled JDK, **not** a system JDK 22+ |
| Android SDK | API 36 |

### Android Studio

1. **File → Open** → select the `ExitSense/` folder
2. Wait for Gradle sync (first run downloads ~300 MB)
3. Select a device or emulator → press **Run** (Shift+F10)

> If sync fails with a `jlink` error:
> `Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK → Android Studio default JDK (21)`

### Command line

Set `JAVA_HOME` to Android Studio's bundled JDK first:

```bash
# macOS
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Linux
export JAVA_HOME="$HOME/android-studio/jbr"
```

```bash
# Debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Clean build
./gradlew clean assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## First launch

The Setup Wizard runs once on install:

1. **Wi-Fi** — connect to your home Wi-Fi and tap *Use This Network*, or type the name manually
2. **Floor** — pick your home floor so the barometer can track descent (skipped if no barometer)
3. **Profile** — create a first checklist profile (an Office starter is provided)
4. **Permissions** — Activity Recognition, Notifications, Battery Optimization

---

## Permissions

| Permission | Purpose | Required? |
|---|---|---|
| `ACCESS_WIFI_STATE` | Detect Wi-Fi connect / disconnect events | Yes |
| `ACCESS_NETWORK_STATE` | Network state callbacks | Yes |
| `NEARBY_WIFI_DEVICES` | Read Wi-Fi network ID on Android 13+ without location | Yes |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Run the monitoring service | Yes |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule background work after reboot | Yes |
| `WAKE_LOCK` | Keep the sensor job alive briefly | Yes |
| `POST_NOTIFICATIONS` | Show exit reminder notifications (Android 13+) | Yes |
| `ACTIVITY_RECOGNITION` | Walking / running / driving detection | Optional |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent the system from killing background work | Recommended |
| `INTERNET` | Fetch weather from Open-Meteo | Optional (Weather only) |
| `ACCESS_FINE_LOCATION` | Read Wi-Fi SSID on Android 9+ · Fetch local weather | Optional |
| `READ_CALENDAR` | Read upcoming calendar events | Optional (Calendar only) |

> No data ever leaves your device except for the optional weather request to Open-Meteo (which only sends your GPS coordinates, nothing personal).

---

## Architecture

```
app/src/main/java/com/exitsense/app/
├── data/
│   ├── local/
│   │   ├── dao/           5 Room DAOs
│   │   ├── database/      AppDatabase (Room, version 1)
│   │   ├── entities/      5 table entities
│   │   └── mapper/        Entity ↔ Domain converters
│   ├── preferences/       DataStore — all user settings
│   └── repository/        5 implementations (Reminder, ExitEvent,
│                          Learning, Calendar, Weather)
├── di/                    Hilt modules (Database, Repository, Sensor,
│                          Notification, Qualifiers)
├── domain/
│   ├── model/             Pure Kotlin models (no Android imports)
│   ├── repository/        Interfaces for use-cases and ViewModels
│   └── usecase/           6 use-cases
├── notifications/         ExitNotificationManager · NotificationActionReceiver
├── presentation/
│   ├── components/        Shared Compose components
│   ├── history/           History screen + ViewModel
│   ├── home/              Home dashboard + ViewModel
│   ├── integrations/      Weather + Calendar screen + ViewModel
│   ├── navigation/        NavGraph + Screen routes
│   ├── profiles/          Profiles list · Add/Edit screen + ViewModels
│   ├── settings/          Settings screen + ViewModel
│   ├── setup/             4-step Setup Wizard + ViewModel
│   └── theme/             Material 3 — Color, Type, Theme (light + dark)
├── receivers/             BootReceiver (re-schedules workers on boot)
├── rules/
│   ├── impl/              ExitDetectorImpl — confidence scoring engine
│   ├── ExitDetector.kt    Interface (injectable, mockable in tests)
│   ├── SignalWeight.kt    Per-signal weights + DEFAULT_EXIT_THRESHOLD
│   └── TimeRuleEvaluator.kt
├── sensors/               7 provider interfaces + implementations
│   │                      Motion · WiFi · Pressure · Screen ·
│   │                      Steps · Charger · Ambient Light
├── service/               ExitMonitoringService (foreground, 2 s debounce)
├── workers/               ExitDetectionWorker (15 min)
│                          LearningAnalysisWorker (daily, on charge)
│                          SnoozeWorker (one-shot)
└── ExitSenseApplication.kt   Hilt entry point + WorkManager init
```

**Stack:** Kotlin · Jetpack Compose · Material 3 · Hilt · Room · WorkManager · DataStore · Kotlin Coroutines + Flow

---

## Database

Five tables, all local, no sync.

| Table | Stores |
|---|---|
| `reminder_profiles` | Profile name, schedule, time window, last-notified timestamp |
| `reminder_items` | Checklist items per profile + learned priority multiplier |
| `exit_events` | Timestamp, confidence score, triggered signals (JSON), profile FK |
| `user_responses` | Per-item confirm/dismiss, used by the learning system |
| `sensor_snapshots` | Raw sensor readings (kept 30 days, then pruned automatically) |

---

## Tests and CI

```bash
# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests (requires an Android device/emulator)
./gradlew connectedDebugAndroidTest
```

The current suite contains **98 unit tests and 5 instrumented tests**. Coverage includes exit scoring, overnight schedule handling, Wi-Fi/permission edge cases, reminder dispatch, repositories and use-cases, ViewModels, mappers, foreground-service behavior, and Compose components.

GitHub Actions runs unit tests and builds both debug and release variants on pushes and pull requests.

---

## Release signing

In Android Studio: **Build → Generate Signed Bundle / APK → APK**.

Via Gradle, add to `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("keystore.jks")
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "your-alias"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
buildTypes {
    release { signingConfig = signingConfigs.getByName("release") }
}
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Build fails with `jlink non-zero exit value` | Set `JAVA_HOME` to Android Studio's JDK 21 (see above) |
| `SDK location not found` | Create `local.properties` with `sdk.dir=/path/to/Android/sdk` |
| `Installed platform not found android-36` | SDK Manager → install **Android 16 (API 36)** |
| SSID shows `<unknown ssid>` | Grant the requested location permission and make sure system Location services are enabled; modern Android may redact both SSID and network identity otherwise |
| No notification even though exit is detected | The current time must fall inside an active profile's scheduled window |
| Notifications stopped after a few hours | Grant Battery Optimization exemption: Settings → Apps → ExitSense → Battery → Unrestricted |
| Barometer or light row missing from Home | Hardware not present on this device — those signals are silently skipped |
| Two notifications for the same exit | Service and worker detection now share one reminder dispatcher and should not duplicate; capture logs if this still occurs |
