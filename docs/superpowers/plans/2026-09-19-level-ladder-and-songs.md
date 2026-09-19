# Level Ladder and Songs Implementation Plan (0.6.0)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn one practice screen into a seven-level ladder (1 key → 2 keys → shuffled → 3, 4, 5 keys → song), answer **every** key press on screen including the ones too short to name, and end each session with a real tune playable on C4–G4.

The falling-notes game is level 8 and ships separately as 0.7.0 — see `docs/superpowers/plans/2026-09-19-falling-notes-game.md` for why, and do not build any of it here.

**Architecture:** One new JVM module `:core:curriculum`, depending on `:core:notes` **and nothing else**, holds level data, prompt generation, star scoring and the song library — all pure data and arithmetic. **`LessonSession` stays the single judging engine** — a level or a song is just an ordered *prompt list* fed to its existing `notes` parameter, so the `judgedMidi` held-note lock, the never-mark-unclear-wrong rule, and all existing AC-LEARN tests keep working. Compose gains three screens that read those pure models.

**Tech Stack:** Kotlin, JUnit 5, Jetpack Compose Material 3. No new third-party libraries, no audio playback, no animation library beyond Compose's own `animate*AsState`.

## Global Constraints

- Ships as **0.6.0** / `versionCode 6`, on top of 0.5.0 (`docs/superpowers/plans/2026-09-19-keyboard-guide-and-export.md`). Do not start until 0.5.0 is merged.
- Package id stays `com.vijaychhetry.kidspiano`.
- Musical vocabulary is exactly **C4 D4 E4 F4 G4** (MIDI 60, 62, 64, 65, 67). No note outside that set appears anywhere.
- Unclear / two notes / too quiet is **never** `INCORRECT`, never costs a star.
- The held-note lock (`judgedMidi` until `IDLE`) is not touched. Any change that lets a held correct note be re-scored is a bug. `LessonSession.kt` line 93 (`if (phase == NotePhase.IDLE) judgedMidi = null`) and line 108 (`phase == NotePhase.STABLE && pitch.midiNote != judgedMidi`) are the two lines that implement it.
- `:core:curriculum` must not depend on `:core:learning`, `:core:pitch` or `:core:calibration`. `:core:learning` already depends on all three, and Task 5 makes `:core:learning` the consumer of curriculum types; a dependency in the other direction is a cycle.
- No fail state anywhere. No lives, no timer.
- Prompt counts are capped at 11 per level.
- Key colours are fixed: C `#E5484D`, D `#F76B15`, E `#E8B931`, F `#2FA84F`, G `#3A7DDE`. Colour is never the only signal.
- Songs are rhythm-free. The strip advances on the correct note, whenever it comes.
- Reuse the 0.5.0 `PianoKeyboardView`; add parameters, do not write a second keyboard.
- Tests first: write the failing test, run it, watch it fail, then implement. **Every new test must fail under a plausible mutation** — the standard in `docs/android/ACCEPTANCE_CRITERIA.md` under "How we know these tests have teeth". A test that passes against a broken implementation is worse than no test.
- Debug APK goes to `releases/KidsPiano-debug-0.6.0.apk`.
- Do not implement until the human approves the screens in `docs/ui-mockups/`.

## Existing test helpers — read these before writing any test

In `android/core/learning/src/test/kotlin/com/vijaychhetry/kidspiano/core/learning/LessonSessionTest.kt`:

| Helper | Line | What it actually does |
| --- | --- | --- |
| `play(session, midi, atFrame)` | 212 | 8 contiguous tone frames. **Does not release.** |
| `hold(session, midi, atFrame)` | 221 | Alias for `play`. **Does not release.** |
| `playPress(session, midi, fromFrame)` | 224 | 8 tone frames **then 4 silent frames**. `releaseFrames = 4`, so this *does* release: the debouncer reaches IDLE and `judgedMidi` is cleared. |
| `playHz(session, hz, atFrame)` | 215 | As `play`, at an arbitrary frequency. |
| `frame(hz, index)` | 236 | One `AudioFrame` of `pianoTone`. |
| silence | — | There is **no** `silentFrame` helper. Write `AudioFrame(FloatArray(2048), SR, index * FRAME_MS)`, as `acLearn04` does at line 93. |
| genuinely ambiguous audio | 107 | `mixTones(sineTone(midiToFreq(60), SR, 0.6, amplitude = 0.35f), sineTone(midiToFreq(64), …))`, as `acLearn05` does. Silence is `NO_SIGNAL`, **not** ambiguous — it never reaches the judging branch, so it proves nothing about the unclear rule. |

Constants in that file: `SR = 44100`, `FRAME_MS = 46L`, `PRESS_FRAMES = 12`.

Every test snippet below is given with its `package`, imports and class header, because workers copy them literally.

## File map

- Create: `android/core/curriculum/build.gradle.kts`
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Levels.kt`, `Prompts.kt`, `Stars.kt`, `Songs.kt`
- Create: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/LevelsTest.kt`, `PromptsTest.kt`, `StarsTest.kt`, `SongsTest.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/progress/ProgressStore.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/levels/LevelMapScreen.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/song/SongScreen.kt`, `SongViewModel.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/summary/SummaryScreen.kt`
- Modify: `android/settings.gradle.kts` — `include(":core:curriculum")`
- Modify: `android/core/learning/build.gradle.kts` — depend on `:core:curriculum`
- Modify: `android/core/learning/.../LessonSession.kt` and `LessonSessionTest.kt`
- Modify: `android/app/.../lesson/LessonScreen.kt`, `LessonViewModel.kt`
- Modify: `android/app/.../ui/PianoKeyboardView.kt`
- Modify: `android/app/.../home/HomeScreen.kt`, `MainActivity.kt`
- Modify: `android/app/build.gradle.kts`, `docs/android/ACCEPTANCE_CRITERIA.md`, `android/README.md`, `releases/README.md`

## Interfaces

```kotlin
// :core:curriculum — Levels.kt
enum class LevelKind { SINGLE, SEQUENCE, SHUFFLE, SEQUENCE_THEN_SHUFFLE, SONG }

data class LevelSpec(
    val id: Int,
    val title: String,
    val keys: List<Int>,
    val kind: LevelKind,
    val shufflePrompts: Int,   // prompts in the shuffled part; 0 when there is none
    val passes: Int = 1,       // times through the ordered part
)

fun curriculum(): List<LevelSpec>
fun levelById(id: Int): LevelSpec
fun keysUnlockedAfter(levelId: Int): List<Int>
fun highestUnlocked(starsByLevel: Map<Int, Int>): Int

const val KEY_C4 = 60; const val KEY_D4 = 62; const val KEY_E4 = 64
const val KEY_F4 = 65; const val KEY_G4 = 67
val FIVE_KEYS: List<Int> = listOf(60, 62, 64, 65, 67)
fun keyColorHex(midi: Int): String

// Prompts.kt
fun promptsFor(level: LevelSpec, seed: Long): List<Int>

// Stars.kt
fun starsFor(answered: Int, prompts: Int, wrongPresses: Int): Int  // 0..3

// Songs.kt
data class Song(val id: String, val title: String, val midi: List<Int>, val syllables: List<String>)
fun songLibrary(): List<Song>
fun songsFor(unlockedKeys: List<Int>): List<Song>
```

Changes to existing types:

