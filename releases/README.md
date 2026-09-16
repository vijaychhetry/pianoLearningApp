# Sideload APK

Debug build of **Kids Piano Lab**. Not a Play Store release.

**Download:** [KidsPianoLab-debug-0.3.0.apk](./KidsPianoLab-debug-0.3.0.apk)

On GitHub, open that file and use **Download raw file**. On a phone, you can also use:

https://github.com/vijaychhetry/pianoLearningApp/raw/cursor/android-audio-lab-5032/releases/KidsPianoLab-debug-0.3.0.apk

Install it and allow **Microphone**.

## What the two screens are for

**Calibrate** is the setup step. It asks for one key at a time — C, D, E, F, G — and takes **four separate presses** of each. Press the key, let go, press it again; holding one key down does not count four times, and the screen tells you to let go. Wrong keys are rejected and named back to you. Skipping a key means the profile cannot score Excellent, and a profile that scores Needs improvement is **not** saved unless you tap **Use anyway**.

**Audio Lab** is an engineering readout, not a lesson. It shows mic level, frequency, MIDI note, confidence, compute latency, which microphone source is in use, and a scrolling log. The log writes the first frame and then a heartbeat, so it is never silently empty.

If nothing registers: watch the mic level bar. If it stays flat, tap **Change mic** to cycle `UNPROCESSED → VOICE_RECOGNITION → MIC`; some phones accept UNPROCESSED and return silence. The app also switches on its own after 1.5 seconds of silence, or if the microphone opens but never delivers audio at all. It tries each source **once** and then tells you plainly that no source is working, rather than cycling forever.

## Version history

| Version | Notes |
| --- | --- |
| 0.3.0-lab | Review fixes. A microphone that opens but sends nothing is now detected by a clock rather than by the audio it is failing to send, so the screen can no longer sit blank. Source recovery stops after one pass and keeps the log. Calibration takes one sample per key press instead of one per frame, and a weak profile is no longer saved silently. Test suite 73 → 113. |
| 0.2.0-lab | Added the guided Calibrate flow; Audio Lab now shows a live level meter, honest status, a never-empty log, and microphone-source fallback. Pitch range widened to A2–F6. |
| 0.1.0-lab | First Audio Lab build. |

Uninstall this debug app before installing a later Play Store build with the same package id (`com.vijaychhetry.kidspiano`).
