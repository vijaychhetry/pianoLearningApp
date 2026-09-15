# Kids Piano — Android (Phase 1 Audio Lab)

Native Android app for the kids product spec (`docs/KIDS_PIANO_MASTER_SPEC.md`).
This is **not** the React website.

## What is here

Phase 0 research: `docs/android/PHASE0_RESEARCH.md`  
Phase 1: **Piano Audio Lab** only — live pitch, no games.

| Module | Role |
| --- | --- |
| `:core:notes` | MIDI math, `NoteValidator` (octave-mismatch = soft correct) |
| `:core:pitch` | `PitchDetector` (YIN + HPS), debounce state machine |
| `:core:calibration` | Profile types + median engine (used in Phase 2 UI) |
| `:core:audio` | `AudioRecord` with UNPROCESSED → fallback, drop-oldest queue |
| `:app` | Compose Audio Lab screen |

## Download APK (no Android Studio)

A debug APK is in [`releases/KidsPianoLab-debug-0.1.0.apk`](../releases/KidsPianoLab-debug-0.1.0.apk). On GitHub, open the file and click **Download raw file**, then install it on the phone (allow install from that source). Allow **Microphone** when the app asks.

## Open in Android Studio

1. Install Android Studio (Ladybug / API 35 SDK).
2. **File → Open** the `android/` folder (not the repo root).
3. Let Gradle sync.
4. Plug in a phone or start an emulator, run `app`.
5. Allow microphone. Put the device on a piano (Yamaha PSR-F52 is the spec target). Play **one** key.

## Command line (JDK 17+, Android SDK)

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :core:notes:test :core:pitch:test :core:calibration:test
./gradlew :app:installDebug
```

Core JVM tests do not need a device. AudioRecord behavior does.

## What we are not building yet

Lessons, falling notes, characters, parent dashboard, Play Store listing.