```kotlin
// LessonSnapshot gains one field
data class LessonSnapshot(
    val expectedMidi: Int?,
    val heardMidi: Int?,      // NEW: sticky — the key judged on the LAST press,
                              // null only before the first press and after restart()
    …
)

// LessonSession gains two parameters
class LessonSession(
    private val notes: List<Int> = MVP_CALIBRATION_MIDI,   // now read as "ordered prompts"
    private val completionCopy: String = "You found all five keys!",
    …
) {
    init { require(notes.isNotEmpty()) { "a lesson needs at least one prompt" } }
}

// The factory grows the same parameter. 0.5.0 already added `notes`.
// This is the ONLY way to build a session — it is where the calibrated A4 is
// threaded into both the detector and the validator. Never call the
// constructor directly from app code.
fun lessonSessionFor(
    profile: CalibrationProfile,
    notes: List<Int> = defaultLessonMidi(),
    completionCopy: String = "You found all five keys!",
): LessonSession

// PianoKeyboardView — one spelling, used everywhere
@Composable
fun PianoKeyboardView(
    highlightMidi: Int?,
    modifier: Modifier = Modifier,
    flashMidi: Int? = null,
    coloredMidi: List<Int> = emptyList(),
)
```

---

### Task 1: Curriculum module and the ladder

**Files:**
- Create: `android/core/curriculum/build.gradle.kts`
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Levels.kt`
- Test: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/LevelsTest.kt`
- Modify: `android/settings.gradle.kts` — add `include(":core:curriculum")` next to `include(":core:learning")`, outside the SDK-gated block

**Interfaces:**
- Consumes: nothing. Copy `android/core/notes/build.gradle.kts` and keep only `implementation(project(":core:notes"))`.
- Produces: `LevelSpec`, `LevelKind`, `curriculum()`, `levelById`, `keysUnlockedAfter`, `highestUnlocked`, `FIVE_KEYS`, `keyColorHex`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vijaychhetry.kidspiano.core.curriculum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LevelsTest {
    @Test
    fun acLvl01_theLadderStartsOnOneKeyAndEndsOnASong() {
        val levels = curriculum()
        assertEquals((1..levels.size).toList(), levels.map { it.id })
        assertEquals(listOf(60), levels.first().keys)
        assertEquals(LevelKind.SINGLE, levels.first().kind)
        assertEquals(LevelKind.SONG, levels.last().kind)
        // 0.6.0 ships seven; 0.7.0 appends the falling game as level 8.
        assertEquals(7, levels.size)
    }

    @Test
    fun acLvl02_keysGrowOneAtATimeAndNeverLeaveC4ToG4() {
        val levels = curriculum()
        assertEquals(listOf(60), levels[0].keys)
        assertEquals(listOf(60, 62), levels[1].keys)
        assertEquals(listOf(60, 62), levels[2].keys)
        assertEquals(listOf(60, 62, 64), levels[3].keys)
        assertEquals(listOf(60, 62, 64, 65), levels[4].keys)
        assertEquals(FIVE_KEYS, levels[5].keys)
        assertEquals(FIVE_KEYS, levels[6].keys)
        levels.forEach { level ->
            assertTrue(level.keys.all { it in FIVE_KEYS }, "level ${level.id} left C4-G4")
            assertEquals(level.keys.sorted(), level.keys, "level ${level.id} is not in pitch order")
        }
    }

    @Test
    fun acLvl03_theThirdLevelShufflesTheSameTwoKeys() {
        val third = levelById(3)
        assertEquals(LevelKind.SHUFFLE, third.kind)
        assertEquals(listOf(60, 62), third.keys)
        assertEquals(8, third.shufflePrompts)
    }

    @Test
    fun acLvl04_noLevelIsLongerThanElevenPrompts() {
        curriculum().filter { it.kind != LevelKind.SONG }.forEach { level ->
            val prompts = promptsFor(level, seed = 1L).size
            assertTrue(prompts in 6..11, "level ${level.id} has $prompts prompts")
        }
    }

    @Test
    fun acLvl05_unlockedKeysAccumulate() {
        assertEquals(listOf(60), keysUnlockedAfter(1))
        assertEquals(listOf(60, 62), keysUnlockedAfter(3))
        assertEquals(listOf(60, 62, 64), keysUnlockedAfter(4))
        assertEquals(listOf(60, 62, 64, 65), keysUnlockedAfter(5))
        assertEquals(FIVE_KEYS, keysUnlockedAfter(6))
        assertEquals(FIVE_KEYS, keysUnlockedAfter(7))
    }

    @Test
    fun acLvl06_everyKeyHasItsOwnColour() {
        assertEquals("#E5484D", keyColorHex(60))
        assertEquals("#F76B15", keyColorHex(62))
        assertEquals("#E8B931", keyColorHex(64))
        assertEquals("#2FA84F", keyColorHex(65))
        assertEquals("#3A7DDE", keyColorHex(67))
        assertEquals(5, FIVE_KEYS.map { keyColorHex(it) }.toSet().size)
        assertThrows(IllegalArgumentException::class.java) { keyColorHex(61) }
        assertThrows(IllegalArgumentException::class.java) { keyColorHex(72) }
    }

    @Test
    fun acLvl07_levelOneIsOpenAndTheNextOneWaitsForAStar() {
        assertEquals(1, highestUnlocked(emptyMap()))
        assertEquals(1, highestUnlocked(mapOf(1 to 0)))
        assertEquals(2, highestUnlocked(mapOf(1 to 1)))
        assertEquals(4, highestUnlocked(mapOf(1 to 3, 2 to 1, 3 to 2)))
        // A gap must stop the walk: stars on 3 do not unlock 4 while 2 is unstarred.
        assertEquals(2, highestUnlocked(mapOf(1 to 3, 3 to 3)))
        assertEquals(7, highestUnlocked((1..7).associateWith { 3 }))
        // Never past the end of the ladder.
        assertEquals(7, highestUnlocked((1..99).associateWith { 3 }))
    }
}
```

`acLvl04` calls `promptsFor`, which arrives in Task 2. Write it now and expect it to stay red until then, or comment it out and restore it in Task 2 — say which in the commit message.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :core:curriculum:test`
Expected: FAIL — project `:core:curriculum` not found, then unresolved references.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.vijaychhetry.kidspiano.core.curriculum

const val KEY_C4 = 60
const val KEY_D4 = 62
const val KEY_E4 = 64
const val KEY_F4 = 65
const val KEY_G4 = 67

val FIVE_KEYS: List<Int> = listOf(KEY_C4, KEY_D4, KEY_E4, KEY_F4, KEY_G4)

enum class LevelKind { SINGLE, SEQUENCE, SHUFFLE, SEQUENCE_THEN_SHUFFLE, SONG }

data class LevelSpec(
    val id: Int,
    val title: String,
    val keys: List<Int>,
    val kind: LevelKind,
    val shufflePrompts: Int,
    val passes: Int = 1,
)

private val LADDER = listOf(
    LevelSpec(1, "One key: C", listOf(KEY_C4), LevelKind.SINGLE, shufflePrompts = 0, passes = 6),
    LevelSpec(2, "Two keys: C and D", listOf(KEY_C4, KEY_D4), LevelKind.SEQUENCE, 0, passes = 3),
    LevelSpec(3, "Mix it up", listOf(KEY_C4, KEY_D4), LevelKind.SHUFFLE, shufflePrompts = 8),
    LevelSpec(4, "Three keys: E joins", listOf(KEY_C4, KEY_D4, KEY_E4), LevelKind.SEQUENCE_THEN_SHUFFLE, 5),
    LevelSpec(5, "Four keys: F joins", listOf(KEY_C4, KEY_D4, KEY_E4, KEY_F4), LevelKind.SEQUENCE_THEN_SHUFFLE, 5),
    LevelSpec(6, "All five: G joins", FIVE_KEYS, LevelKind.SEQUENCE_THEN_SHUFFLE, 6),
    LevelSpec(7, "Song time", FIVE_KEYS, LevelKind.SONG, 0),
)

fun curriculum(): List<LevelSpec> = LADDER

fun levelById(id: Int): LevelSpec =
    LADDER.firstOrNull { it.id == id } ?: throw IllegalArgumentException("no level $id")

