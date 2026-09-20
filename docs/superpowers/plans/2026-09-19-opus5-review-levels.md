# Opus 5 review of the level ladder plan — what changed

Reviewer: Opus 5, read-only, against the live Kotlin in `:core:learning` and `:core:pitch`.
Verdict: **Yes with fixes.** Every fix below is in the spec and the two plans; none of it is code yet.

The architectural bet was confirmed against the real source: `LessonSession` genuinely handles a prompt list with repeats (`expectedMidi` is `notes.getOrNull(index)`, `complete` is `index >= notes.size`, nothing assumes five distinct entries), and the held-note lock is press-shaped rather than prompt-shaped, so feeding it `[60,60,60,60,60,60]` is safe. All six melodies were verified note by note and confirmed public domain. What went wrong was in the tests and in the game's timing model.

## The plan was split in two

Thirteen tasks with six Compose screens, a new module, a new timing model and a new judging path is not one reviewable increment, and the falling game carried nearly all of the risk.

- **0.6.0 — `2026-09-19-level-ladder-and-songs.md`.** Seven levels, songs, all the feedback work. Every piece is a prompt list through the already-trusted engine; nothing needs a clock.
- **0.7.0 — `2026-09-19-falling-notes-game.md`.** The press-detector extraction, then the game.

The cut line buys a measurement. 0.6.0 writes `session.jsonl` with a timestamp and the key actually heard on every judged press, so the real onset-to-judgement distribution can be read off an exported log from the actual PSR-F52 and the hit window set from data instead of arithmetic. `docs/android/ACCEPTANCE_CRITERIA.md` already lists end-to-end acoustic latency as unclaimed by any test; the falling game is the first feature whose correctness depends on it.

## Critical — nine, all fixed

| # | What was wrong | Fix |
| --- | --- | --- |
| C1 | `acLearn14`, the single test guarding the held-note lock, asserted the wrong value and would have failed on first run. It called `playPress` and called it "holding", but `playPress` ends with 4 silent frames and `releaseFrames = 4`, so it *releases*: the second press is judged and `completedCount` is 2, not 1. | Rewritten with `hold` (8 contiguous frames, no release) for the held case and `playPress` for the genuine second press. It now fails in both directions — remove the lock and the middle assertion breaks; make the lock permanent and the last one does. A table of what each existing helper actually does was added at the top of the plan. |
| C2 | The plan's own `promptsFor` failed the plan's own `acPro04`. The shuffle could not see the ordered prefix, so level 4's `C D E` followed by a shuffle starting `E E` is a triple — 1/9 per seed, which a 200-seed sweep catches with probability ≈ 1. | `jumble` takes `precededBy` and is seeded with the prefix tail. The random draw was also replaced with a balanced bag drained most-plentiful-first, so "every key at least once" is structural instead of a fallback, and a run of five Cs and one E is impossible. |
| C3 | `:core:curriculum` could not compile its own `StarsTest`. Task 1 excluded `:core:pitch` and `:core:calibration`, and `:core:learning` uses `implementation`, so nothing leaks transitively — but Kotlin must resolve every parameter type of the `LessonSession` constructor. Task 5 also implied a dependency cycle. | `:core:curriculum` now depends on `:core:notes` and nothing else. The one test needing both audio helpers and the engine moved to `:core:learning`, where they already live. |
| C4 | The plan told the worker to copy a `silentFrame` helper and not to invent one. That helper does not exist anywhere in the repo. | Named the real construct, `AudioFrame(FloatArray(2048), SR, index * FRAME_MS)`, and documented the full helper inventory with line numbers. The test also switched from silence to genuinely ambiguous audio, because silence is `NO_SIGNAL` and never reaches the judging branch — it proved nothing about the unclear rule. |
| C5 | `heardMidi = judgedMidi` would make the amber flash vanish before it finished. `judgedMidi` is cleared the instant the debouncer reaches IDLE, ~190 ms after the finger lifts, against a 600 ms flash. The feature's whole point is defeated. | `heardMidi` is its own sticky field, cleared only by `restart()`, with an assertion that it survives six frames of silence after the press. |
| C6 | Nothing owned the frame loop for the game. The detector, debouncer, ambiguity guard and held-note lock all live *inside* `LessonSession.onFrame`, and the game's ViewModel would have become a fourth hand-written copy — in the one layer this repo has no tests for, carrying the two hardest safety rules in the app. | `PressStream` is extracted into `:core:pitch` as Task 0 of the game plan, with the existing AC-LEARN and AC-DEB suites as the regression net and a rule that not one of them may be edited. |
| C7 | A child who stood in silence through a whole game run scored **three stars** — `starsFor` keyed only on wrong presses, and `acGame07` asserted `complete` and `hitCount == 0` in the same breath while calling it "no fail state". | Game stars come from catches: 12 → ★★★, 8+ → ★★, 1+ → ★, none → no stars. No-fail means failure is never punished, not that inaction is rewarded maximally. |
| C8 | Requeued tiles collided with live ones. Requeue at `nowMs + gapMs` puts the second chance's window 680 ms on top of the next tile's, the plan never said which tile a press resolves against, and consecutive misses — what a struggling child actually produces — stack the collisions. | Second chances are appended after the last tile of the run, spaced one at a time so a batch of expiries cannot pile onto one millisecond. `acGame07` now asserts that no two outstanding tiles ever have overlapping windows, across a whole simulated run. |
| C9 | `val tiles: List<FallingTile>` in the interface block cannot grow, but the plan's own test required it to — and two entries with the same id throw in any `LazyRow` keyed on it. | The published API is an append-only `schedule` with ids unique for the whole run, plus `secondChanceFor`, which makes requeue-once a structural invariant rather than a bookkeeping convention. |

