# Piano Learning App — product spec (v1)

There was no product spec before this file. `docs/FIX_PLAN.md` is the
**engineering** plan for pitch detection. This document is the **product** spec:
what is being built, for whom, and what “working” means.

---

## 1. One-sentence pitch

A web page that **listens to a piano through the microphone**, names the note,
and asks the player to match a target key.

Think of it as a **note-hearing tutor**, not a full piano school and not a
kids’ game.

---

## 2. Who it is for

| Who | Fit in v1 |
| --- | --- |
| Parent or teacher next to an acoustic / digital piano | Yes — this is the user |
| Child age 7+ who already knows note names, with an adult doing setup | Possible |
| Child age 5 | No — adult must run the app |
| Child age 3 | No |
| Student on a phone with no piano (on-screen keys only) | Demo / practice without a mic, not real piano learning |

v1 language is English. UI is technical (Hz, cents, Calibrate). A kids skin
is **not** in v1.

---

## 3. What you can do

Three screens, plus a piano at the bottom.

```
  [ Listen ]   [ Calibrate ]   [ Practice ]
  -----------------------------------------
  big note + tuner / wizard / target note
  -----------------------------------------
  C3 ……………… keyboard ……………… C6
```

### Listen

- Turn on the **microphone**, or use the **Simulator**.
- Play **one** piano key (or tap a key on screen).
- The app shows the note name (e.g. `A4`), a rough frequency, and how sharp/flat
  it is (cents).
- The matching key on the picture keyboard lights up.

### Calibrate (required for a real piano)

Two measurements. An adult should do this.

1. **Silence** — nobody plays for ~2 seconds. The app learns room noise so
   quiet notes are not ignored and chatter is not treated as a note.
2. **A4** — hold the A above middle C. The app learns if this piano is not
   exactly 440 Hz.

If the capture is bad (wrong note, too noisy), quality is `poor` and the
offset is **not** blindly saved.

### Practice

- App shows one target from the C major notes `C4 D4 E4 F4 G4 A4 B4 C5`.
- Player plays that note on the real piano (or taps the picture key).
- Correct → score goes up, next note appears.
- Wrong → “Heard X — try Y”.

### Simulator (no piano / no mic)

Tapping a picture key injects a fake piano tone into the **same** detector.
Used for development and for checking the UI without hardware.

---

## 4. What “working” means

A session is a success when **all** of these are true:

1. Chrome (or Edge) on a laptop, or Chrome on Android, with mic permission
   allowed, page on `https://` or `localhost`.
2. Adult completes Calibrate: silence + A4, quality `ok` or `good`.
3. Phone or laptop mic is **close** to the piano (music stand, not across the room).
4. Player plays **one key at a time**.
5. Listen names that key in the middle octaves (about C3–C6 on screen;
   engine hears roughly A1–C7).
6. Practice accepts the target note and rejects a clearly different note.

It is **not** a failure if chords, very low bass, or a far-away phone mic
are wrong. Those are out of scope.

---

## 5. What we are not building (v1)

- Chords / two hands at once
- MIDI cable keyboards
- Full 88-key range on screen
- Songs, sheet music, or a lesson library
- Accounts, cloud, teachers’ dashboard
- Play Store / App Store native app
- Spoken voice, cartoon rewards, or a 3–5 year old UI
- Auto-starting without a tap (browsers block the mic until a click)

---

## 6. Platform

| Surface | v1 |
| --- | --- |
| Desktop browser (Chrome / Edge) | Primary |
| Android **Chrome** (HTTPS + mic permission) | Supported, small keys |
| Android Play Store app | No |
| iPhone Safari | Same rules as Chrome; still a website |
| In-app browsers (Instagram, Facebook) | Do not rely on these |

The product is a **static web app** (React + Web Audio). It is not a Kotlin
or Flutter install.

---

## 7. How a first-time user should run it

1. Open the site in Chrome.
2. Sit the device on the piano or very near the strings / speakers.
3. Tap **Calibrate** → Enable microphone → allow the browser prompt.
4. Stay quiet → Capture silence.
5. Hold **A4** → Capture A4.
6. Go to **Practice** (or Listen) and play single notes.

If the mic is refused, only the on-screen Simulator works.

---

## 8. Build status (this repo)

| Item | Status |
| --- | --- |
| Product spec (this file) | Current |
| Listen / Calibrate / Practice UI | Built on branch `cursor/piano-note-recognition-5032` |
| Pitch engine + calibration | Built; 20 automated tests |
| Real acoustic piano on Android | Not proven in the cloud VM — needs a device next to a piano |
| Kids mode (ages 5–7) | Not built |

---

## 9. Possible later versions (not committed)

Only if we decide the product is for children:

- **Kids 5–7:** giant target letter, colors, stars, parent-only Calibrate.
- **Kids 3:** not a good fit for microphone pitch games; picture + tap is a
  different product.

Until then, treat v1 as **adult setup, child may play Practice**.