fun keysUnlockedAfter(levelId: Int): List<Int> =
    LADDER.filter { it.id <= levelId }.flatMap { it.keys }.distinct().sorted()

/** Walks the ladder from the bottom and stops at the first unstarred level. */
fun highestUnlocked(starsByLevel: Map<Int, Int>): Int {
    var unlocked = 1
    LADDER.forEach { level ->
        if ((starsByLevel[level.id] ?: 0) < 1) return unlocked
        unlocked = minOf(level.id + 1, LADDER.size)
    }
    return unlocked
}

private val COLORS = mapOf(
    KEY_C4 to "#E5484D",
    KEY_D4 to "#F76B15",
    KEY_E4 to "#E8B931",
    KEY_F4 to "#2FA84F",
    KEY_G4 to "#3A7DDE",
)

fun keyColorHex(midi: Int): String =
    COLORS[midi] ?: throw IllegalArgumentException("no colour for MIDI $midi; only C4-G4 are taught")
```

- [ ] **Step 4: Run tests and make sure they pass**

Run: `./gradlew :core:curriculum:test`
Expected: BUILD SUCCESSFUL (7 tests once Task 2 lands).

- [ ] **Step 5: Commit**

```bash
git add android/core/curriculum android/settings.gradle.kts
git commit -m "Add the level ladder as data"
```

---

### Task 2: Prompt generation (the jumble)

**Files:**
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Prompts.kt`
- Test: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/PromptsTest.kt`

**Interfaces:**
- Consumes: `LevelSpec`, `LevelKind`
- Produces: `promptsFor(level: LevelSpec, seed: Long): List<Int>` — the list handed straight to `LessonSession(notes = …)`

Two rules, and the implementation has to satisfy both **across the whole returned list**, prefix included:

1. **Balanced** — each key of the level appears `n/k` or `n/k + 1` times in the shuffled part. This is a property of the bag we draw from, not a fallback, so "every key appears at least once" comes for free.
2. **Never three of the same key in a row.** The shuffle must be told the last two prompts of the ordered prefix, or level 4's `C D E` prefix followed by a shuffle starting `E E` produces a triple. That failure mode is not rare: with three keys it is 1/9 per seed, so a 200-seed sweep catches it with probability 1 − (8/9)²⁰⁰ ≈ 1.

Drawing the **most plentiful legal key first** is what keeps a triple-free arrangement reachable — a purely random draw can paint itself into a corner where the only key left is the blocked one.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vijaychhetry.kidspiano.core.curriculum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptsTest {
    @Test
    fun acPro01_oneKeyLevelAsksForTheSameKeySixTimes() {
        assertEquals(List(6) { 60 }, promptsFor(levelById(1), seed = 1L))
    }

    @Test
    fun acPro02_twoKeyLevelIsCThenDThreeTimes() {
        assertEquals(listOf(60, 62, 60, 62, 60, 62), promptsFor(levelById(2), seed = 1L))
    }

    @Test
    fun acPro03_theJumbleIsBalancedAndNotAlwaysStrictAlternation() {
        val prompts = promptsFor(levelById(3), seed = 7L)
        assertEquals(8, prompts.size)
        assertEquals(4, prompts.count { it == 60 }, "unbalanced: $prompts")
        assertEquals(4, prompts.count { it == 62 }, "unbalanced: $prompts")
        val alternating = listOf(60, 62, 60, 62, 60, 62, 60, 62)
        assertTrue(
            (1L..50L).any { promptsFor(levelById(3), it) != alternating },
            "every seed produced the same predictable C D C D run",
        )
    }

    @Test
    fun acPro04_shuffleNeverRepeatsAKeyMoreThanTwiceInARow() {
        (1L..200L).forEach { seed ->
            listOf(3, 4, 5, 6).forEach { id ->
                val prompts = promptsFor(levelById(id), seed)
                prompts.windowed(3).forEach { triple ->
                    assertTrue(
                        triple.toSet().size > 1,
                        "level $id seed $seed produced three identical prompts: $prompts",
                    )
                }
            }
        }
    }

    @Test
    fun acPro05_sequenceThenShuffleWalksUpFirstThenMixes() {
        val prompts = promptsFor(levelById(4), seed = 3L)
        assertEquals(listOf(60, 62, 64), prompts.take(3))
        assertEquals(3 + 5, prompts.size)
        val shuffled = prompts.drop(3)
        assertTrue(shuffled.toSet().containsAll(listOf(60, 62, 64)), "a key never came back: $prompts")
        listOf(60, 62, 64).forEach { key ->
            assertTrue(shuffled.count { it == key } in 1..2, "unbalanced: $shuffled")
        }
    }

    @Test
    fun acPro06_sameSeedGivesTheSameRunAndDifferentSeedsDiffer() {
        assertEquals(promptsFor(levelById(6), 11L), promptsFor(levelById(6), 11L))
        assertTrue(
            (1L..50L).map { promptsFor(levelById(6), it) }.toSet().size > 1,
            "the seed makes no difference",
        )
        assertNotEquals(promptsFor(levelById(3), 11L), promptsFor(levelById(3), 11L).reversed())
    }

    @Test
    fun acPro07_theSongLevelHasNoDrillPrompts() {
        assertTrue(promptsFor(levelById(7), 1L).isEmpty())
    }

    @Test
    fun acPro08_everyPromptIsAKeyTheLevelTeaches() {
        (1L..50L).forEach { seed ->
            curriculum().forEach { level ->
                promptsFor(level, seed).forEach { prompt ->
                    assertTrue(prompt in level.keys, "level ${level.id} asked for $prompt")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run — FAIL** (`promptsFor` unresolved).

Run: `./gradlew :core:curriculum:test --tests '*PromptsTest'`

- [ ] **Step 3: Implement**

```kotlin
package com.vijaychhetry.kidspiano.core.curriculum

import kotlin.random.Random

fun promptsFor(level: LevelSpec, seed: Long): List<Int> = when (level.kind) {
    LevelKind.SINGLE -> List(level.passes) { level.keys.first() }
    LevelKind.SEQUENCE -> (1..level.passes).flatMap { level.keys }
    LevelKind.SHUFFLE -> jumble(level.keys, level.shufflePrompts, seed)
    LevelKind.SEQUENCE_THEN_SHUFFLE -> {
        val ordered = level.keys
        ordered + jumble(ordered, level.shufflePrompts, seed, precededBy = ordered.takeLast(2))
    }
    LevelKind.SONG -> emptyList()
}

/**
 * A balanced draw that never asks for the same key three times running.
 * [precededBy] carries the tail of whatever comes before this run, so the
 * rule holds across the join and not just inside the shuffle.
 */