## Important — fourteen, all addressed

Detection latency was computed from the live code rather than guessed: ~30 ms of capture, 46–93 ms for the first usable 2048-sample block, **92.9 ms** for `stableFrames = 3`, ~10 ms of compute, so ~200 ms typically and 250–300 ms with a lost attack frame. The magnitude was fine; the **shape** was wrong. Latency is strictly positive, so a symmetric ±700 ms window gives a child ~950 ms of early grace and only ~450 ms late — and beginners are late. The window is now **450 ms early / 900 ms late**, compensated once rather than twice, with both boundaries pinned (the old `acGame02` pressed 600 ms out against a 700 ms window, so anything from 600 upward passed, including 5000).

Also fixed: a fresh seed on "Play again", so level 3 does not replay the identical order on the one level whose purpose is unpredictability; the hardcoded `"Play the C key."` opening line, which is wrong the moment a shuffled level or Ode to Joy starts on something other than C; the `lessonSessionFor` signature, since it is the only place the calibrated A4 reaches the detector and bypassing it would silently disable calibration with no test to catch it; **a press too short to name now gets an answer** (`I heard something. Hold the key a bit longer.`) — recognition needs ~139 ms of steady pitch and a child's poke does not produce that, so the app said nothing at all and the previous answer stayed on screen, which made the promise of feedback on every press false; star thresholds that scale with level length; an 80% completion rule so quitting at prompt 9 of 11 still banks a star and still unlocks the song, which was the design's one genuine fail state; a lane definition (`FIVE_KEYS.indexOf(midi)`); tiles that stay drawn past the line, where over a third of the window lives; `require(notes.isNotEmpty())`, guarding the same `NaN`-into-Compose class of bug that AC-METER-04 already exists for; and an AC row for every one of the 30 + 18 new tests, not 17 of them.

## Minor

Spec table said Hot Cross Buns had 20 notes (it has 17) and that level 4 plays "four in order" (it plays three). `promptCount` became `shufflePrompts` and level 1 now reads `passes`, removing a dead field. The ticker constant is 250 ms in all three existing ViewModels, not 60, and rendering now runs off `withFrameMillis` instead — 60 ms is 17 fps and would look steppy. A repeated prompt says `Yes! C4 again.` instead of `Yes! Now C4.` five times running. The game header counts catches only; a miss tally is a health bar with extra steps. Test snippets all carry their package, imports and class header, because workers copy them literally.

## Test-teeth audit

Against the repo's own standard — every test must fail under a plausible mutation. Of the 37 originally planned: 2 would have failed on first run, 1 would not have compiled, 3 were rubber stamps, and 4 were materially weaker than the plan claimed. The rest were sound.

Rewritten: `acLearn14` (C1), `acStar04` → `acStar05` (C3/C4, and now uses real ambiguous audio), `acSong06` (was tautological — the function ends in the very sort the test asserted, and deleting the sort still passed; now pins the exact id order), `acGame07` → `acGame08` (was "tick 200 times and nothing threw", which cannot detect a fail state being added and was blind to the three-stars-for-silence bug).

Strengthened: `acGame02` pins both boundaries to the millisecond; `acGame05` now asserts the tile is still catchable after unclear audio, so a mutation where unclear burns the tile fails; `acSong05` writes out all six melodies in full as an independent second transcription, because `take(3)` cannot catch a corrupted note 10; `acPro03` pins balance instead of asserting inequality against one specific list; `acLvl03` pins the prompt count instead of `>= 8`; `acLvl06` pins all five colour values, so swapping orange and yellow now fails; `acLvl07` adds a gap case, so stars on level 3 cannot unlock level 4 while level 2 is unstarred.

Added: `heardMidi` outliving the release (C5), `require(notes.isNotEmpty())`, the window-overlap invariant across a whole run, `acPress06` pinning the detection timestamp that every timing number is measured against, and `acGame10` requiring at least four distinct keys and no triples in a tile run.

## Pedagogy

Level 6 was 15 prompts over five keys — at a realistic 8–20 seconds per prompt for a beginner hunting for a key on a real piano, that is 2–5 minutes against a spec that claimed 30–90 seconds. Every level is now capped at 11 prompts, and `acLvl04` enforces it. Allowing two of the same key in a row but never three was confirmed as the right call. Game tempo (3 s fall, 2.5 s gap) was judged well; the problem there was window shape, not speed.

## Not adopted

Nothing. Every finding was either fixed or folded into the split.
