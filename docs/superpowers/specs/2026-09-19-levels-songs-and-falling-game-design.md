# Levels, songs, and the falling-notes game

Same Android app. Builds on 0.5.0 (note-name hero, 61-key guide, Calibrate dropdown, export). This slice turns one practice screen into a **ladder a child can climb**, ends every session with a **real tune**, and adds the **falling-notes game** at the top.

Vocabulary stays **five white keys, C4–G4**. Everything below is built from those five keys and nothing else.

**Shipping in two steps.** The ladder and the songs are 0.6.0. The falling game is 0.7.0. The reason is in [Why the game ships second](#why-the-game-ships-second) — it is the first feature in this app whose correctness depends on a number no unit test can produce, and 0.6.0 is what produces it.

## Why the ladder

0.5.0 Practice asks for C4, then D4, then E4, F4, G4 — five different keys on the child's very first run. That is five new motor skills in one sitting. A four-year-old needs to own **one** key before the second one means anything, and needs to hear the same key come back in a different order before it is really learned.

## The ladder

Eight levels. Each is short. No level can be failed.

| # | Name | Keys | Prompts | What happens |
| --- | --- | --- | --- | --- |
| 1 | **One key: C** | C4 | 6 | Press C4 six times. The only key lit. Builds "this key is C." |
| 2 | **Two keys: C and D** | C4 D4 | 6 | C then D, three times through. |
| 3 | **Mix it up** | C4 D4 | 8 | Same two keys in **random** order. This is the level that proves the child reads the prompt instead of repeating a pattern. |
| 4 | **Three keys: E joins** | C4 D4 E4 | 3 + 5 | Three in order, then five shuffled. |
| 5 | **Four keys: F joins** | C4–F4 | 4 + 5 | Four in order, then five shuffled. |
| 6 | **All five: G joins** | C4–G4 | 5 + 6 | Five in order, then six shuffled. |
| 7 | **Song time** | whatever is unlocked | song | Play a tune, note by note, at the child's own pace. |
| 8 | **Falling notes** (0.7.0) | C4–G4 | 12 tiles | The game: notes drift down to a line, the child plays them as they land. |

Order matches the request: one key → two keys → jumbled two keys → third key → … → falling game.

Prompt counts are capped at 11. A beginner hunting for a key on a real piano takes 8–20 seconds per prompt, and detection adds a fifth of a second on top, so 11 prompts is already 1.5–3.5 minutes. Fifteen would be five.

### The jumble rule

A shuffled run is **balanced** — each key of the level appears the same number of times, give or take one — and **never asks for the same key three times in a row**. Both matter. Without balance, level 6 could ask for C five times and E once. Without the anti-triple rule, a child can pass by hammering one key and never read the prompt. The rule spans the ordered prefix too: level 4's `C D E` prefix cannot be followed by `E E`.

### Unlock and stars

A level unlocks when the one before it earns at least one star. Stars scale with how long the level is, so the top of the ladder is not stingier than the bottom:

| Stars | Rule |
| --- | --- |
| ★★★ | every prompt answered, and at most `prompts / 10` wrong |
| ★★ | at most `max(2, prompts / 3)` wrong |
| ★ | at least **80%** of the prompts answered |
| none | stopped before 80% |

The 80% rule is deliberate. A tired child who stops at prompt 9 of 11 still banks a star and still reaches the song. Without it, an all-or-nothing completion star is the one real fail state in a design that claims to have none.

**Unclear audio, two notes at once, and too-quiet never cost a star and are never "wrong."** That rule from the master spec holds in every level, including the game.

There is no lose state, no timer on levels 1–7, no lives. A child can stay on a level forever and the app keeps answering.

## Feedback on every single key press

This is a contract, not a nicety. Every press the engine resolves produces exactly one visible answer, within the same frame it is judged. These are the exact strings; 0.6.0 owns making every one of them true, because 0.5.0 leaves the wrong-key case as `Try C.` with no octave.

| What the child played | Screen | Words |
| --- | --- | --- |
| The asked key, more to come | Key flashes its colour, star pops | `Yes! Now D4.` |
| The asked key, and the next prompt is the same key | Same | `Yes! C4 again.` |
| The asked key, last prompt | Star burst | `Level 3 done!` / `You played Hot Cross Buns!` |
| A different white key | **The key they actually pressed flashes amber on the on-screen keyboard**, target keeps glowing | `That was E4. Press C4.` |
| Right letter, wrong octave | Both keys marked on the mini-map | `That was C5. Press C4.` |
| Unclear, two notes, too quiet | Soft pulse, nothing marked wrong | `I couldn't hear that. Try again.` |
| **A press too short to identify** | Soft pulse | `I heard something. Hold the key a bit longer.` |
| Nothing yet | Target key pulses | `Play the C4 key.` |

Two of these rows are new and both were missing from the first draft of this design.

**Showing the wrong key they hit** is the first. Today the app only names it. Lighting it up next to the target is what teaches a child the distance between C and E. It has one non-obvious requirement: the highlight must **outlive the release**. The engine drops its internal note lock about 190 ms after a finger lifts, so a highlight driven directly off that lock would vanish before the child had a chance to look at it. The flash is its own state, and it persists until the next press is judged.

**The too-short press** is the second, and without it the promise of "feedback on every key press" is simply false. Recognition needs roughly 140 ms of steady pitch to name a note. A four-year-old's exploratory poke does not produce that, so the app would say nothing at all — and because the previous answer is still on screen, it looks to the child like the app ignored them. Now a poke gets an answer telling them what to do differently.

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
| Step Up (warm-up) | C D E F | 8 | Level 5 |
| Au Clair de la Lune | C D E | 11 | Level 4 |
| Are You Sleeping (first half) | C D E F G | 14 | Level 6 |
| Ode to Joy (first line) | C D E F G | 15 | Level 6 |
| Hot Cross Buns | C D E | 17 | Level 4 |
| Mary Had a Little Lamb | C D E G | 26 | Level 6 |

Listed shortest first, which is the order they are offered in.

Exact melodies (MIDI, octave 4 throughout):

```
Step Up             60 62 64 65 | 65 64 62 60
Au Clair de la Lune 60 60 60 62 | 64 62 | 60 64 62 62 60
Are You Sleeping    60 62 64 60 | 60 62 64 60 | 64 65 67 | 64 65 67
Ode to Joy          64 64 65 67 67 65 64 62 | 60 60 62 64 | 64 62 62
Hot Cross Buns      64 62 60 | 64 62 60 | 60 60 60 60 62 62 62 62 | 64 62 60
Mary Had a Little Lamb
                    64 62 60 62 64 64 64 | 62 62 62 | 64 67 67 |
                    64 62 60 62 64 64 64 64 62 62 64 62 60
```

Two of these are deliberately partial. **Are You Sleeping** stops after `E F G / E F G` because the next phrase ("Morning bells are ringing") needs A4, which we never teach. **Ode to Joy** is the first line only, for the same reason. Both stop at a musically complete phrase, so they do not sound truncated to a child.

**Songs have no rhythm requirement.** The child plays the next note whenever they are ready; the strip advances on the correct note. A wrong note does not advance and does not end anything — it gets the same per-press feedback as a drill. Rhythm is the falling game's job, not a five-year-old's first tune.

Each note carries its **lyric syllable**, printed under the note, so "Hot cross buns" reads as words and not as `E D C`.

Rights: Hot Cross Buns (English street cry, printed 1798), Au Clair de la Lune (French traditional, 18th c.), Are You Sleeping / Frère Jacques (French traditional, 18th c.) and Mary Had a Little Lamb (Hale 1830, to the traditional *Merrily We Roll Along*) are all public domain. Ode to Joy is Beethoven, 1824. Its English words here are Henry van Dyke's *Joyful, Joyful, We Adore Thee*, published 1907 and public domain in the US. Step Up is original to this app.

## The falling-notes game (level 8, ships as 0.7.0)

Coloured note tiles drift from the top of the screen down to a **hit line** just above the on-screen keyboard. Five lanes, one per key, left to right in pitch order — that is the only lane mapping a child can use without being told. When a tile touches the line, the child plays that key on the real piano.

- Fall time **3.0 s** per tile — slow enough to read the letter and find the key.
- Tiles arrive every **2.5 s**. Two tiles share the screen for about half a second; the hit windows never touch, with 1.1 s of dead air between them.
- **The hit window is asymmetric: 450 ms early, 900 ms late.** See below for why it is not ±700 ms.
- The tile stays drawn past the line, fading, until its window closes. It must not vanish at the moment the child is most likely to finally press.
- A tile the child misses fades out and **comes back once, appended after the last tile of the run**. Not requeued nearby — a nearby requeue lands its window on top of a live tile, and a struggling child misses several in a row, so the collisions stack.
- A wrong key inside the window is named (`That was E4`) and the tile keeps falling until its window closes.
- Unclear or two-note audio is ignored completely — it neither hits nor misses, and it does not consume the tile.
- A press when no window is open is ignored, not scolded. A child noodling between tiles is not doing anything wrong.
- 12 tiles per run, then stars, then the same song offer as any other session.

The counter on screen counts **catches only** — "★ 7 caught". It never shows a miss tally. A visible failure count is a health bar with extra steps.

Game stars come from catches, not from wrong notes: **12 of 12 → ★★★, 8 or more → ★★, any run finished → ★**. Scoring the game on wrong presses alone would hand three stars to a child who stood in silence for the whole run, which is the opposite of no-fail: no-fail means failure is never punished, not that doing nothing is rewarded maximally.

There is no failure, no health bar, and no sudden-death. The game is a rhythm *reward*, not a test.

### Why the window is 450 ms early / 900 ms late

The app hears the piano through a microphone, and everything between the hammer and the judgement costs time:

| Stage | Cost |
| --- | --- |
| Sound travel, microphone, ADC, Android capture buffer | ~30 ms |
| First 2048-sample block that ends after the onset and carries usable pitch | 46–93 ms |
| Two more agreeing frames to reach a stable note (`stableFrames = 3`) | 93 ms |
| Pitch detection compute | ~10 ms |
| **Typical total** | **~200 ms**, and 250–300 ms when the attack transient costs a frame |

Detection latency is strictly **positive**. The app is never early. A window centred on the detection timestamp therefore gives the child about 950 ms of early grace and only 450 ms of late grace — and beginners are late, not early. Shifting the window to 450 early / 900 late puts the generosity where children actually need it.

### Why the game ships second

Everything above is arithmetic, not measurement. 0.5.0 already writes `session.jsonl` with a timestamp on every judged press, and 0.6.0 adds the key that was actually heard. So: ship the ladder and the songs, let a real child use them next to the real PSR-F52 on the real phone, export the log, and read the true onset-to-judgement distribution off it. Then set the window from data.

`docs/android/ACCEPTANCE_CRITERIA.md` already lists end-to-end acoustic-to-display latency under "not claimed by these tests". The falling game is the first feature whose correctness *depends* on that number, and it is the one number no unit test in this repo can produce.

## Screens

Prototype: `docs/ui-mockups/kids-prototype.html`

1. **Home** — big Play button, the child's star total, Grown-ups link.
2. **Level map** — rounded stops on a winding path, each with its stars, locked ones dimmed with a small padlock. Tapping an unlocked stop starts it.
3. **Drill** (levels 1–6) — target note name huge and in the key's colour, cue badge, feedback line, progress pips, colour-coded 61-key mini-map + zoom strip with the target lit and any wrong press flashed amber.
4. **Song** — horizontal strip of coloured note bubbles with lyric syllables underneath, current note enlarged, played notes ticked; same keyboard below.
5. **Falling game** — five lanes of coloured tiles descending to a bright hit line sitting directly on top of the keyboard, catch counter, no health bar.
6. **Session summary** — stars earned with a burst, what was learned in one line, `Play a song` / `Play again` / `Level map`.

Visual language: cream background, rounded 20 dp cards, one accent purple for chrome, the five key colours for anything musical, large touch targets, and no tiny text anywhere a child looks.

## Engine

New JVM module **`:core:curriculum`**, depending on **`:core:notes` and nothing else**:

- `LevelSpec` / `curriculum()` — the ladder as data.
- `promptsFor(level, seed)` — the balanced, anti-triple prompt list.
- `starsFor(answered, prompts, wrong)` — the star rule.
- `Song` + `songLibrary()` + `songsFor(unlockedKeys)` — melody, syllables, key requirements.
- `highestUnlocked(starsByLevel)` — the unlock rule.

`LessonSession` in `:core:learning` **stays the single judging engine for levels 1–7**. A level and a song are both just an ordered prompt list, including one with repeats, so nothing about the judging changes. It gains a sticky "key actually heard", a per-session ending line, and the two new feedback cases above.

The falling game (0.7.0) needs two new pieces:

- **`PressStream`**, extracted from `LessonSession`'s frame loop into `:core:pitch`: `onFrame(AudioFrame): Press?`, one `Press(midi, atMs)` per physical press and never one for ambiguous audio. `LessonSession` delegates to it, so its existing acceptance tests become the regression net for the extraction. Without this, the game's ViewModel becomes a fourth hand-written copy of the unclear-guard and the held-note lock, in the one layer this repo has no tests for.
- **`FallingGameSession`** — a pure clock over an append-only tile schedule with unique ids.

`ProgressStore` (Android side) keeps stars per level and the highest unlocked level in the existing prefs. Recognition, calibration, the held-note lock, and the export files are untouched except that level, song and game events are appended to the session log so a run can be analysed from the exported JSONL.

## What we are not building

Characters or a mascot, streaks and daily goals, cloud sync or accounts, sheet-music notation, black keys, chords, two-hand play, tempo control, any tune that needs a key outside C4–G4, in-app audio playback of the songs (the piano is the instrument), and any fail state.
