# Kids Piano — Android

Native Android app for the kids product spec (`docs/KIDS_PIANO_MASTER_SPEC.md`).
This is **not** the React website.

## What is here

Phase 0 research: `docs/android/PHASE0_RESEARCH.md`  
Phase 1–2: Audio Lab + guided calibration (grown-up tools).  
Phase 3 start: **Level 1 Find this key** (C D E F G) for the child.

| Module | Role |
| --- | --- |
| `:core:notes` | MIDI math, `NoteValidator` (octave-mismatch = soft correct) |
| `:core:pitch` | `PitchDetector` (YIN + HPS), debounce state machine |
| `:core:pitch` (cont.) | `LabSession`, `MicSourceCycler`, `FrameArrivalMonitor` — everything the Audio Lab screen decides, with no Android types so it is testable |
| `:core:calibration` | Profile types, median engine, `concertA4Hz`, and `CalibrationRunner` |
| `:core:learning` | `LessonSession` (Level 1 find-this-key) and the grown-ups math gate |
| `:core:audio` | `AudioRecord` with UNPROCESSED → fallback, drop-oldest queue |
| `:app` | Compose screens; thin adapters over the core session classes |

## Download APK (no Android Studio)

A debug APK is in [`releases/KidsPiano-debug-0.4.0.apk`](../releases/KidsPiano-debug-0.4.0.apk). On GitHub, open the file and click **Download raw file**, then install it on the phone (allow install from that source). Allow **Microphone** when the app asks.

If 0.4.0 is not in the folder yet, the previous lab build is still at [`releases/KidsPianoLab-debug-0.3.0.apk`](../releases/KidsPianoLab-debug-0.3.0.apk).

## What the child sees

**Home** — **Practice** (C D E F G, one key at a time) once a grown-up has calibrated. **Grown-ups** sits behind a small math question.

**Practice** — big letter, play that key on the Yamaha, then Yes / Try C / I couldn't hear that. Never marks a guess as wrong. Checkmark, retry, and question-mark cues sit next to the colour so the result is not colour-only.

## Grown-up tools (unchanged job)

**Calibrate** — setup. Five keys, four separate presses each. A weak profile is not saved as the default.

**Audio Lab** — engineering readout (spec §15). Not a lesson.

## Open in Android Studio

1. Install Android Studio (Ladybug / API 35 SDK).
2. **File → Open** the `android/` folder (not the repo root).
3. Let Gradle sync.
4. Plug in a phone or start an emulator, run `app`.
5. Allow microphone. Put the device on a piano (Yamaha PSR-F52 is the spec target). Grown-ups → Calibrate, then Kids Home → Practice.

## Command line (JDK 17+, Android SDK)

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :core:notes:test :core:pitch:test :core:calibration:test :core:learning:test
./gradlew :app:installDebug
```

JVM tests need no device (129 as of 0.4.0). They are held to mutation testing rather than to a green tick: see `docs/android/ACCEPTANCE_CRITERIA.md`. Real `AudioRecord` behaviour still needs a phone.

## What we are not building yet

Falling notes, songs, characters, badges, parent dashboard, Play Store listing.
