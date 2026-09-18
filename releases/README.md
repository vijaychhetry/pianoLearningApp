# Sideload APK

Debug build of **Kids Piano**. Not a Play Store release.

**Download (Level 1):** [KidsPiano-debug-0.4.0.apk](./KidsPiano-debug-0.4.0.apk)

On GitHub, open that file and use **Download raw file**. On a phone, you can also use the raw URL for this branch:

https://github.com/vijaychhetry/pianoLearningApp/raw/cursor/level-1-find-key-2211/releases/KidsPiano-debug-0.4.0.apk

Install it and allow **Microphone**. Uninstall an older **Kids Piano Lab** debug build first if the installer refuses (same package id `com.vijaychhetry.kidspiano`).

## How to use it

1. Open the app. **Practice** stays off until a grown-up sets up the piano.
2. Tap **Grown-ups**, answer **2 + 5**, then **Calibrate**. Play C D E F G, four separate presses each (press, let go, press again) — not all 36 white keys.
3. Tap **Kids Home**. **Practice** should be on. Tap it, tap **Start**, play the big letter on the Yamaha, one key at a time.

**Audio Lab** is still there under Grown-ups. It is an engineering readout, not a lesson.

## Version history

| Version | Notes |
| --- | --- |
| 0.4.0 | Child Home + Level 1 Find this key (C–G). Calibration profile now retunes A4 for live recognition. Calibrate and Audio Lab sit behind a grown-ups gate. |
| 0.3.0-lab | Review fixes. A microphone that opens but sends nothing is now detected by a clock rather than by the audio it is failing to send, so the screen can no longer sit blank. Source recovery stops after one pass and keeps the log. Calibration takes one sample per key press instead of one per frame, and a weak profile is no longer saved silently. Test suite 73 → 113. |
| 0.2.0-lab | Added the guided Calibrate flow; Audio Lab now shows a live level meter, honest status, a never-empty log, and microphone-source fallback. Pitch range widened to A2–F6. |
| 0.1.0-lab | First Audio Lab build. |

Uninstall this debug app before installing a later Play Store build with the same package id (`com.vijaychhetry.kidspiano`).
