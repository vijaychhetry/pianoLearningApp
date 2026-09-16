# Sideload APK

Debug build of **Kids Piano Lab**. Not a Play Store release.

**Download:** [KidsPianoLab-debug-0.2.0.apk](./KidsPianoLab-debug-0.2.0.apk)

On GitHub, open that file and use **Download raw file**. On a phone, you can also use:

https://github.com/vijaychhetry/pianoLearningApp/raw/cursor/android-audio-lab-5032/releases/KidsPianoLab-debug-0.2.0.apk

Install it and allow **Microphone**.

## What the two screens are for

**Calibrate** is the setup step. It asks for one key at a time — C, D, E, F, G — takes several clean samples of each, and saves how your piano sounds in your room. Wrong keys are rejected and told to you; skipping a key means the profile cannot score Excellent.

**Audio Lab** is an engineering readout, not a lesson. It shows mic level, frequency, MIDI note, confidence, latency, which microphone source is in use, and a scrolling log. The log writes the first frame and then a heartbeat, so it is never silently empty.

If nothing registers: check the mic level bar moves. If it stays flat, tap **Change mic** to cycle `UNPROCESSED → VOICE_RECOGNITION → MIC`; some phones accept UNPROCESSED and return silence. The app also switches on its own after 1.5 seconds of pure silence.

## Version history

| Version | Notes |
| --- | --- |
| 0.2.0-lab | Added the guided Calibrate flow; Audio Lab now shows a live level meter, honest status, a never-empty log, and microphone-source fallback. Pitch range widened to A2–F6. |
| 0.1.0-lab | First Audio Lab build. |

Uninstall this debug app before installing a later Play Store build with the same package id (`com.vijaychhetry.kidspiano`).
