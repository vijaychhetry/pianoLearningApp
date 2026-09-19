# Levels, songs, and the falling-notes game

Same Android app. Builds on 0.5.0 (note-name hero, 61-key guide, Calibrate dropdown, export). This slice turns one practice screen into a **ladder a child can climb**, ends every session with a **real tune**, and adds the **falling-notes game** as the reward at the top.

Vocabulary stays **five white keys, C4–G4**. Everything below is built from those five keys and nothing else.

## Why the ladder

0.5.0 Practice asks for C4, then D4, then E4, F4, G4 — five different keys on the child's very first run. That is five new motor skills in one sitting. A four-year-old needs to own **one** key before the second one means anything, and needs to hear the same key come back in a different order before it is really learned.

## The ladder

Eight levels. Each is short (30–90 seconds). No level can be failed.

| # | Name | Keys | What happens |
| --- | --- | --- | --- |
| 1 | **One key: C** | C4 | Press C4 six times. The only key lit. Builds "this key is C." |
| 2 | **Two keys: C and D** | C4 D4 | C then D, in order, three times through. |
| 3 | **Mix it up** | C4 D4 | Same two keys in **random** order, eight prompts. This is the level that proves the child reads the prompt instead of repeating a pattern. |
| 4 | **Three keys: E joins** | C4 D4 E4 | Four in order, then eight shuffled. |
| 5 | **Four keys: F joins** | C4–F4 | Four in order, then eight shuffled. |
| 6 | **All five: G joins** | C4–G4 | Five in order, then ten shuffled. |
| 7 | **Song time** | whatever is unlocked | Play a tune, note by note, at the child's own pace. |
| 8 | **Falling notes** | C4–G4 | The game: notes drift down to a line, the child plays them as they land. |

Order matches the request: one key → two keys → jumbled two keys → third key → … → falling game.

### Unlock and stars

A level unlocks when the one before it earns at least one star. Stars are generous on purpose:

- ★ finished the level (always awarded on completion)
- ★★ two or fewer wrong keys
- ★★★ no wrong keys

**Unclear audio, two notes at once, and too-quiet never cost a star and are never "wrong."** That rule from the master spec holds in every level, including the game.

There is no lose state, no timer on levels 1–7, no lives. A child can stay on a level forever and the app keeps answering.

## Feedback on every single key press

This is a contract, not a nicety. Every press the engine resolves produces exactly one visible answer, within the same frame it is judged:

| What the child played | Screen | Words |
| --- | --- | --- |
| The asked key | Key flashes its colour, star pops | `Yes! Now D4.` |
| A different white key | **The key they actually pressed flashes amber on the on-screen keyboard**, target keeps glowing | `That was E4. Press C4.` |
| Right letter, wrong octave | Both keys marked on the mini-map | `That was C5. Press C4.` |
| Unclear, two notes, too quiet | Soft pulse, nothing marked wrong | `I didn't catch that. Try again.` |
| Nothing for 5 s | Target key pulses | `Press the red C4 key.` |

Showing the **wrong key they hit** is new. Today the app only names it. Lighting it up next to the target is what teaches a child the distance between C and E.

## Key colours

Each of the five keys gets a fixed colour, used identically on the keyboard guide, the song strip, the falling notes, and the stars. Same convention as coloured chime bars, so paper stickers on the real piano match the screen.

| Key | Colour |
| --- | --- |
| C4 | red `#E5484D` |
| D4 | orange `#F76B15` |
| E4 | yellow `#E8B931` |
| F4 | green `#2FA84F` |
| G4 | blue `#3A7DDE` |

Colour is never the only signal — every key also carries its name (`C4`) and its position.

## Songs

A song is offered at the end of a session when **every note in it** is inside the keys the child has unlocked. All tunes are traditional or original; nothing licensed.

| Song | Keys needed | Notes | Unlocks after |
| --- | --- | --- | --- |
| Hot Cross Buns | C D E | 20 | Level 4 |
| Au Clair de la Lune | C D E | 11 | Level 4 |
| Step Up (warm-up) | C D E F | 8 | Level 5 |
| Are You Sleeping (first half) | C D E F G | 14 | Level 6 |
| Ode to Joy (first line) | C D E F G | 15 | Level 6 |
| Mary Had a Little Lamb | C D E G | 26 | Level 6 |

Exact melodies (MIDI, octave 4 throughout):

