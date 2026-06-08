# Smart Exit Reminder — ExitSense

A production-ready Android application that intelligently detects when you are leaving home and reminds you to take everything you need — **without GPS or location permission**.

---

## Quick Start

```
Build status : ✅  DEBUG APK builds successfully
Tests        : ✅  30/30 passing (5 test suites)
APK size     : ~19 MB (debug)
Min SDK      : API 26 (Android 8.0)
Target SDK   : API 36 (Android 16)
```

---

## Opening in Android Studio

### Prerequisites

| Tool | Required version | Notes |
|------|------------------|-------|
| Android Studio | Meerkat (2024.3) or newer | [Download](https://developer.android.com/studio) |
| JDK | **21** (bundled with Android Studio) | Do **not** use the system JDK if it is v22+ |
| Android SDK Platform | **API 36** | Install via SDK Manager if missing |
| Android Build Tools | **36.1.0** or newer | Install via SDK Manager if missing |
| Gradle | **9.3** (auto-downloaded by wrapper) | No manual install needed |

### Step-by-step

1. **Open the project**
   ```
   Android Studio → File → Open → select the ExitSense/ folder
   ```
   Android Studio will automatically detect the `settings.gradle.kts` and begin Gradle sync.

2. **Wait for Gradle sync**
   The first sync downloads all Maven dependencies (~300 MB). Progress is shown in the
   *Build* tool window at the bottom. This takes 2–5 minutes on a first run.

3. **Configure the JDK** *(only needed if sync fails with a JDK error)*
   ```
   File → Settings → Build, Execution, Deployment → Build Tools → Gradle
   Gradle JDK → select "Android Studio default JDK" (JDK 21)
   ```
   On macOS the Gradle JDK setting is under:
   ```
   Android Studio → Settings → Build, Execution, Deployment → Build Tools → Gradle
   ```

4. **Run on a device or emulator**
   - Select a device from the run-target dropdown (top toolbar).
   - Click **▶ Run** (Shift+F10) to build and install.

5. **Grant permissions on first launch**
   The 4-step Setup Wizard covers Wi-Fi name, home floor, default profile, and permissions
   (Activity Recognition · Post Notifications · Battery Optimization).

---

## Command-line Build

> These commands work from the `ExitSense/` project root. The first run downloads
> Gradle 9.3 and all Maven dependencies — internet connection required.

### Set the correct JDK

Android Studio ships JDK 21 at a known path. Export it before running any `./gradlew` command:

**macOS:**
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

**Linux (typical Android Studio install):**
```bash
export JAVA_HOME="$HOME/android-studio/jbr"
```

**Windows (Git Bash / PowerShell):**
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

> **Why?** The system JDK may be newer than JDK 21. AGP 8.9 uses `jlink`
> internally and it is not compatible with JDK 22+.

### Build commands

```bash
# Debug APK — fastest, includes debug symbols
./gradlew assembleDebug

# Release APK (requires signing config — see Signing section below)
./gradlew assembleRelease

# Install debug APK directly to a connected device / running emulator
./gradlew installDebug

# Run all unit tests
./gradlew test

# Run unit tests and generate HTML coverage report
./gradlew testDebugUnitTest
# Report: app/build/reports/tests/testDebugUnitTest/index.html

# Full clean build (use when switching branches or after schema changes)
./gradlew clean assembleDebug
```

### APK output location

```
app/build/outputs/apk/debug/app-debug.apk       (~19 MB)
app/build/outputs/apk/release/app-release.apk   (after signing)
```

---

## Signing a Release APK

1. In Android Studio: **Build → Generate Signed Bundle / APK → APK → Next**
2. Create or select a keystore file.
3. Fill alias, key password, store password → **Next** → select `release` → **Finish**.

Or via command line: add a `signingConfigs` block in `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("path/to/keystore.jks")
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "your-alias"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

---

## Architecture

```
ExitSense/app/src/main/java/com/exitsense/app/
├── data/
│   ├── local/
│   │   ├── dao/          Room DAOs (5 interfaces)
│   │   ├── database/     AppDatabase + MIGRATION_1_2 + MIGRATION_2_3
│   │   ├── entities/     Room entities (5 tables)
│   │   └── mapper/       Entity ↔ Domain converters
│   ├── preferences/      DataStore — user settings (home Wi-Fi, threshold…)
│   └── repository/       Repository implementations (3 classes)
├── di/                   Hilt modules (Database, Repository, Sensor, Notification)
├── domain/
│   ├── model/            Pure Kotlin models (no Android imports)
│   ├── repository/       Interfaces consumed by use-cases
│   └── usecase/          Business logic (6 use-cases)
├── notifications/        ExitNotificationManager + NotificationActionReceiver
├── presentation/
│   ├── components/       Shared Compose components
│   ├── history/          History screen + ViewModel
│   ├── home/             Home dashboard + ViewModel
│   ├── navigation/       NavGraph + Screen sealed class
│   ├── profiles/         Profile list + Add/Edit screens + ViewModels
│   ├── settings/         Settings screen + ViewModel
│   ├── setup/            4-step wizard + ViewModel
│   └── theme/            Material 3 (Color, Type, Theme — dark + light)
├── receivers/            BootReceiver (re-schedules WorkManager)
├── rules/
│   ├── impl/             ExitDetectorImpl — confidence scoring engine
│   ├── ExitDetector.kt   Interface (mockable in tests)
│   ├── SignalWeight.kt   Configurable per-signal weights
│   └── TimeRuleEvaluator.kt
├── sensors/
│   ├── impl/             MotionProviderImpl, WifiProviderImpl, PressureProviderImpl,
│   │                     ScreenStateProviderImpl, StepCountProviderImpl,
│   │                     ChargerStateProviderImpl, AmbientLightProviderImpl
│   └── *.kt              Interfaces + data classes (mockable in tests)
├── service/              ExitMonitoringService (foreground)
├── workers/              ExitDetectionWorker, LearningAnalysisWorker, SnoozeWorker
└── ExitSenseApplication.kt   Hilt entry point + WorkManager initialiser
```

### Key design decisions

| Concern | Approach |
|---|---|
| No GPS / no location | Wi-Fi SSID + accelerometer + barometer + light sensor + charger state + screen + steps |
| Architecture | MVVM · Repository · Use-cases |
| DI | Hilt (SingletonComponent) |
| DB | Room v3 with explicit migrations |
| Background | WorkManager periodic (15 min) + optional Foreground Service (30 s poll) |
| Notification cap | One notification per 24 h **per profile** — Office and Gym each get their own cooldown |
| Notification timing | Notification only fires when at least one profile is within its scheduled time window (`TimeRuleEvaluator`) |
| Notification actions | Confirm saves `UserResponse` for every enabled item; Snooze re-shows after the user-configured duration |
| State | Kotlin StateFlow / SharedFlow throughout |
| UI | Jetpack Compose + Material 3 (light & dark mode) |
| Learning | Confirmation-rate → `learnedPriority` multiplier [0.2, 2.0] |

---

## Exit Confidence Scoring

Every evaluation combines signals into a confidence score and fires a notification when `score ≥ threshold`.

| Signal | Score | Notes |
|--------|-------|-------|
| Home Wi-Fi disconnected | **+50** | Primary exit indicator |
| Still connected to home Wi-Fi | **−40** | Hard-clamps result ≤ 0 |
| Outdoor ambient light (≥ 3000 lux) | **+30** | Near-certain exit confirmation |
| Charger unplugged (within 30 min) | **+25** | Strong pre-departure signal; resets when plugged back in |
| Walking | +20 | |
| Running | +20 | |
| Barometer descent (≥ 0.3 hPa drop) | +20 | ~1 floor change |
| Driving | +15 | |
| Steps in last 60 s (≥ 20 steps) | +15 | Rolling window via `TYPE_STEP_DETECTOR` |
| Screen recently unlocked (< 3 min) | +10 | |
| Within profile time window | +10 | Tiebreaker |

**Default threshold: 80** — configurable per user in Settings.

Example: *Wi-Fi off (+50) + Walking (+20) + Charger unplugged (+25) = **95** → notification fires.*

Example: *Outdoor light (+30) + Wi-Fi off (+50) + Schedule (+10) = **90** → notification fires.*

---

## Database Schema

Room v3 with schema export (`app/schemas/`).

| Table | Purpose |
|-------|---------|
| `reminder_profiles` | Name, schedule type, active days, time window, per-profile notification timestamp |
| `reminder_items` | Checklist items per profile + `learnedPriority` multiplier |
| `exit_events` | Timestamp, confidence score, triggered signals (JSON) |
| `user_responses` | Per-item confirm/dismiss, used by learning system |
| `sensor_snapshots` | Raw sensor readings for debugging |

**Migration 1 → 2:** adds `learnedPriority REAL NOT NULL DEFAULT 1.0` to `reminder_items`.

**Migration 2 → 3:** adds `lastNotifiedAt INTEGER NOT NULL DEFAULT 0` to `reminder_profiles` for per-profile notification cooldown.

---

## Permissions

| Permission | Purpose | Required? |
|---|---|---|
| `ACCESS_WIFI_STATE` | Detect Wi-Fi connect/disconnect | Yes |
| `ACCESS_NETWORK_STATE` | `ConnectivityManager.NetworkCallback` | Yes |
| `ACTIVITY_RECOGNITION` | Walking/running/driving detection | Optional |
| `POST_NOTIFICATIONS` | Show exit reminders (Android 13+) | Yes |
| `FOREGROUND_SERVICE` | Continuous monitoring | Yes |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule workers after reboot | Yes |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep background detection reliable | Recommended |
| `ACCESS_FINE_LOCATION` | **NOT requested** | — |

> On Android 9+ the SSID may appear as `<unknown ssid>` without location permission.
> The app handles this gracefully — Wi-Fi *disconnect events* still fire and contribute
> their +50 score regardless of SSID visibility.

---

## Testing

```bash
# Run all unit tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.exitsense.app.rules.ExitDetectorTest"

# HTML report (opens in browser)
open app/build/reports/tests/testDebugUnitTest/index.html
```

| Test suite | Tests | Coverage area |
|---|---|---|
| `ExitDetectorTest` | 7 | Scoring, threshold, at-home suppression, barometer |
| `TimeRuleEvaluatorTest` | 9 | Schedule matching, day logic, overnight windows |
| `SaveProfileUseCaseTest` | 4 | Create vs update, validation errors |
| `HomeViewModelTest` | 3 | StateFlow emission, sensor changes, manual detection |
| `MapperTest` | 7 | Entity↔Domain round-trips, malformed data handling |
| **Total** | **30** | All passing |

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `jlink non-zero exit value` | Wrong JDK. Set `JAVA_HOME` to Android Studio's JDK 21 (see above). |
| `SDK location not found` | Create `local.properties` with `sdk.dir=/path/to/Android/sdk` |
| `Installed platform not found android-36` | Open SDK Manager → install **Android 16 (API 36)** platform |
| Gradle sync stuck / fails with 401 | Check proxy settings in Android Studio |
| `@HiltAndroidApp` not found | Run `./gradlew clean` then re-sync |
| SSID shows `<unknown ssid>` | Normal on Android 9+. Users type their home SSID manually in setup. |
| No notification despite exit detected | Check that the current time falls within at least one active profile's scheduled window. |
| Notification fires outside work hours | Ensure the profile's start/end time is correct in the Profiles screen. |
| Background detection stops after a few hours | Grant Battery Optimization exemption in Setup step 4 or System Settings → Battery. |
| Pressure/light row missing from Live Sensors card | Sensor hardware not present on this device or emulator. |
| Got two notifications for same exit | Each profile has its own 24 h cooldown — check if multiple profiles matched the same event. |