private fun jumble(
    keys: List<Int>,
    count: Int,
    seed: Long,
    precededBy: List<Int> = emptyList(),
): List<Int> {
    if (count == 0 || keys.isEmpty()) return emptyList()
    val random = Random(seed)
    val remaining = keys.associateWith { count / keys.size }.toMutableMap()
    keys.shuffled(random).take(count % keys.size).forEach { remaining[it] = remaining.getValue(it) + 1 }

    val out = ArrayList<Int>(count)
    val tail = ArrayList(precededBy.takeLast(2))
    repeat(count) {
        val blocked = if (tail.size == 2 && tail[0] == tail[1]) tail[0] else null
        val open = remaining.filterValues { it > 0 }.filterKeys { it != blocked }
        require(open.isNotEmpty()) { "cannot avoid a triple for $keys x $count" }
        // Draining the most plentiful key first keeps a triple-free run reachable.
        val most = open.values.max()
        val candidates = open.filterValues { it == most }.keys.sorted()
        val pick = candidates[random.nextInt(candidates.size)]
        remaining[pick] = most - 1
        out += pick
        tail += pick
        if (tail.size > 2) tail.removeAt(0)
    }
    return out
}
```

Note `LevelKind.SINGLE` now reads `passes`, not `shufflePrompts` — level 1 has no shuffled part, and leaving a non-zero `shufflePrompts` on a level that never shuffles is the kind of dead field that breaks the first invariant test somebody writes.

- [ ] **Step 4: Run** `./gradlew :core:curriculum:test` — PASS, including the 200-seed sweep and `acLvl04` from Task 1.
- [ ] **Step 5: Commit** `git commit -m "Generate balanced level prompts a child cannot game"`

---

### Task 3: Stars

**Files:**
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Stars.kt`
- Test: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/StarsTest.kt`

**Interfaces:**
- Produces: `starsFor(answered: Int, prompts: Int, wrongPresses: Int): Int`

Two decisions the first draft of this plan got wrong, both about not punishing a child:

- **Thresholds scale with length.** A flat "2 or fewer wrong" makes an 11-prompt level far stingier than a 6-prompt one, so the top of the ladder would almost always read as one star.
- **80% banks a star.** Quitting at prompt 9 of 11 must not score zero, because zero stars means the next level stays locked — which would be the one genuine fail state in a design that claims to have none.

`wrongPresses` is incremented by the caller **only** on `RecognitionStatus.INCORRECT`. `acStar05` in Task 5 proves the engine never reports that for unclear audio.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vijaychhetry.kidspiano.core.curriculum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StarsTest {
    @Test
    fun acStar01_finishingAlwaysEarnsAtLeastOneStar() {
        assertEquals(1, starsFor(answered = 11, prompts = 11, wrongPresses = 25))
        assertEquals(1, starsFor(answered = 6, prompts = 6, wrongPresses = 99))
    }

    @Test
    fun acStar02_thresholdsScaleWithTheLengthOfTheLevel() {
        // 11 prompts: perfect bar 1, good bar 3.
        assertEquals(3, starsFor(11, 11, 0))
        assertEquals(3, starsFor(11, 11, 1))
        assertEquals(2, starsFor(11, 11, 2))
        assertEquals(2, starsFor(11, 11, 3))
        assertEquals(1, starsFor(11, 11, 4))
        // 6 prompts: perfect bar 0, good bar 2.
        assertEquals(3, starsFor(6, 6, 0))
        assertEquals(2, starsFor(6, 6, 1))
        assertEquals(2, starsFor(6, 6, 2))
        assertEquals(1, starsFor(6, 6, 3))
    }

    @Test
    fun acStar03_threeStarsNeedEveryPromptAnswered() {
        assertEquals(2, starsFor(answered = 10, prompts = 11, wrongPresses = 0))
    }

    @Test
    fun acStar04_eightyPercentBanksAStarAndLessBanksNothing() {
        assertEquals(1, starsFor(answered = 9, prompts = 11, wrongPresses = 6))
        assertEquals(2, starsFor(answered = 9, prompts = 11, wrongPresses = 0))
        assertEquals(0, starsFor(answered = 8, prompts = 11, wrongPresses = 0))
        assertEquals(0, starsFor(answered = 0, prompts = 11, wrongPresses = 0))
    }

    @Test
    fun acStar06_aLevelWithNoPromptsScoresNothingRatherThanDividingByZero() {
        assertEquals(0, starsFor(answered = 0, prompts = 0, wrongPresses = 0))
    }
}
```

- [ ] **Step 2: Run — FAIL.**
- [ ] **Step 3: Implement**

```kotlin
fun starsFor(answered: Int, prompts: Int, wrongPresses: Int): Int {
    if (prompts <= 0) return 0
    val banked = answered * 5 >= prompts * 4        // 80% of the level
    if (!banked) return 0
    val perfectBar = prompts / 10
    val goodBar = maxOf(2, prompts / 3)
    return when {
        answered >= prompts && wrongPresses <= perfectBar -> 3
        wrongPresses <= goodBar -> 2
        else -> 1
    }
}
```

- [ ] **Step 4: PASS** `./gradlew :core:curriculum:test`
- [ ] **Step 5: Commit** `git commit -m "Scale stars to level length and bank one at eighty percent"`

---

### Task 4: Song library

**Files:**
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Songs.kt`
- Test: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/SongsTest.kt`

**Interfaces:**
- Produces: `Song`, `songLibrary()`, `songsFor(unlockedKeys: List<Int>): List<Song>`

The melody assertions below are a **second, independent transcription** written out in full. That is deliberate: a test that only checks `take(3)` cannot catch a corrupted note 10, and these note arrays are the one place in the app where a silent typo produces a wrong tune rather than a crash.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vijaychhetry.kidspiano.core.curriculum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SongsTest {
    private fun song(id: String) = songLibrary().single { it.id == id }

    @Test
    fun acSong01_everySongIsPlayableOnTheFiveWhiteKeys() {
        songLibrary().forEach { s ->
            assertTrue(s.midi.isNotEmpty(), "${s.id} has no notes")
            assertTrue(
                s.midi.all { it in FIVE_KEYS },
                "${s.id} needs a key we never teach: ${s.midi.filterNot { it in FIVE_KEYS }}",
            )
        }
    }

    @Test
    fun acSong02_everyNoteHasANonBlankLyricSyllable() {
        songLibrary().forEach { s ->
            assertEquals(s.midi.size, s.syllables.size, "${s.id} syllables do not line up")
            assertTrue(s.syllables.none { it.isBlank() }, "${s.id} has a blank syllable")
            assertTrue(s.title.isNotBlank() && s.id.isNotBlank())
        }
        assertEquals(songLibrary().size, songLibrary().map { it.id }.toSet().size, "duplicate ids")
    }

    @Test
    fun acSong03_songsUnlockByTheKeysLearnedSoFar() {
        val threeKeys = listOf(60, 62, 64)
        val ids = songsFor(threeKeys).map { it.id }
        assertTrue("hot-cross-buns" in ids)
        assertTrue("au-clair-de-la-lune" in ids)
        assertFalse("step-up" in ids, "Step Up needs F")
        assertFalse("ode-to-joy" in ids)
        assertFalse("mary-had-a-little-lamb" in ids, "Mary needs G")
        assertEquals(2, ids.size)
        assertEquals(3, songsFor(listOf(60, 62, 64, 65)).size)
        assertTrue(songsFor(listOf(60)).isEmpty())
    }

    @Test
    fun acSong04_allSixSongsAreOfferedOnceEveryKeyIsLearned() {
        assertEquals(6, songsFor(FIVE_KEYS).size)
    }

    @Test
    fun acSong05_theMelodiesAreTheRealTunes() {
        assertEquals(listOf(60, 62, 64, 65, 65, 64, 62, 60), song("step-up").midi)
        assertEquals(listOf(60, 60, 60, 62, 64, 62, 60, 64, 62, 62, 60), song("au-clair-de-la-lune").midi)
        assertEquals(
            listOf(60, 62, 64, 60, 60, 62, 64, 60, 64, 65, 67, 64, 65, 67),
            song("are-you-sleeping").midi,
        )
        assertEquals(
            listOf(64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 62),
            song("ode-to-joy").midi,
        )
        assertEquals(
            listOf(64, 62, 60, 64, 62, 60, 60, 60, 60, 60, 62, 62, 62, 62, 64, 62, 60),
            song("hot-cross-buns").midi,
        )
        assertEquals(
            listOf(
                64, 62, 60, 62, 64, 64, 64,
                62, 62, 62,
                64, 67, 67,
                64, 62, 60, 62, 64, 64, 64, 64, 62, 62, 64, 62, 60,
            ),
            song("mary-had-a-little-lamb").midi,
        )
        assertEquals(listOf("Hot", "cross", "buns"), song("hot-cross-buns").syllables.take(3))
        assertEquals("snow", song("mary-had-a-little-lamb").syllables.last())
    }

    @Test
    fun acSong06_songsAreOfferedShortestFirst() {
        assertEquals(
            listOf(
                "step-up",
                "au-clair-de-la-lune",
                "are-you-sleeping",
                "ode-to-joy",
                "hot-cross-buns",
                "mary-had-a-little-lamb",
            ),
            songsFor(FIVE_KEYS).map { it.id },
        )
    }
}
```

`acSong06` asserts the exact id order, not `sizes == sizes.sorted()` — the latter is a tautology when the function ends in `sortedBy { it.midi.size }` and passes just as happily if that sort is deleted.

- [ ] **Step 2: Run — FAIL.**

- [ ] **Step 3: Implement** — octave 4 only, every note in `FIVE_KEYS`:

```kotlin
package com.vijaychhetry.kidspiano.core.curriculum