```
Hot Cross Buns     64 62 60 | 64 62 60 | 60 60 60 60 62 62 62 62 | 64 62 60
Au Clair de la Lune 60 60 60 62 | 64 62 | 60 64 62 62 60
Step Up            60 62 64 65 | 65 64 62 60
Are You Sleeping   60 62 64 60 | 60 62 64 60 | 64 65 67 | 64 65 67
Ode to Joy         64 64 65 67 67 65 64 62 | 60 60 62 64 | 64 62 62
Mary Had a Little Lamb
                   64 62 60 62 64 64 64 | 62 62 62 | 64 67 67 |
                   64 62 60 62 64 64 64 64 62 62 64 62 60
```

**Songs have no rhythm requirement.** The child plays the next note whenever they are ready; the strip advances on the correct note. A wrong note does not advance and does not end anything — it gets the same per-press feedback as a drill. Rhythm is the falling game's job, not a five-year-old's first tune.

Each note carries its **lyric syllable**, printed under the note, so "Hot cross buns" reads as words and not as `E D C`.

## The falling-notes game (Level 8)

Coloured note tiles drift from the top of the screen down to a **hit line** just above the on-screen keyboard. When a tile touches the line, the child plays that key on the real piano.

- Fall time **3.0 s** per tile — slow enough to read the letter and find the key.
- Hit window **±700 ms** around the moment the tile reaches the line. That is deliberately wide: microphone capture, pitch detection, and a child's hand all add delay, and a game that punishes system latency is just a broken game.
- Tiles arrive one at a time at first (**1 tile per 2.5 s**), never overlapping in the first run.
- A tile the child misses fades out and **comes back once** at the end of the run. It is not a life lost.
- A wrong key during the window is named (`That was E4`) and the tile keeps falling until its window closes.
- Unclear or two-note audio is ignored completely — it neither hits nor misses.
- 12 tiles per run, then stars, then the same song offer as any other session.

There is no failure, no health bar, and no sudden-death. The game is a rhythm *reward*, not a test.

## Screens

Prototype: `docs/ui-mockups/kids-prototype.html`

1. **Home** — big Play button, the child's star total, Grown-ups link.
2. **Level map** — eight rounded stops on a winding path, each with its stars, locked ones dimmed with a small padlock. Tapping an unlocked stop starts it.
3. **Drill** (levels 1–6) — target note name huge and in the key's colour, cue badge, feedback line, progress pips, colour-coded 61-key mini-map + zoom strip with the target lit and any wrong press flashed amber.
4. **Song** — horizontal strip of coloured note bubbles with lyric syllables underneath, current note enlarged, played notes ticked; same keyboard below.
5. **Falling game** — three or four lanes of coloured tiles descending to a bright hit line sitting directly on top of the keyboard, combo counter, no health bar.
6. **Session summary** — stars earned with a burst, what was learned in one line, `Play a song` / `Play again` / `Level map`.

Visual language: cream background, rounded 20 dp cards, one accent purple for chrome, the five key colours for anything musical, large touch targets, and no tiny text anywhere a child looks.

## Engine

New JVM module **`:core:curriculum`** (no Android types, same testing pattern as `:core:learning`):

- `LevelSpec` / `curriculum()` — the eight levels above as data.
- `DrillSession` — generalises today's `LessonSession` to sequence **or** seeded shuffle, and adds "which key did they actually press" to the snapshot.
- `LevelScore` — star rule, with unclear excluded.
- `Song` + `songLibrary()` + `songsFor(unlockedKeys)` — melody, syllables, key requirements.
- `SongSession` — rhythm-free progression with the same feedback contract.
- `FallingGameSession` — pure function of a clock: spawn schedule, hit windows, hit/miss/late, requeue-once.
- `ProgressStore` (Android side) — stars per level and highest unlocked, in the existing prefs.

Recognition, calibration, the held-note lock (`judgedMidi`), and the export files are untouched except that level/song/game events are appended to the session log so a run can be analysed from the exported JSONL.

## What we are not building

Characters or a mascot, streaks and daily goals, cloud sync or accounts, sheet-music notation, black keys, chords, two-hand play, tempo control, any tune that needs a key outside C4–G4, in-app audio playback of the songs (the piano is the instrument), and any fail state.

## Dependency

Ships as **0.6.0**, on top of 0.5.0. The coloured keyboard here is the 0.5.0 `PianoKeyboardView` with a colour parameter added, not a second keyboard implementation.
