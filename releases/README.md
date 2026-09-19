# Sideload APK

Debug build of **Kids Piano**. Not a Play Store release.

**Download (0.5.2 — landscape fits, no scroll):** [KidsPiano-debug-0.5.2.apk](./KidsPiano-debug-0.5.2.apk)

On GitHub, open that file and use **Download raw file**. On a phone, you can also use the raw URL for this branch:

https://github.com/vijaychhetry/pianoLearningApp/raw/cursor/spec-v4-050-2211/releases/KidsPiano-debug-0.5.2.apk

Install it and allow **Microphone**. Uninstall an older **Kids Piano** debug build first if the installer refuses (same package id `com.vijaychhetry.kidspiano`).

## How to use it

1. Open the app. **Practice** stays off until a grown-up sets up the piano.
2. **Hold Grown-ups for 2 seconds**, answer the times-table (`6×6` … `9×9`), then hold **Hold to open**.
3. **Setup** — put the phone on the stand, stick the five colour labels on **C4 D4 E4 F4 G4**, share the sticker list if you want a printout.
4. **Calibrate** — pick the key from the dropdown if you need to jump. Play four separate presses of each asked key (press, let go, press again). The on-screen keyboard shows which physical key.
5. **Files** — share the calibration JSON and the session log (JSON only, no recordings).
6. **Kids Home → Practice**. The phone turns **landscape**. Play the glowing key on the Yamaha. The big letter is `C`; under it it says `C4 · middle C`.

**Audio Lab** is still under Grown-ups. It is an engineering readout, not a lesson.

## How to export

1. Grown-ups → **Files**.
2. **Share calibration JSON** or **Share session log**.
3. Pick Drive / email / Files. The filename starts with a UTC stamp `yyyy-MM-dd-HHmm`.
4. Optional: drop the files into `diagnostics/inbox/` on a computer for later analysis.

## Version history

| Version | Notes |
| --- | --- |
| 0.5.2 | Landscape Practice is one screen: letter, feedback, and keyboard all fit. No scroll. |
| 0.5.1 | Practice (and later games) lock to landscape so the keyboard no longer overlaps. Home, Setup, Calibrate, Files, and Audio Lab stay portrait. |
| 0.5.0 | Spec v4 “Trust the ears”: on-screen keyboard (mini-map + zoom), hearable-key dropdown, exportable calibration + session log, Setup + stickers, times-table gate with 2 s hold, hop-512 capture. Level ladder and falling game are **not** in this APK. |
| 0.4.0 | Child Home + Level 1 Find this key (C–G). Calibration profile now retunes A4 for live recognition. Calibrate and Audio Lab sit behind a grown-ups gate. |
| 0.3.0-lab | Review fixes. A microphone that opens but sends nothing is now detected by a clock rather than by the audio it is failing to send, so the screen can no longer sit blank. Source recovery stops after one pass and keeps the log. Calibration takes one sample per key press instead of one per frame, and a weak profile is no longer saved silently. Test suite 73 → 113. |
| 0.2.0-lab | Added the guided Calibrate flow; Audio Lab now shows a live level meter, honest status, a never-empty log, and microphone-source fallback. Pitch range widened to A2–F6. |
| 0.1.0-lab | First Audio Lab build. |

Uninstall this debug app before installing a later Play Store build with the same package id (`com.vijaychhetry.kidspiano`).