data class Song(
    val id: String,
    val title: String,
    val midi: List<Int>,
    val syllables: List<String>,
)

private val STEP_UP = Song(
    "step-up", "Step Up",
    listOf(60, 62, 64, 65, 65, 64, 62, 60),
    listOf("Up", "we", "go", "now", "back", "we", "come", "home"),
)

private val AU_CLAIR = Song(
    "au-clair-de-la-lune", "Au Clair de la Lune",
    listOf(60, 60, 60, 62, 64, 62, 60, 64, 62, 62, 60),
    listOf("Au", "clair", "de", "la", "lu", "ne", "mon", "a", "mi", "Pier", "rot"),
)

// First half only. The next phrase needs A4, which we never teach, and this
// stops on a complete phrase so it does not sound cut off to a child.
private val ARE_YOU_SLEEPING = Song(
    "are-you-sleeping", "Are You Sleeping",
    listOf(60, 62, 64, 60, 60, 62, 64, 60, 64, 65, 67, 64, 65, 67),
    listOf(
        "Are", "you", "sleep", "ing", "Are", "you", "sleep", "ing",
        "Broth", "er", "John", "Broth", "er", "John",
    ),
)

// First line only, for the same reason.
private val ODE_TO_JOY = Song(
    "ode-to-joy", "Ode to Joy",
    listOf(64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 62),
    listOf(
        "Joy", "ful", "joy", "ful", "we", "a", "dore", "thee",
        "God", "of", "glo", "ry", "Lord", "of", "love",
    ),
)

private val HOT_CROSS_BUNS = Song(
    "hot-cross-buns", "Hot Cross Buns",
    listOf(64, 62, 60, 64, 62, 60, 60, 60, 60, 60, 62, 62, 62, 62, 64, 62, 60),
    listOf(
        "Hot", "cross", "buns", "Hot", "cross", "buns",
        "One", "a", "pen", "ny", "two", "a", "pen", "ny",
        "hot", "cross", "buns",
    ),
)

private val MARY = Song(
    "mary-had-a-little-lamb", "Mary Had a Little Lamb",
    listOf(
        64, 62, 60, 62, 64, 64, 64,
        62, 62, 62,
        64, 67, 67,
        64, 62, 60, 62, 64, 64, 64, 64, 62, 62, 64, 62, 60,
    ),
    listOf(
        "Ma", "ry", "had", "a", "lit", "tle", "lamb",
        "lit", "tle", "lamb",
        "lit", "tle", "lamb",
        "Ma", "ry", "had", "a", "lit", "tle", "lamb", "its",
        "fleece", "was", "white", "as", "snow",
    ),
)

fun songLibrary(): List<Song> =
    listOf(STEP_UP, AU_CLAIR, ARE_YOU_SLEEPING, ODE_TO_JOY, HOT_CROSS_BUNS, MARY)

fun songsFor(unlockedKeys: List<Int>): List<Song> =
    songLibrary()
        .filter { song -> song.midi.all { it in unlockedKeys } }
        .sortedBy { it.midi.size }
