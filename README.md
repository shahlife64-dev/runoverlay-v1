# Telemetry Overlay — Running Stats

A floating, draggable overlay (like Telemetry Overlay / RaceChrono-style widgets)
that shows 8 live stats over any app while you run:

**Time of day · Date · Activity time · Distance · Pace · Heart rate · Cadence · Elevation**

## Data sources
| Stat | Source |
|---|---|
| Time of day / Date | System clock |
| Activity time | Stopwatch from overlay start |
| Distance / Pace / Elevation | Phone GPS (Fused Location Provider) |
| Heart rate | Any standard BLE Heart Rate strap/watch (Bluetooth HR profile 0x180D — Polar, Garmin, Wahoo, Coospo, etc.) |
| Cadence | Phone's built-in step-detector sensor (steps per minute, both feet) |

## Why there's no .apk attached
This project was written in an environment with no Android SDK and no network
access, so it could not be compiled here. You'll need to build it yourself —
takes about 5 minutes with Android Studio.

## Build instructions (Android Studio — recommended)
1. Install **Android Studio** (free): https://developer.android.com/studio
2. Unzip this project, then **File → Open** and select the `TelemetryOverlay` folder.
3. Let Gradle sync (Android Studio will auto-download the Gradle version pinned
   in `gradle/wrapper/gradle-wrapper.properties` — first sync needs internet).
4. Plug in your phone (USB debugging on) or use an emulator, then press **Run ▶**
   to install a debug build directly — no signing needed for this.
5. To get a standalone `.apk` file you can share/install without a cable:
   **Build → Generate Signed Bundle / APK → APK** → create a new keystore
   (any password) → build **release**. The `.apk` lands in
   `app/release/app-release.apk`.

## Build instructions (command line, if you have the Android SDK installed)
```
cd TelemetryOverlay
gradle wrapper --gradle-version 8.4   # generates gradlew/gradlew.bat once
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## First run on your phone
1. Open the app → **Grant Permissions** (overlay + location + Bluetooth +
   activity recognition; accept "Allow all the time" for background location
   if you want tracking to survive the screen locking).
2. **Pair Heart Rate Monitor** → put your strap/watch in pairing mode → tap it
   in the list. (Skip this if you don't have one — HR just won't update.)
3. **Start Overlay** → switch to Strava/Spotify/whatever → the stats box
   floats on top. Drag the top bar to reposition it; tap ✕ to close it.
4. **Stop Overlay** from the app, or drag down the notification and stop it
   from there, when your run is done.

## Known limitations / things you may want to tune
- Cadence uses the phone's on-device step detector. It's fine in a pocket or
  armband but noisier than a dedicated footpod — the code averages over a
  rolling 10s window to smooth it.
- Pace is smoothed over the trailing 30 seconds of GPS fixes, not the whole run.
- Elevation comes from GPS altitude, which drifts more than a barometer. If
  your phone has a barometric pressure sensor (`Sensor.TYPE_PRESSURE`), that's
  a straightforward swap in `LocationTracker`/`OverlayService` for more
  accurate elevation.
- Some OEMs (Xiaomi, Huawei, Samsung in some modes) aggressively kill
  background services — you may need to disable battery optimization for
  this app in system settings for long runs.
- No data is saved/exported after the run; this is a live overlay only, not a
  run-logging app. Say the word if you want a CSV/GPX export added.
