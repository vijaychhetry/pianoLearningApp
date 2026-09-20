# Spec gap: web prototype vs Kids Master Spec v3.0

**Verdict: we are not following the kids spec. Build a new native Android app.**

Keep the current Vite/React site only as a **pitch-detection lab / prototype**. Do not grow it into the kids product.

Canonical product spec: [`KIDS_PIANO_MASTER_SPEC.md`](KIDS_PIANO_MASTER_SPEC.md)  
What we actually shipped: a Chrome web tuner (`docs/SPEC.md`, `src/`).

The kids spec is explicit (Section 2, instruction 15): first production app is **Kotlin + Jetpack Compose + AudioRecord**. React / React Native must not replace native audio for the MVP.

---

## Why not “just convert this app”?

| Kids spec (v3.0) | Current repo | Match? |
| --- | --- | --- |
| Android Play app, independent child practice | Website, adult/teacher tuner | No |
| Kotlin, Compose, Hilt, Room, DataStore | React 19 + Vite | No |
| `AudioRecord` + `UNPROCESSED` + audio-priority thread + bounded PCM ring buffer | Browser `getUserMedia` + `AnalyserNode` + rAF | No |
| TarsosDSP behind `PitchDetector` | Custom YIN/HPS in TypeScript | Concept only |
| Foreground service + mic indicator (Android 12+) | Tab dies when Chrome backgrounds | No |
| Calibration: several samples per note C–G, Room profile, blind C–G test | Silence floor + one A4 cents offset in `localStorage` | No |
| States: UNCLEAR / AMBIGUOUS must never show as wrong | Practice marks any locked other MIDI as wrong | No |
| Audio Lab first, then lessons, then one falling-notes game | Listen / Calibrate / Practice UI already | Wrong order vs spec |
| Original characters, stars, badges, parent dashboard | None | No |
| Designed for Families / Play Data Safety | Not an Android app | No |
| Tests: JUnit + on-device `androidTest` on PSR-F52 | 20 JVM-style Vitest sine/piano fixtures | Partial idea only |

Reuse **ideas**, not the **app**:

- Frequency → MIDI math
- “Don’t trust one detector; combine YIN + harmonic check”
- “Don’t save a bad calibration”
- Synthetic piano-tone fixtures as a starting regression set

Those belong in a new `core/pitch` Kotlin module. The React UI, Web Audio session, and three-tab layout should not be the product.

---

## Recommended path

1. **Adopt v3.0 as the only product spec.** Treat `docs/SPEC.md` as “web lab notes.”
2. **New Android codebase** in this repo under `android/` (or a sibling repo). Same GitHub project is fine; same React tree is not.
3. **Follow spec phases:** Phase 0 research (TarsosDSP/Aubio) → Phase 1 Audio Lab on a real phone next to a Yamaha PSR-F52 → Phase 2 per-note calibration → Phase 3 five-note lessons → then one falling-notes game.
4. **Do not** start characters, songs, or Play Store polish before Audio Lab is stable on hardware.
5. **Decide now (spec §44):** if the child plays C5 when the lesson asks for C4 — treat as soft-correct (“right letter, other C”) for 5-key beginner range. Encode that in `NoteValidator` before lessons.

---

## What this means for the open PR

The current PR is still useful as a **browser proof of pitch math**. It is **not** MVP toward the kids spec. Next implementation work should be a greenfield Android module, not more React screens.