```

Note counts, which `acSong02` enforces: Step Up 8, Au Clair 11, Are You Sleeping 14, Ode to Joy 15, Hot Cross Buns 17, Mary 26.

- [ ] **Step 4: PASS** `./gradlew :core:curriculum:test`
- [ ] **Step 5: Commit** `git commit -m "Add six tunes that fit inside C4 to G4"`

---

### Task 5: The judging engine drives levels and songs

**Files:**
- Modify: `android/core/learning/src/main/kotlin/com/vijaychhetry/kidspiano/core/learning/LessonSession.kt`
- Modify: `android/core/learning/src/test/kotlin/com/vijaychhetry/kidspiano/core/learning/LessonSessionTest.kt`
- Modify: `android/core/learning/build.gradle.kts` — add `implementation(project(":core:curriculum"))`

**Interfaces:**
- Consumes: `promptsFor`, `Song.midi` (via callers), `FIVE_KEYS`
- Produces: `LessonSnapshot.heardMidi`, `LessonSession(completionCopy = …)`, `lessonSessionFor(profile, notes, completionCopy)`, the copy in the spec's feedback table

A song and a level are the same thing to this class: an ordered prompt list, possibly with repeats. Nothing about the judging changes. Five things do:

1. **`heardMidi` is sticky.** It must **not** be `judgedMidi`. `judgedMidi` is cleared the moment the debouncer hits IDLE (line 93), roughly 190 ms after the finger lifts, so a UI flash driven off it would disappear before the child looked. Add a separate `lastHeardMidi`, set it in the judging branch, and clear it only in `restart()`.
2. **`completionCopy`** replaces both hardcoded `"You found all five keys!"` strings (lines 97 and 115).
3. **Copy names keys with their octave, everywhere.** 0.5.0 fixed the octave-mismatch line but left `INCORRECT` as `"Try C."` and the opening line as a hardcoded `"Play the C key."` (lines 67 and 147), which is wrong the moment a shuffled level or Ode to Joy opens on a key that is not C.
4. **A repeated prompt says so** — `"Yes! C4 again."` rather than `"Yes! Now C4."` five times running in level 1.
5. **A press too short to name gets an answer.** Recognition needs 3 agreeing frames ≈ 139 ms. A child's poke produces ATTACK then IDLE with no STABLE in between, and today that path writes no feedback at all: the `lastResult == null` branch (line 123) stops firing after the first judgement, so the previous prompt's answer just stays on screen. Without this the promise of "feedback on every key press" is false.

**Two existing tests will go red on the copy change and must be updated in the same commit:** `acLearn01` asserts `feedback.contains("Play the C key")` (line 35) and `acLearn02` asserts `feedback.contains("Try C")` (line 48). Update them to `"Play the C4 key"` and `"That was E4. Press C4."`. Do not weaken them to `contains("C")`.

- [ ] **Step 1: Write the failing tests** (append to `LessonSessionTest`, inside `class LessonSessionTest`)

```kotlin
    @Test
    fun acLearn13_theSnapshotNamesTheKeyTheChildActuallyPressedAndKeepsIt() {
        val session = LessonSession(notes = listOf(60, 62))
        session.begin(0)
        val snap = play(session, midi = 64, atFrame = 0)
        assertEquals(RecognitionStatus.INCORRECT, snap.status)
        assertEquals(64, snap.heardMidi, "the screen must be able to flash the key they hit")
        assertEquals(60, snap.expectedMidi)
        assertTrue(snap.feedback.contains("E4"), "got: ${snap.feedback}")
        assertTrue(snap.feedback.contains("C4"), "got: ${snap.feedback}")
        // Let go. The amber flash runs for 600 ms; heardMidi must outlive the
        // release, or the child never sees which key they hit.
        repeat(6) { i ->
            session.onFrame(AudioFrame(FloatArray(2048), SR, (8 + i) * FRAME_MS))
        }
        assertEquals(64, session.snapshot().heardMidi, "the flash must outlive the release")
        assertEquals(64, session.snapshot().heardMidi, "snapshot() must not consume it")
    }

    @Test
    fun acLearn14_aRepeatedPromptStillNeedsASecondPress() {
        val session = LessonSession(notes = listOf(60, 60))
        session.begin(0)
        // `hold` is 8 contiguous tone frames and never releases.
        var snap = hold(session, 60, 0)
        assertEquals(1, snap.completedCount)
        assertEquals(60, snap.expectedMidi, "the second prompt is C4 again")
        snap = hold(session, 60, 8)
        assertEquals(1, snap.completedCount, "a held key cannot answer the next prompt")
        assertNotEquals(RecognitionStatus.INCORRECT, snap.status)
        assertFalse(snap.complete)
        // `playPress` ends with 4 silent frames, so this one really is a new press.
        snap = playPress(session, 60, 16)
        assertEquals(2, snap.completedCount, "a real second press does answer it")
        assertTrue(snap.complete)
    }

    @Test
    fun acLearn15_aRepeatedPromptIsAnnouncedAsAgain() {
        val session = LessonSession(notes = listOf(60, 60, 62))
        session.begin(0)
        val first = playPress(session, 60, 0)
        assertTrue(first.feedback.contains("again"), "got: ${first.feedback}")
        val second = playPress(session, 60, PRESS_FRAMES)
        assertTrue(second.feedback.contains("Now D4"), "got: ${second.feedback}")
    }

    @Test
    fun acLearn16_aSongIsJustAPromptList() {
        val session = LessonSession(
            notes = listOf(64, 62, 60),
            completionCopy = "You played Hot Cross Buns!",
        )
        session.begin(0)
        // A wrong note mid-song neither advances nor ends anything.
        val wrong = play(session, 67, 0)
        assertEquals(RecognitionStatus.INCORRECT, wrong.status)
        assertEquals(0, wrong.completedCount)
        assertEquals(64, wrong.expectedMidi)
        var frame = PRESS_FRAMES
        listOf(64, 62, 60).forEach { midi ->
            playPress(session, midi, frame)
            frame += PRESS_FRAMES
        }
        val snap = session.snapshot()
        assertTrue(snap.complete)
        assertEquals(null, snap.expectedMidi)
        assertEquals("You played Hot Cross Buns!", snap.feedback)
        assertEquals(LessonCue.DONE, snap.cue)
    }

    @Test
    fun acLearn17_aPressTooShortToNameStillGetsAnAnswer() {
        val session = LessonSession(notes = listOf(60, 62))
        session.begin(0)
        // Answer the first prompt so the "nothing has happened yet" branch is spent.
        playPress(session, 60, 0)
        val before = session.snapshot().feedback
        // Two tone frames is ATTACK but never STABLE: stableFrames = 3.
        session.onFrame(frame(midiToFreq(62), PRESS_FRAMES))
        session.onFrame(frame(midiToFreq(62), PRESS_FRAMES + 1))
        val snap = session.onFrame(
            AudioFrame(FloatArray(2048), SR, (PRESS_FRAMES + 2) * FRAME_MS),
        )
        assertNotEquals(before, snap.feedback, "a poke must not leave the old answer on screen")
        assertTrue(snap.feedback.contains("Hold the key"), "got: ${snap.feedback}")
        assertNotEquals(RecognitionStatus.INCORRECT, snap.status)
        assertEquals(1, snap.completedCount)
    }

    @Test
    fun acStar05_unclearAudioIsNotAWrongPress() {
        val session = LessonSession(notes = listOf(60, 62))
        session.begin(0)
        // Two notes at once — genuinely ambiguous. Silence is NO_SIGNAL and
        // never reaches the judging branch, so it would prove nothing here.
        val mix = com.vijaychhetry.kidspiano.core.pitch.mixTones(
            sineTone(midiToFreq(60), SR, 0.6, amplitude = 0.35f),
            sineTone(midiToFreq(64), SR, 0.6, amplitude = 0.35f),
        )
        var wrong = 0
        var snap = session.snapshot()
        repeat(16) { i ->
            val start = (2000 + i * 64).coerceAtMost(mix.size - 2048)
            snap = session.onFrame(
                AudioFrame(mix.copyOfRange(start, start + 2048), SR, i * FRAME_MS),
            )
            if (snap.status == RecognitionStatus.INCORRECT) wrong++
        }
        assertEquals(RecognitionStatus.AMBIGUOUS, snap.status)
        assertEquals(0, wrong)
        assertEquals(0, snap.completedCount)
    }

    @Test
    fun acLearn18_aLessonWithNoPromptsIsRejectedRatherThanReturningNaN() {
        assertThrows(IllegalArgumentException::class.java) { LessonSession(notes = emptyList()) }
    }
```

`acStar05` lives here, not in `:core:curriculum`, because it needs the audio helpers and `LessonSession` — and `:core:curriculum` deliberately depends on neither. Add `import org.junit.jupiter.api.Assertions.assertThrows`.

`acLearn18` guards a bug class this repo has already been bitten by: `progress` is `index / notes.size` (line 177), and AC-METER-04 already says "`NaN` can never reach Compose's `fillMaxWidth`".

- [ ] **Step 2: Run — FAIL**

Run: `./gradlew :core:learning:test`
Expected: unresolved `heardMidi` / `completionCopy`, plus `acLearn01` and `acLearn02` red once the copy changes.

- [ ] **Step 3: Implement**

```kotlin
data class LessonSnapshot(
    val expectedMidi: Int?,
    val heardMidi: Int?,
    …
)

class LessonSession(
    private val notes: List<Int> = MVP_CALIBRATION_MIDI,
    private val completionCopy: String = "You found all five keys!",
    …
) {
    init { require(notes.isNotEmpty()) { "a lesson needs at least one prompt" } }

    private var lastHeardMidi: Int? = null
    private var pressReachedAttack = false
    private var pressReachedStable = false
    private var pokeToReport = false
    private var lastFeedback: String = "Play the ${nameOf(notes.first())} key."
```

In `onFrame`, after `lastPhase = phase`:

```kotlin
        if (phase == NotePhase.ATTACK) pressReachedAttack = true
        if (phase == NotePhase.STABLE) pressReachedStable = true
        if (phase == NotePhase.IDLE) {
            judgedMidi = null
            // ATTACK with no STABLE is a press too short to name.
            if (pressReachedAttack && !pressReachedStable) pokeToReport = true
            pressReachedAttack = false
            pressReachedStable = false
        }
```

In the `when`, insert a branch **after** the `pitch.ambiguous` branch and **before** `phase == NotePhase.STABLE` (a frame cannot be both IDLE and STABLE, so the order between those two is free; it must come before `lastResult == null`, which would otherwise swallow it):

```kotlin
            pokeToReport -> {
                pokeToReport = false
                lastFeedback = SHORT_PRESS_COPY
            }
```

In the judging branch, set the sticky field and use the new copy:

```kotlin
            phase == NotePhase.STABLE && pitch.midiNote != judgedMidi -> {
                judgedMidi = pitch.midiNote
                lastHeardMidi = pitch.midiNote
                val previousTarget = target
                val result = validator.validate(target, recognizer.recognize(pitch))
                lastResult = result
                if (result.status == RecognitionStatus.CORRECT) {
                    index++
                    lastFeedback = when {
                        complete -> completionCopy
                        expectedMidi == previousTarget -> "Yes! ${nameOf(expectedMidi)} again."
                        else -> "Yes! Now ${nameOf(expectedMidi)}."
                    }
                } else {
                    lastFeedback = childCopy(result.status, target, pitch.midiNote)
                }
            }
```

`childCopy` names both keys for a wrong key as well as a wrong octave:

```kotlin
    private fun childCopy(status: RecognitionStatus, expected: Int, detected: Int?): String {
        val want = nameOf(expected)
        val heard = detected?.let { midiToNoteName(it) }
        return when (status) {
            RecognitionStatus.INCORRECT ->
                if (heard != null) "That was $heard. Press $want." else "Press $want."
            RecognitionStatus.CORRECT_OCTAVE_MISMATCH ->
                if (heard != null) "That was $heard. Press $want." else "Press $want."
            else -> UNCLEAR_COPY
        }
    }

    private fun nameOf(midi: Int?): String = midi?.let { midiToNoteName(it) } ?: "—"
```

`letterOf` stays for `LessonSnapshot.letter`, which the UI still uses. `restart()` resets `lastHeardMidi`, `pressReachedAttack`, `pressReachedStable`, `pokeToReport`, and sets `lastFeedback = "Play the ${nameOf(notes.first())} key."`. The two remaining `"You found all five keys!"` literals (lines 97 and 115) become `completionCopy`.

New companion constant: `const val SHORT_PRESS_COPY = "I heard something. Hold the key a bit longer."`

Then the factory:

```kotlin
fun lessonSessionFor(
    profile: CalibrationProfile,
    notes: List<Int> = defaultLessonMidi(),
    completionCopy: String = "You found all five keys!",
): LessonSession {
    val a4 = concertA4Hz(profile)
    return LessonSession(
        notes = notes,
        completionCopy = completionCopy,
        detector = YinHpsPitchDetector(a4Hz = a4),
        validator = DefaultNoteValidator(a4Hz = a4),
    )
}
```

Do **not** touch the `judgedMidi` lock, the ambiguity guard, or `pressIsLocked()`. Do not let app code call the constructor directly — `lessonSessionFor` is the only place the calibrated A4 reaches the detector and validator, and bypassing it silently disables calibration for every level and song with no test to catch it.

- [ ] **Step 4: PASS** `./gradlew :core:learning:test :core:curriculum:test`
Expected: all existing AC-LEARN tests still green, `acLearn01`/`acLearn02` updated, 7 new tests passing.

- [ ] **Step 5: Commit** `git commit -m "Name the key the child actually pressed and answer every press"`

---

### Task 6: Progress store

**Files:**
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/progress/ProgressStore.kt`
- Modify: the session-log call sites — append one line per level result

**Interfaces:**
- Consumes: `curriculum()`, `highestUnlocked` (already tested by `acLvl07`)
- Produces: `ProgressStore.stars(levelId)`, `record(levelId, stars)`, `highestUnlocked()`, `totalStars()`

Store in the existing `calibration` prefs under `level.<id>.stars`. `record` keeps the **best** score, never overwrites a 3 with a 1. The rule itself is already unit-tested in `:core:curriculum`; this class is only the prefs wrapper, so it needs no new JVM test.

- [ ] **Step 1:** `./gradlew :app:assembleDebug` for a green baseline.
- [ ] **Step 2–3:** Implement the wrapper. Append `{"kind":"level","id":…,"stars":…,"answered":…,"wrong":…}` to the session log so a run can be read back out of the exported JSONL.
- [ ] **Step 4:** `./gradlew :app:assembleDebug`
- [ ] **Step 5:** Commit `git commit -m "Remember stars and unlock the next stop"`

---

### Task 7: Colour the keyboard and flash the wrong key

**Files:**
- Modify: `android/app/src/main/java/com/vijaychhetry/kidspiano/ui/PianoKeyboardView.kt`

**Interfaces:**
- Consumes: `keyColorHex`, `LessonSnapshot.heardMidi`
- Produces: `PianoKeyboardView(highlightMidi, modifier, flashMidi, coloredMidi)` — this exact parameter order, in every call site and every doc

The target key is filled with `keyColorHex(highlightMidi)`. Taught-but-not-target keys get a pale wash of their own colour, **mixed against white rather than drawn with alpha** — an alpha tint composites against whatever is behind the keyboard, which in this layout is near-black, and the keys come out muddy and their names become unreadable. `flashMidi` draws a 3 dp amber ring that fades over 600 ms via `animateFloatAsState`. Do not add `clickable`.

Proof: `:app:assembleDebug` plus a device screenshot. No Compose test harness exists in this repo and empty `androidTest` stubs are banned.

- [ ] **Step 1:** `assembleDebug` baseline.
- [ ] **Step 2–3:** Add the parameters, defaulted so existing call sites keep compiling.
- [ ] **Step 4:** `./gradlew :app:assembleDebug`
- [ ] **Step 5:** Commit `git commit -m "Colour the taught keys and flash the one the child hit"`

---

### Task 8: Level map screen

**Files:**
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/levels/LevelMapScreen.kt`
- Modify: `android/app/.../home/HomeScreen.kt` — Play opens the map
- Modify: `android/app/.../MainActivity.kt` — `Dest.LEVEL_MAP`

Stops on a winding path: a `LazyColumn` with alternating start/end alignment. Each stop shows its number, title, up to three stars, and either the colours of the keys it teaches or a padlock. Locked stops are 40% alpha and not clickable. The map becomes the child's home after Practice.

- [ ] **Step 1:** `assembleDebug` baseline.
- [ ] **Step 2–3:** Build from `curriculum()` + `ProgressStore`.
- [ ] **Step 4:** `assembleDebug`.
- [ ] **Step 5:** Commit `git commit -m "Add the level map"`

---

### Task 9: Drill screen for levels 1–6

**Files:**
- Modify: `android/app/.../lesson/LessonScreen.kt`, `LessonViewModel.kt`

`LessonViewModel.startLevel(level: LevelSpec, profile: CalibrationProfile)`:

```kotlin
require(level.kind != LevelKind.SONG) { "level ${level.id} is not a drill" }
val seed = System.currentTimeMillis()
val prompts = promptsFor(level, seed)
session = lessonSessionFor(profile, prompts, "Level ${level.id} done!")
```

**Play again must draw a new seed** and build a fresh session. `LessonSession.notes` is a constructor `val` and `restart()` only resets the index (lines 141–152), so calling `restart()` on level 3 replays the identical eight prompts — on the one level whose whole purpose is that the child cannot predict the order. Route `playAgain()` for a ladder level through `startLevel` again.

UI state gains `levelTitle`, `heardMidi`, `wrongPresses`, `answered`. The hero note name is tinted `keyColorHex(expectedMidi)`. Progress becomes pips, one per prompt. On completion, navigate to the summary with `starsFor(answered, prompts.size, wrongPresses)`. A pause that leaves the level also reports through `starsFor`, so 80% still banks a star.

- [ ] **Step 1–5:** as Task 8, commit `git commit -m "Run a level from the ladder on the practice screen"`

---

### Task 10: Song screen

**Files:**
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/song/SongScreen.kt`, `SongViewModel.kt`

Engine is `lessonSessionFor(profile, song.midi, "You played ${song.title}!")`. A `LazyRow` of note bubbles: a circle in `keyColorHex(midi)` with the note name, syllable underneath. Current note 1.4× and outlined, answered notes ticked, upcoming notes 60% alpha, auto-scrolled to keep the current note centred. Same keyboard below.

- [ ] **Step 1–5:** commit `git commit -m "Play a tune, one note at a time, at the child's pace"`

---

### Task 11: Session summary, wiring, 0.6.0

**Files:**
- Create: `android/app/.../summary/SummaryScreen.kt`
- Modify: `MainActivity.kt`, `android/app/build.gradle.kts`, `docs/android/ACCEPTANCE_CRITERIA.md`, `android/README.md`, `releases/README.md`
- Copy: APK → `releases/KidsPiano-debug-0.6.0.apk`

Summary shows the stars with a scale-in animation, one line of what was learned, then `Play a song` for each entry in `songsFor(keysUnlockedAfter(levelId))`, `Play again`, and `Level map`.

New AC rows — **one per new test, all 30 of them**, IDs matching test names:

| ID | Criterion | Test |
| --- | --- | --- |
| AC-LVL-01 | Ladder starts on one key, ends on a song | `LevelsTest.acLvl01_theLadderStartsOnOneKeyAndEndsOnASong` |
| AC-LVL-02 | Keys grow one at a time, never leave C4–G4 | `acLvl02_keysGrowOneAtATimeAndNeverLeaveC4ToG4` |
| AC-LVL-03 | Level 3 shuffles the same two keys, eight prompts | `acLvl03_theThirdLevelShufflesTheSameTwoKeys` |
| AC-LVL-04 | No level is longer than eleven prompts | `acLvl04_noLevelIsLongerThanElevenPrompts` |
| AC-LVL-05 | Unlocked keys accumulate | `acLvl05_unlockedKeysAccumulate` |
| AC-LVL-06 | Each key has its own fixed colour | `acLvl06_everyKeyHasItsOwnColour` |
| AC-LVL-07 | Level 1 open; a gap stops the unlock walk | `acLvl07_levelOneIsOpenAndTheNextOneWaitsForAStar` |
| AC-PRO-01 | One-key level asks six times | `PromptsTest.acPro01_oneKeyLevelAsksForTheSameKeySixTimes` |
| AC-PRO-02 | Two-key level is C then D, three times | `acPro02_twoKeyLevelIsCThenDThreeTimes` |
| AC-PRO-03 | The jumble is balanced, not fixed alternation | `acPro03_theJumbleIsBalancedAndNotAlwaysStrictAlternation` |
| AC-PRO-04 | A jumble never repeats a key three times running | `acPro04_shuffleNeverRepeatsAKeyMoreThanTwiceInARow` |
| AC-PRO-05 | Ordered run first, then a balanced mix | `acPro05_sequenceThenShuffleWalksUpFirstThenMixes` |
| AC-PRO-06 | Seeded runs reproduce; seeds differ | `acPro06_sameSeedGivesTheSameRunAndDifferentSeedsDiffer` |
| AC-PRO-07 | The song level has no drill prompts | `acPro07_theSongLevelHasNoDrillPrompts` |
| AC-PRO-08 | Every prompt is a key the level teaches | `acPro08_everyPromptIsAKeyTheLevelTeaches` |
| AC-STAR-01 | Finishing always earns a star | `StarsTest.acStar01_finishingAlwaysEarnsAtLeastOneStar` |
| AC-STAR-02 | Thresholds scale with level length | `acStar02_thresholdsScaleWithTheLengthOfTheLevel` |
| AC-STAR-03 | Three stars need every prompt answered | `acStar03_threeStarsNeedEveryPromptAnswered` |
| AC-STAR-04 | 80% banks a star, less banks nothing | `acStar04_eightyPercentBanksAStarAndLessBanksNothing` |
| AC-STAR-05 | Unclear audio is not a wrong press | `LessonSessionTest.acStar05_unclearAudioIsNotAWrongPress` |
| AC-STAR-06 | An empty level scores 0, not a divide by zero | `acStar06_aLevelWithNoPromptsScoresNothingRatherThanDividingByZero` |
| AC-SONG-01 | Every song fits C4–G4 | `SongsTest.acSong01_everySongIsPlayableOnTheFiveWhiteKeys` |
| AC-SONG-02 | Every note has a non-blank syllable | `acSong02_everyNoteHasANonBlankLyricSyllable` |
| AC-SONG-03 | Songs unlock by keys learned | `acSong03_songsUnlockByTheKeysLearnedSoFar` |
| AC-SONG-04 | All six offered once five keys are learned | `acSong04_allSixSongsAreOfferedOnceEveryKeyIsLearned` |
| AC-SONG-05 | Every melody is the real tune, note for note | `acSong05_theMelodiesAreTheRealTunes` |
| AC-SONG-06 | Songs are offered shortest first | `acSong06_songsAreOfferedShortestFirst` |
| AC-LEARN-13 | Heard key is reported and outlives the release | `LessonSessionTest.acLearn13_theSnapshotNamesTheKeyTheChildActuallyPressedAndKeepsIt` |
| AC-LEARN-14 | A repeated prompt needs a second press | `acLearn14_aRepeatedPromptStillNeedsASecondPress` |
| AC-LEARN-15 | A repeated prompt is announced as "again" | `acLearn15_aRepeatedPromptIsAnnouncedAsAgain` |
| AC-LEARN-16 | A song is just a prompt list | `acLearn16_aSongIsJustAPromptList` |
| AC-LEARN-17 | A press too short to name still gets an answer | `acLearn17_aPressTooShortToNameStillGetsAnAnswer` |
| AC-LEARN-18 | An empty lesson is rejected, not NaN | `acLearn18_aLessonWithNoPromptsIsRejectedRatherThanReturningNaN` |

**Automation (must run before claiming done):**

```bash
cd android
./gradlew :core:notes:test :core:pitch:test :core:calibration:test \
          :core:learning:test :core:diagnostics:test :core:curriculum:test
./gradlew :app:assembleDebug
```

Expected: 0 failures. Read the new total from `build/test-results` and put that number in ACCEPTANCE_CRITERIA.

On-device checklist (gradle cannot see any of this):

1. Level 1 asks for C4 six times, the hero is red, and the second prompt says "Yes! C4 again."
2. Play E4 when C4 is asked — the E4 key rings amber, the words name both keys, and the ring is still visible after letting go.
3. Poke a key for a fraction of a second — the screen says "Hold the key a bit longer", not nothing.
4. Level 3 prompts are not C D C D, and Play again gives a different order.
5. Hold a correct C4 for five seconds on level 1 — it must not auto-answer the next prompt.
6. Finish level 4, take the song, play Hot Cross Buns to the end.
7. Quit level 6 at prompt 9 of 11 — one star is banked and level 7 unlocks.

- [ ] **Step 1–3:** AC rows, gradle, APK copy, commit, push, update the PR.

```bash
git add android docs releases
git commit -m "Ship 0.6.0 level ladder and songs"
git push -u origin HEAD
```

---

## Self-review

**Spec coverage:** one key → level 1. Two keys → level 2. Jumble two keys → level 3, `acPro03` and `acPro04`. Third key → level 4, then 5 and 6. Feedback on every press → Task 5, five separate cases including the too-short poke that had no answer at all before. Songs playable on the learned keys, offered at the end of a session → Tasks 4, 10, 11. Attractive kid screens → key colours, level map, bubbles and stars. Structure before code → this plan plus the mockups. **Falling game → not here; `2026-09-19-falling-notes-game.md`.**

**Placeholders:** none. Every melody is written out as MIDI with matching syllables, twice — once in the implementation and once, independently, in the test.

**Type consistency:** `LevelSpec.shufflePrompts` (not `promptCount`), `starsFor(answered, prompts, wrongPresses)`, `promptsFor(level, seed)`, `songsFor(unlockedKeys)`, `LessonSnapshot.heardMidi`, `lessonSessionFor(profile, notes, completionCopy)`, `PianoKeyboardView(highlightMidi, modifier, flashMidi, coloredMidi)` — one spelling and one parameter order each, everywhere.

**Module direction:** `:core:curriculum` → `:core:notes` only. `:core:learning` → `:core:curriculum`. No cycle. The one test that needs both audio helpers and `LessonSession` (`acStar05`) lives in `:core:learning`, which is why `:core:curriculum` does not need a test dependency on it.

**Risk being carried on purpose:** Tasks 7–11 are Compose and have no unit tests. They get a reviewer pass instead, checking that the held-note lock, the never-wrong-on-unclear rule, the sticky `heardMidi`, and the no-fail-state rule survived contact with the UI, and that no app code calls the `LessonSession` constructor directly.

## Execution (only after the human approves the screens)

Subagent-driven, one task per subagent, JVM tests after each.
