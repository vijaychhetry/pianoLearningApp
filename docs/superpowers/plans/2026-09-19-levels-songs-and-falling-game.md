# Levels, Songs, and Falling Game Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn one practice screen into an eight-level ladder (1 key → 2 keys → shuffled → 3, 4, 5 keys → song → falling-notes game), answer every key press on screen, and end each session with a real tune playable on C4–G4.

**Architecture:** One new JVM module `:core:curriculum` holds level data, prompt generation, star scoring, the song library, and the falling-game clock. **`LessonSession` stays the single judging engine for levels 1–7** — a level or a song is just an ordered *prompt list* fed to its existing `notes` parameter, so the `judgedMidi` held-note lock, the never-mark-unclear-wrong rule, and all existing AC-LEARN tests keep working untouched. Only the falling game needs a new session type, because it is the only screen where time matters. Compose gains five screens that read those pure models.

**Tech Stack:** Kotlin, JUnit 5, Jetpack Compose Material 3. No new third-party libraries, no audio playback, no animation library beyond Compose's own `animate*AsState` / `Animatable`.

## Global Constraints

- Ships as **0.6.0** / `versionCode 6`, on top of 0.5.0 (`docs/superpowers/plans/2026-09-19-keyboard-guide-and-export.md`). Do not start until 0.5.0 is merged.
- Package id stays `com.vijaychhetry.kidspiano`.
- Musical vocabulary is exactly **C4 D4 E4 F4 G4** (MIDI 60, 62, 64, 65, 67). No note outside that set appears anywhere — not in a level, not in a song, not in the game.
- Unclear / two notes / too quiet is **never** `INCORRECT`, never costs a star, never counts as a game miss.
- The held-note lock (`judgedMidi` until `IDLE`) is not touched. Any change that lets a held correct note be re-scored is a bug.
- No fail state anywhere. No lives, no health bar, no timer on levels 1–7.
- Key colours are fixed: C `#E5484D`, D `#F76B15`, E `#E8B931`, F `#2FA84F`, G `#3A7DDE`. Colour is never the only signal — the note name is always present too.
- Falling game: fall time 3000 ms, hit window ±700 ms, spawn gap 2500 ms, 12 tiles, each missed tile requeued **once**.
- Songs are rhythm-free. The strip advances on the correct note, whenever it comes.
- No mascot, no streaks, no accounts, no notation, no black keys, no chords, no in-app song playback.
- Reuse the 0.5.0 `PianoKeyboardView`; add a colour parameter, do not write a second keyboard.
- Tests first: write the failing test, run it, watch it fail, then implement.
- Debug APK goes to `releases/KidsPiano-debug-0.6.0.apk`.
- Do not implement until the human approves the screens in `docs/ui-mockups/`.

## File map

- Create: `android/core/curriculum/build.gradle.kts`
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Levels.kt`
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Prompts.kt`
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Stars.kt`
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Songs.kt`
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/FallingGame.kt`
- Create: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/LevelsTest.kt`
- Create: `.../curriculum/PromptsTest.kt`, `StarsTest.kt`, `SongsTest.kt`, `FallingGameTest.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/progress/ProgressStore.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/levels/LevelMapScreen.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/song/SongScreen.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/game/FallingGameScreen.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/game/FallingGameViewModel.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/summary/SummaryScreen.kt`
- Modify: `android/settings.gradle.kts` — `include(":core:curriculum")`
- Modify: `android/core/learning/.../LessonSession.kt` — `heardMidi` in the snapshot, `completionCopy` parameter
- Modify: `android/app/.../lesson/LessonScreen.kt` + `LessonViewModel.kt` — level-driven prompts, colours, wrong-key flash
- Modify: `android/app/.../ui/PianoKeyboardView.kt` — `keyColors` + `flashMidi` parameters
- Modify: `android/app/.../home/HomeScreen.kt` — Play goes to the level map
- Modify: `android/app/.../MainActivity.kt` — LEVEL_MAP / DRILL / SONG / GAME / SUMMARY destinations
- Modify: `android/app/build.gradle.kts` — 0.6.0, depend on `:core:curriculum`
- Modify: `docs/android/ACCEPTANCE_CRITERIA.md`, `android/README.md`, `releases/README.md`

## Interfaces

```kotlin
// Levels.kt
enum class LevelKind { SINGLE, SEQUENCE, SHUFFLE, SEQUENCE_THEN_SHUFFLE, SONG, FALLING }

data class LevelSpec(
    val id: Int,              // 1..8
    val title: String,        // "Mix it up"
    val keys: List<Int>,      // MIDI, ascending
    val kind: LevelKind,
    val promptCount: Int,     // prompts in the shuffled part; 0 for SONG/FALLING
    val passes: Int = 1,      // times through the ordered part
)

fun curriculum(): List<LevelSpec>          // exactly 8, ids 1..8
fun levelById(id: Int): LevelSpec
fun keysUnlockedAfter(levelId: Int): List<Int>

const val KEY_C4 = 60; const val KEY_D4 = 62; const val KEY_E4 = 64
const val KEY_F4 = 65; const val KEY_G4 = 67
val FIVE_KEYS: List<Int> = listOf(60, 62, 64, 65, 67)
fun keyColorHex(midi: Int): String         // "#E5484D" … throws for anything else

// Prompts.kt
fun promptsFor(level: LevelSpec, seed: Long): List<Int>
// feeds LessonSession(notes = promptsFor(...))

// Stars.kt
fun starsFor(completed: Boolean, wrongPresses: Int): Int  // 0..3

// Songs.kt
data class Song(
    val id: String,
    val title: String,
    val midi: List<Int>,
    val syllables: List<String>,   // same size as midi
)
fun songLibrary(): List<Song>
fun songsFor(unlockedKeys: List<Int>): List<Song>

// FallingGame.kt
data class FallingTile(val id: Int, val midi: Int, val landsAtMs: Long)
enum class TileOutcome { HIT, WRONG_KEY, IGNORED }

class FallingGameSession(
    val tiles: List<FallingTile>,
    private val windowMs: Long = 700L,
) {
    val hitCount: Int
    val wrongCount: Int
    val missedIds: List<Int>
    fun activeTiles(nowMs: Long, fallMs: Long = 3000L): List<FallingTile>
    fun tileProgress(tile: FallingTile, nowMs: Long, fallMs: Long = 3000L): Float // 0f top, 1f line
    fun onPress(midi: Int?, atMs: Long): TileOutcome
    fun onTick(nowMs: Long): List<Int>   // ids that just expired
    val complete: Boolean
}

fun defaultTileSchedule(seed: Long, count: Int = 12, gapMs: Long = 2500L, firstAtMs: Long = 3000L): List<FallingTile>
```

Existing type changes:

```kotlin
// LessonSnapshot gains one field
data class LessonSnapshot(
    val expectedMidi: Int?,
    val heardMidi: Int?,      // NEW: the key actually judged on the last press, null when none
    …
)

// LessonSession gains one parameter, default preserves 0.5.0 copy
class LessonSession(
    private val notes: List<Int> = MVP_CALIBRATION_MIDI,   // now read as "ordered prompts"
    private val completionCopy: String = "You found all five keys!",
    …
)
```

---

### Task 1: Curriculum module and the eight levels

**Files:**
- Create: `android/core/curriculum/build.gradle.kts` (copy `android/core/learning/build.gradle.kts`, keep `:core:common`, `:core:notes`, `:core:learning`; drop `:core:pitch` and `:core:calibration`)
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Levels.kt`
- Test: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/LevelsTest.kt`
- Modify: `android/settings.gradle.kts` — add `include(":core:curriculum")` next to `include(":core:learning")`, outside the SDK-gated block

**Interfaces:**
- Consumes: nothing but `midiToNoteName` from `:core:notes`
- Produces: `LevelSpec`, `LevelKind`, `curriculum()`, `levelById`, `keysUnlockedAfter`, `FIVE_KEYS`, `keyColorHex`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vijaychhetry.kidspiano.core.curriculum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LevelsTest {
    @Test
    fun acLvl01_theLadderIsEightLevelsAndStartsOnOneKey() {
        val levels = curriculum()
        assertEquals(8, levels.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), levels.map { it.id })
        assertEquals(listOf(60), levels[0].keys)
        assertEquals(LevelKind.SINGLE, levels[0].kind)
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
        levels.forEach { level ->
            assertTrue(level.keys.all { it in FIVE_KEYS }, "level ${level.id} left C4–G4")
        }
    }

    @Test
    fun acLvl03_theThirdLevelShufflesTheSameTwoKeys() {
        val third = levelById(3)
        assertEquals(LevelKind.SHUFFLE, third.kind)
        assertEquals(listOf(60, 62), third.keys)
        assertTrue(third.promptCount >= 8)
    }

    @Test
    fun acLvl04_songAndGameSitAtTheTop() {
        assertEquals(LevelKind.SONG, levelById(7).kind)
        assertEquals(LevelKind.FALLING, levelById(8).kind)
        assertEquals(FIVE_KEYS, levelById(8).keys)
    }

    @Test
    fun acLvl05_unlockedKeysAccumulate() {
        assertEquals(listOf(60), keysUnlockedAfter(1))
        assertEquals(listOf(60, 62, 64), keysUnlockedAfter(4))
        assertEquals(FIVE_KEYS, keysUnlockedAfter(6))
        assertEquals(FIVE_KEYS, keysUnlockedAfter(8))
    }

    @Test
    fun acLvl06_everyKeyHasItsOwnColour() {
        val colours = FIVE_KEYS.map { keyColorHex(it) }
        assertEquals(5, colours.toSet().size)
        assertEquals("#E5484D", keyColorHex(60))
        assertEquals("#3A7DDE", keyColorHex(67))
        assertThrows(IllegalArgumentException::class.java) { keyColorHex(61) }
    }
}
```

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

enum class LevelKind { SINGLE, SEQUENCE, SHUFFLE, SEQUENCE_THEN_SHUFFLE, SONG, FALLING }

data class LevelSpec(
    val id: Int,
    val title: String,
    val keys: List<Int>,
    val kind: LevelKind,
    val promptCount: Int,
    val passes: Int = 1,
)

private val LADDER = listOf(
    LevelSpec(1, "One key: C", listOf(KEY_C4), LevelKind.SINGLE, promptCount = 6),
    LevelSpec(2, "Two keys: C and D", listOf(KEY_C4, KEY_D4), LevelKind.SEQUENCE, 0, passes = 3),
    LevelSpec(3, "Mix it up", listOf(KEY_C4, KEY_D4), LevelKind.SHUFFLE, promptCount = 8),
    LevelSpec(4, "Three keys: E joins", listOf(KEY_C4, KEY_D4, KEY_E4), LevelKind.SEQUENCE_THEN_SHUFFLE, 8),
    LevelSpec(5, "Four keys: F joins", listOf(KEY_C4, KEY_D4, KEY_E4, KEY_F4), LevelKind.SEQUENCE_THEN_SHUFFLE, 8),
    LevelSpec(6, "All five: G joins", FIVE_KEYS, LevelKind.SEQUENCE_THEN_SHUFFLE, 10),
    LevelSpec(7, "Song time", FIVE_KEYS, LevelKind.SONG, 0),
    LevelSpec(8, "Falling notes", FIVE_KEYS, LevelKind.FALLING, 12),
)

fun curriculum(): List<LevelSpec> = LADDER

fun levelById(id: Int): LevelSpec =
    LADDER.firstOrNull { it.id == id } ?: throw IllegalArgumentException("no level $id")

fun keysUnlockedAfter(levelId: Int): List<Int> =
    LADDER.filter { it.id <= levelId }
        .flatMap { it.keys }
        .distinct()
        .sorted()

private val COLORS = mapOf(
    KEY_C4 to "#E5484D",
    KEY_D4 to "#F76B15",
    KEY_E4 to "#E8B931",
    KEY_F4 to "#2FA84F",
    KEY_G4 to "#3A7DDE",
)

fun keyColorHex(midi: Int): String =
    COLORS[midi] ?: throw IllegalArgumentException("no colour for MIDI $midi; only C4–G4 are taught")
```

- [ ] **Step 4: Run tests and make sure they pass**

Run: `./gradlew :core:curriculum:test`
Expected: BUILD SUCCESSFUL, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add android/core/curriculum android/settings.gradle.kts
git commit -m "Add the eight-level ladder as data"
```

---

### Task 2: Prompt generation (the jumble)

**Files:**
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Prompts.kt`
- Test: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/PromptsTest.kt`

**Interfaces:**
- Consumes: `LevelSpec`, `LevelKind` from Task 1
- Produces: `promptsFor(level: LevelSpec, seed: Long): List<Int>` — the ordered list handed straight to `LessonSession(notes = …)`

Rules the tests pin down: a shuffled run uses **every** key of the level at least once, never repeats the same key more than twice in a row (otherwise a child can pass by hammering one key), and is reproducible for a given seed.

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
        val prompts = promptsFor(levelById(1), seed = 1L)
        assertEquals(List(6) { 60 }, prompts)
    }

    @Test
    fun acPro02_twoKeyLevelIsCThenDThreeTimes() {
        val prompts = promptsFor(levelById(2), seed = 1L)
        assertEquals(listOf(60, 62, 60, 62, 60, 62), prompts)
    }

    @Test
    fun acPro03_shuffleUsesBothKeysAndIsNotJustTheOrderedRun() {
        val prompts = promptsFor(levelById(3), seed = 7L)
        assertEquals(8, prompts.size)
        assertTrue(prompts.contains(60) && prompts.contains(62))
        assertTrue(prompts.all { it == 60 || it == 62 })
        assertNotEquals(listOf(60, 62, 60, 62, 60, 62, 60, 62), prompts)
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
        assertEquals(3 + 8, prompts.size)
        assertTrue(prompts.drop(3).toSet().containsAll(listOf(60, 62, 64)))
    }

    @Test
    fun acPro06_sameSeedGivesTheSameRunAndDifferentSeedsDiffer() {
        assertEquals(promptsFor(levelById(6), 11L), promptsFor(levelById(6), 11L))
        assertNotEquals(promptsFor(levelById(6), 11L), promptsFor(levelById(6), 12L))
    }

    @Test
    fun acPro07_songAndGameLevelsHaveNoDrillPrompts() {
        assertTrue(promptsFor(levelById(7), 1L).isEmpty())
        assertTrue(promptsFor(levelById(8), 1L).isEmpty())
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
    LevelKind.SINGLE -> List(level.promptCount) { level.keys.first() }
    LevelKind.SEQUENCE -> (1..level.passes).flatMap { level.keys }
    LevelKind.SHUFFLE -> shuffled(level.keys, level.promptCount, seed)
    LevelKind.SEQUENCE_THEN_SHUFFLE ->
        level.keys + shuffled(level.keys, level.promptCount, seed)
    LevelKind.SONG, LevelKind.FALLING -> emptyList()
}

private fun shuffled(keys: List<Int>, count: Int, seed: Long): List<Int> {
    val random = Random(seed)
    val out = ArrayList<Int>(count)
    val unused = keys.toMutableList()
    repeat(count) {
        val choices = keys.filter { candidate ->
            val tail = out.takeLast(2)
            !(tail.size == 2 && tail.all { it == candidate })
        }
        // Keys not used yet win, so every key of the level is asked at least once.
        val remainingSlots = count - out.size
        val pool = when {
            unused.isNotEmpty() && remainingSlots <= unused.size ->
                choices.filter { it in unused }.ifEmpty { choices }
            else -> choices
        }
        val pick = pool[random.nextInt(pool.size)]
        out += pick
        unused.remove(pick)
    }
    return out
}
```

- [ ] **Step 4: Run** `./gradlew :core:curriculum:test` — PASS, including the 200-seed sweep.

- [ ] **Step 5: Commit** `git commit -m "Generate level prompts, including a jumble a child cannot game"`

---

### Task 3: Stars

**Files:**
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Stars.kt`
- Test: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/StarsTest.kt`

**Interfaces:**
- Produces: `starsFor(completed: Boolean, wrongPresses: Int): Int`

- [ ] **Step 1: Failing test**

```kotlin
class StarsTest {
    @Test
    fun acStar01_finishingAlwaysEarnsAtLeastOneStar() {
        assertEquals(1, starsFor(completed = true, wrongPresses = 25))
    }

    @Test
    fun acStar02_twoOrFewerWrongKeysIsTwoStarsAndACleanRunIsThree() {
        assertEquals(3, starsFor(true, 0))
        assertEquals(2, starsFor(true, 1))
        assertEquals(2, starsFor(true, 2))
        assertEquals(1, starsFor(true, 3))
    }

    @Test
    fun acStar03_anUnfinishedLevelScoresNothingButIsNotAFailure() {
        assertEquals(0, starsFor(completed = false, wrongPresses = 0))
    }
}
```

`wrongPresses` is incremented by the caller **only** on `RecognitionStatus.INCORRECT`. Add the guard test in the same file:

```kotlin
    @Test
    fun acStar04_unclearAudioIsNotAWrongPress() {
        val session = LessonSession(notes = listOf(60, 62))
        session.begin(0)
        var wrong = 0
        // 20 frames of silence
        repeat(20) { frame ->
            val snap = session.onFrame(silentFrame(frame))
            if (snap.status == RecognitionStatus.INCORRECT) wrong++
        }
        assertEquals(0, wrong)
        assertEquals(3, starsFor(completed = true, wrongPresses = wrong))
    }
```

Copy `silentFrame` from `android/core/learning/src/test/kotlin/.../LessonSessionTest.kt`; do not invent a new audio helper.

- [ ] **Step 2: Run — FAIL.**
- [ ] **Step 3: Implement**

```kotlin
fun starsFor(completed: Boolean, wrongPresses: Int): Int = when {
    !completed -> 0
    wrongPresses == 0 -> 3
    wrongPresses <= 2 -> 2
    else -> 1
}
```

- [ ] **Step 4: PASS** `./gradlew :core:curriculum:test`
- [ ] **Step 5: Commit** `git commit -m "Score levels with generous stars and no failure"`

---

### Task 4: Song library

**Files:**
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/Songs.kt`
- Test: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/SongsTest.kt`

**Interfaces:**
- Produces: `Song`, `songLibrary()`, `songsFor(unlockedKeys: List<Int>): List<Song>`

- [ ] **Step 1: Failing test**

```kotlin
class SongsTest {
    @Test
    fun acSong01_everySongIsPlayableOnTheFiveWhiteKeys() {
        songLibrary().forEach { song ->
            assertTrue(song.midi.isNotEmpty(), "${song.id} has no notes")
            assertTrue(
                song.midi.all { it in FIVE_KEYS },
                "${song.id} needs a key we never teach: ${song.midi.filterNot { it in FIVE_KEYS }}",
            )
        }
    }

    @Test
    fun acSong02_everyNoteHasALyricSyllable() {
        songLibrary().forEach { song ->
            assertEquals(song.midi.size, song.syllables.size, "${song.id} syllables do not line up")
        }
    }

    @Test
    fun acSong03_hotCrossBunsIsUnlockedByThreeKeysAndOdeToJoyIsNot() {
        val threeKeys = listOf(60, 62, 64)
        val ids = songsFor(threeKeys).map { it.id }
        assertTrue("hot-cross-buns" in ids)
        assertTrue("au-clair-de-la-lune" in ids)
        assertFalse("ode-to-joy" in ids)
        assertFalse("mary-had-a-little-lamb" in ids)
    }

    @Test
    fun acSong04_allSixSongsAreOfferedOnceEveryKeyIsLearned() {
        assertEquals(6, songsFor(FIVE_KEYS).size)
    }

    @Test
    fun acSong05_theMelodiesAreTheRealTunes() {
        val hotCross = songLibrary().single { it.id == "hot-cross-buns" }
        assertEquals(listOf(64, 62, 60), hotCross.midi.take(3))
        assertEquals(listOf("Hot", "cross", "buns"), hotCross.syllables.take(3))

        val ode = songLibrary().single { it.id == "ode-to-joy" }
        assertEquals(listOf(64, 64, 65, 67, 67, 65, 64, 62), ode.midi.take(8))

        val mary = songLibrary().single { it.id == "mary-had-a-little-lamb" }
        assertEquals(listOf(64, 62, 60, 62, 64, 64, 64), mary.midi.take(7))
        assertEquals(26, mary.midi.size)
    }

    @Test
    fun acSong06_songsAreOrderedEasiestFirst() {
        val sizes = songsFor(FIVE_KEYS).map { it.midi.size }
        assertEquals(sizes.sorted(), sizes)
    }
}
```

- [ ] **Step 2: Run — FAIL.**

- [ ] **Step 3: Implement** — exact data, all traditional or original, octave 4 only:

```kotlin
data class Song(
    val id: String,
    val title: String,
    val midi: List<Int>,
    val syllables: List<String>,
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

private val AU_CLAIR = Song(
    "au-clair-de-la-lune", "Au Clair de la Lune",
    listOf(60, 60, 60, 62, 64, 62, 60, 64, 62, 62, 60),
    listOf("Au", "clair", "de", "la", "lu", "ne", "mon", "a", "mi", "Pier", "rot"),
)

private val STEP_UP = Song(
    "step-up", "Step Up",
    listOf(60, 62, 64, 65, 65, 64, 62, 60),
    listOf("Up", "we", "go", "now", "back", "we", "come", "home"),
)

private val ARE_YOU_SLEEPING = Song(
    "are-you-sleeping", "Are You Sleeping",
    listOf(60, 62, 64, 60, 60, 62, 64, 60, 64, 65, 67, 64, 65, 67),
    listOf(
        "Are", "you", "sleep", "ing", "Are", "you", "sleep", "ing",
        "Broth", "er", "John", "Broth", "er", "John",
    ),
)

private val ODE_TO_JOY = Song(
    "ode-to-joy", "Ode to Joy",
    listOf(64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 62),
    listOf(
        "Joy", "ful", "joy", "ful", "we", "a", "dore", "thee",
        "God", "of", "glo", "ry", "Lord", "of", "love",
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

Count check before writing the test green: Hot Cross Buns 17 notes / 17 syllables, Au Clair 11 / 11, Step Up 8 / 8, Are You Sleeping 14 / 14, Ode to Joy 15 / 15, Mary 26 / 26. `acSong02` fails loudly if any pair drifts.

- [ ] **Step 4: PASS** `./gradlew :core:curriculum:test`
- [ ] **Step 5: Commit** `git commit -m "Add six tunes that fit inside C4 to G4"`

---

### Task 5: LessonSession drives songs and reports the wrong key

**Files:**
- Modify: `android/core/learning/src/main/kotlin/com/vijaychhetry/kidspiano/core/learning/LessonSession.kt`
- Modify: `android/core/learning/src/test/kotlin/com/vijaychhetry/kidspiano/core/learning/LessonSessionTest.kt`

**Interfaces:**
- Consumes: `promptsFor`, `Song.midi`
- Produces: `LessonSnapshot.heardMidi`, `LessonSession(completionCopy = …)`

A song and a level are the same thing to this class: an ordered prompt list. Nothing about the judging changes.

- [ ] **Step 1: Failing tests** (append to `LessonSessionTest`)

```kotlin
@Test
fun acLearn13_theSnapshotNamesTheKeyTheChildActuallyPressed() {
    val session = LessonSession(notes = listOf(60, 62))
    session.begin(0)
    val snap = play(session, midi = 64, atFrame = 0)
    assertEquals(RecognitionStatus.INCORRECT, snap.status)
    assertEquals(64, snap.heardMidi, "the screen must be able to flash the key they hit")
    assertEquals(60, snap.expectedMidi)
}

@Test
fun acLearn14_aRepeatedPromptStillNeedsASecondPress() {
    val session = LessonSession(notes = listOf(60, 60))
    session.begin(0)
    val first = playPress(session, midi = 60, fromFrame = 0)
    assertEquals(RecognitionStatus.CORRECT, first.status)
    assertEquals(1, first.completedCount)
    // Keep holding the same key: the lock must not score the second C4 for free.
    val held = hold(session, midi = 60, atFrame = 40)
    assertEquals(1, held.completedCount, "a held key cannot answer the next prompt")
}

@Test
fun acLearn15_aSongIsJustAPromptList() {
    val session = LessonSession(
        notes = listOf(64, 62, 60),
        completionCopy = "You played Hot Cross Buns!",
    )
    session.begin(0)
    var frame = 0
    listOf(64, 62, 60).forEach { midi ->
        playPress(session, midi = midi, fromFrame = frame)
        frame += 40
    }
    val snap = session.snapshot()
    assertTrue(snap.complete)
    assertEquals("You played Hot Cross Buns!", snap.feedback)
}

@Test
fun acLearn16_unclearAudioDuringASongNeitherAdvancesNorScoresWrong() {
    val session = LessonSession(notes = listOf(64, 62))
    session.begin(0)
    val snap = playHz(session, hz = 269.0, atFrame = 0) // between C and C#
    assertNotEquals(RecognitionStatus.INCORRECT, snap.status)
    assertEquals(0, snap.completedCount)
}
```

`play`, `playPress`, `hold`, `playHz` already exist in that file.

- [ ] **Step 2: Run — FAIL** (`heardMidi` and `completionCopy` unresolved).

Run: `./gradlew :core:learning:test`

- [ ] **Step 3: Implement**

Add the field to the data class and set it where the press is judged. In `onFrame`, the `phase == NotePhase.STABLE && pitch.midiNote != judgedMidi` branch already assigns `judgedMidi = pitch.midiNote`; `snapshotFrom` reads `judgedMidi` for the new field:

```kotlin
data class LessonSnapshot(
    val expectedMidi: Int?,
    val heardMidi: Int?,
    …
)

// in snapshotFrom(...)
heardMidi = judgedMidi,
```

Replace both hardcoded completion strings with the parameter:

```kotlin
class LessonSession(
    private val notes: List<Int> = MVP_CALIBRATION_MIDI,
    private val completionCopy: String = "You found all five keys!",
    …
)
```

`restart()` must clear `judgedMidi` (it already does) so `heardMidi` starts null.

Do **not** touch the `judgedMidi` lock, the ambiguity guard, or `pressIsLocked()`.

- [ ] **Step 4: PASS** `./gradlew :core:learning:test :core:curriculum:test`
- [ ] **Step 5: Commit** `git commit -m "Report the key the child actually pressed, and let a song set the ending"`

---

### Task 6: The falling-notes game clock

**Files:**
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/FallingGame.kt`
- Test: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/FallingGameTest.kt`

**Interfaces:**
- Consumes: `FIVE_KEYS`
- Produces: `FallingTile`, `TileOutcome`, `FallingGameSession`, `defaultTileSchedule`

This class holds no audio and no Compose. It is a clock plus a list, so timing can be tested to the millisecond.

- [ ] **Step 1: Failing test**

```kotlin
class FallingGameTest {
    private fun session(vararg tiles: FallingTile) = FallingGameSession(tiles.toList())

    @Test
    fun acGame01_pressingOnTheLineIsAHit() {
        val s = session(FallingTile(1, 60, landsAtMs = 3000))
        assertEquals(TileOutcome.HIT, s.onPress(60, atMs = 3000))
        assertEquals(1, s.hitCount)
        assertTrue(s.complete)
    }

    @Test
    fun acGame02_theWindowIsWideEnoughForMicrophoneLag() {
        assertEquals(TileOutcome.HIT, session(FallingTile(1, 60, 3000)).onPress(60, 2400))
        assertEquals(TileOutcome.HIT, session(FallingTile(1, 60, 3000)).onPress(60, 3600))
    }

    @Test
    fun acGame03_pressingFarTooEarlyIsIgnoredNotPunished() {
        val s = session(FallingTile(1, 60, 3000))
        assertEquals(TileOutcome.IGNORED, s.onPress(60, atMs = 1000))
        assertEquals(0, s.wrongCount)
        assertEquals(0, s.hitCount)
    }

    @Test
    fun acGame04_aWrongKeyInsideTheWindowIsNamedButDoesNotBurnTheTile() {
        val s = session(FallingTile(1, 60, 3000))
        assertEquals(TileOutcome.WRONG_KEY, s.onPress(64, atMs = 3000))
        assertEquals(1, s.wrongCount)
        assertEquals(TileOutcome.HIT, s.onPress(60, atMs = 3200))
        assertEquals(1, s.hitCount)
    }

    @Test
    fun acGame05_unclearAudioNeverHitsAndNeverMisses() {
        val s = session(FallingTile(1, 60, 3000))
        assertEquals(TileOutcome.IGNORED, s.onPress(null, atMs = 3000))
        assertEquals(0, s.wrongCount)
        assertTrue(s.onTick(3100).isEmpty())
    }

    @Test
    fun acGame06_aMissedTileComesBackExactlyOnce() {
        val s = session(FallingTile(1, 60, 3000))
        assertEquals(listOf(1), s.onTick(3701))
        assertEquals(listOf(1), s.missedIds)
        assertFalse(s.complete, "a missed tile is requeued, not lost")
        val requeued = s.tiles.filter { it.id == 1 }
        assertEquals(2, requeued.size, "requeued once")
        assertTrue(requeued.last().landsAtMs > 3701)
        // It must not be requeued a second time.
        s.onTick(requeued.last().landsAtMs + 701)
        assertEquals(2, s.tiles.count { it.id == 1 })
        assertTrue(s.complete)
    }

    @Test
    fun acGame07_thereIsNoFailState() {
        val s = FallingGameSession(defaultTileSchedule(seed = 1L))
        var now = 0L
        repeat(200) {
            now += 200
            s.onTick(now)
        }
        assertTrue(s.complete)
        assertEquals(0, s.hitCount)
        // Missing every tile still ends the run; nothing throws, nothing "loses".
    }

    @Test
    fun acGame08_theDefaultRunIsTwelveSlowTilesOnTaughtKeysOnly() {
        val tiles = defaultTileSchedule(seed = 5L)
        assertEquals(12, tiles.size)
        assertTrue(tiles.all { it.midi in FIVE_KEYS })
        assertEquals(3000L, tiles.first().landsAtMs)
        val gaps = tiles.zipWithNext { a, b -> b.landsAtMs - a.landsAtMs }
        assertTrue(gaps.all { it == 2500L }, "tiles must not overlap on a first run")
    }

    @Test
    fun acGame09_tileProgressRunsFromTopToLine() {
        val tile = FallingTile(1, 60, landsAtMs = 3000)
        val s = session(tile)
        assertEquals(0f, s.tileProgress(tile, nowMs = 0), 0.01f)
        assertEquals(0.5f, s.tileProgress(tile, nowMs = 1500), 0.01f)
        assertEquals(1f, s.tileProgress(tile, nowMs = 3000), 0.01f)
    }
}
```

- [ ] **Step 2: Run — FAIL.**

- [ ] **Step 3: Implement.** Keep `tiles` a mutable backing list exposed read-only; `onTick` expires anything whose window has closed, requeues each id at most once at `nowMs + gapMs`, and `complete` is true when no tile is outstanding. `onPress(null, …)` returns `IGNORED` before any other branch. A press outside every open window returns `IGNORED` — never `WRONG_KEY` — so a child noodling between tiles is not scolded.

- [ ] **Step 4: PASS** `./gradlew :core:curriculum:test`
- [ ] **Step 5: Commit** `git commit -m "Add a forgiving falling-notes clock with no fail state"`

---

### Task 7: Progress store

**Files:**
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/progress/ProgressStore.kt`
- Modify: `android/app/.../export/SessionLogStore.kt` call sites — append one line per level result

**Interfaces:**
- Consumes: `curriculum()`, `starsFor`
- Produces: `ProgressStore.stars(levelId)`, `record(levelId, stars)`, `highestUnlocked()`, `totalStars()`

Store in the existing `calibration` prefs under `level.<id>.stars`. A level is unlocked when the previous level has at least one star; level 1 is always unlocked. Keep the JVM-testable rule in `:core:curriculum`:

```kotlin
fun highestUnlocked(starsByLevel: Map<Int, Int>): Int
```

- [ ] **Step 1: Failing test** in `LevelsTest`

```kotlin
@Test
fun acLvl07_levelOneIsOpenAndTheNextOneWaitsForAStar() {
    assertEquals(1, highestUnlocked(emptyMap()))
    assertEquals(1, highestUnlocked(mapOf(1 to 0)))
    assertEquals(2, highestUnlocked(mapOf(1 to 1)))
    assertEquals(4, highestUnlocked(mapOf(1 to 3, 2 to 1, 3 to 2)))
    assertEquals(8, highestUnlocked((1..8).associateWith { 3 }))
}
```

- [ ] **Step 2: FAIL.**
- [ ] **Step 3:** Implement `highestUnlocked` in `Levels.kt`, then the Android `ProgressStore` wrapping prefs.
- [ ] **Step 4:** `./gradlew :core:curriculum:test`
- [ ] **Step 5:** Commit `git commit -m "Remember stars and unlock the next stop"`

---

### Task 8: Colour the keyboard and flash the wrong key

**Files:**
- Modify: `android/app/src/main/java/com/vijaychhetry/kidspiano/ui/PianoKeyboardView.kt`

**Interfaces:**
- Consumes: `keyColorHex`, `LessonSnapshot.heardMidi`
- Produces: `PianoKeyboardView(highlightMidi, flashMidi, coloredMidi, modifier)`

```kotlin
@Composable
fun PianoKeyboardView(
    highlightMidi: Int?,
    modifier: Modifier = Modifier,
    flashMidi: Int? = null,          // amber pulse on the key the child actually hit
    coloredMidi: List<Int> = emptyList(),  // taught keys wear their own colour
)
```

The target key is filled with `keyColorHex(highlightMidi)`; taught-but-not-target keys get a 35% tint of their own colour; `flashMidi` draws a 3 dp amber ring that fades over 600 ms via `animateFloatAsState`. Do not add `clickable`.

Proof: `:app:assembleDebug` plus a screenshot on a device. No Compose test harness exists in this repo and empty `androidTest` stubs are banned.

- [ ] **Step 1:** `./gradlew :app:assembleDebug` before the change, to know the baseline is green.
- [ ] **Step 2–3:** Add the parameters; keep all existing call sites compiling by defaulting them.
- [ ] **Step 4:** `./gradlew :app:assembleDebug`
- [ ] **Step 5:** Commit `git commit -m "Colour the taught keys and flash the one the child hit"`

---

### Task 9: Level map screen

**Files:**
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/levels/LevelMapScreen.kt`
- Modify: `android/app/.../home/HomeScreen.kt` — Play opens the map
- Modify: `android/app/.../MainActivity.kt` — `Dest.LEVEL_MAP`

Eight stops on a winding path, `LazyColumn` with alternating start/end alignment. Each stop: number, title, up to three stars, and either the key colours it teaches or a padlock. Locked stops are 40% alpha and not clickable. The map is the child's home after Practice.

- [ ] **Step 1:** `assembleDebug` baseline.
- [ ] **Step 2–3:** Build the screen from `curriculum()` + `ProgressStore`.
- [ ] **Step 4:** `assembleDebug`.
- [ ] **Step 5:** Commit `git commit -m "Add the level map"`

---

### Task 10: Drill screen for levels 1–6

**Files:**
- Modify: `android/app/.../lesson/LessonScreen.kt`, `LessonViewModel.kt`

`LessonViewModel.startLevel(level: LevelSpec, seed: Long, profile: CalibrationProfile)` builds `LessonSession(notes = promptsFor(level, seed), completionCopy = "Level ${level.id} done!")` through `lessonSessionFor`. UI state gains `levelTitle`, `heardMidi`, `wrongPresses`, `stars`.

Hero note name is tinted `keyColorHex(expectedMidi)`. Progress becomes pips, one per prompt, filled as they are answered. On completion, navigate to the summary with `starsFor(true, wrongPresses)`.

- [ ] **Step 1–5:** as Task 9, commit `git commit -m "Run a level from the ladder on the practice screen"`

---

### Task 11: Song screen

**Files:**
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/song/SongScreen.kt`

Horizontal `LazyRow` of note bubbles: circle in `keyColorHex(midi)` with the note name, syllable underneath. Current note is 1.4× and outlined; answered notes get a small tick; upcoming notes are 60% alpha. Auto-scrolls to keep the current note centred. Same keyboard below. Engine is `LessonSession(notes = song.midi, completionCopy = "You played ${song.title}!")`.

- [ ] **Step 1–5:** commit `git commit -m "Play a tune, one note at a time, at the child's pace"`

---

### Task 12: Falling game screen

**Files:**
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/game/FallingGameScreen.kt`, `FallingGameViewModel.kt`

Tiles are `Box`es positioned by `tileProgress(tile, now)` against the hit line, which sits directly on the keyboard. The ViewModel drives a 60 ms ticker (same pattern as `CalibrationViewModel`'s `TICK_MS`) and forwards each judged press from the recognition pipeline to `session.onPress(midi, nowMs)`. Hit: the tile bursts into stars. `WRONG_KEY`: name it in the feedback line and flash the key. `IGNORED`: nothing at all.

No health bar, no score multiplier that can go down, no "game over" text anywhere.

- [ ] **Step 1–5:** commit `git commit -m "Add the falling-notes game screen"`

---

### Task 13: Session summary, wiring, 0.6.0

**Files:**
- Create: `android/app/.../summary/SummaryScreen.kt`
- Modify: `MainActivity.kt`, `android/app/build.gradle.kts`, `docs/android/ACCEPTANCE_CRITERIA.md`, `android/README.md`, `releases/README.md`
- Copy: APK → `releases/KidsPiano-debug-0.6.0.apk`

Summary shows the stars with a scale-in animation, one line of what was learned, then `Play a song` (only when `songsFor(keysUnlockedAfter(levelId))` is non-empty), `Play again`, and `Level map`.

New AC rows, IDs matching test names:

| ID | Criterion | Test |
| --- | --- | --- |
| AC-LVL-01 | Eight levels, first is one key | `LevelsTest.acLvl01_theLadderIsEightLevelsAndStartsOnOneKey` |
| AC-LVL-02 | Keys grow one at a time, never leave C4–G4 | `acLvl02_keysGrowOneAtATimeAndNeverLeaveC4ToG4` |
| AC-LVL-03 | Level 3 shuffles the same two keys | `acLvl03_theThirdLevelShufflesTheSameTwoKeys` |
| AC-LVL-05 | Unlocked keys accumulate | `acLvl05_unlockedKeysAccumulate` |
| AC-LVL-07 | Level 1 open, next waits for a star | `acLvl07_levelOneIsOpenAndTheNextOneWaitsForAStar` |
| AC-PRO-04 | A jumble never repeats a key three times running | `PromptsTest.acPro04_shuffleNeverRepeatsAKeyMoreThanTwiceInARow` |
| AC-PRO-06 | Seeded runs are reproducible | `acPro06_sameSeedGivesTheSameRunAndDifferentSeedsDiffer` |
| AC-STAR-02 | Two or fewer wrong is two stars | `StarsTest.acStar02_twoOrFewerWrongKeysIsTwoStarsAndACleanRunIsThree` |
| AC-STAR-04 | Unclear audio is not a wrong press | `acStar04_unclearAudioIsNotAWrongPress` |
| AC-SONG-01 | Every song fits C4–G4 | `SongsTest.acSong01_everySongIsPlayableOnTheFiveWhiteKeys` |
| AC-SONG-03 | Songs unlock by keys learned | `acSong03_hotCrossBunsIsUnlockedByThreeKeysAndOdeToJoyIsNot` |
| AC-LEARN-13 | Snapshot names the key actually pressed | `LessonSessionTest.acLearn13_theSnapshotNamesTheKeyTheChildActuallyPressed` |
| AC-LEARN-14 | A repeated prompt needs a second press | `acLearn14_aRepeatedPromptStillNeedsASecondPress` |
| AC-GAME-02 | Hit window survives microphone lag | `FallingGameTest.acGame02_theWindowIsWideEnoughForMicrophoneLag` |
| AC-GAME-04 | Wrong key does not burn the tile | `acGame04_aWrongKeyInsideTheWindowIsNamedButDoesNotBurnTheTile` |
| AC-GAME-05 | Unclear audio never hits or misses | `acGame05_unclearAudioNeverHitsAndNeverMisses` |
| AC-GAME-06 | A missed tile returns exactly once | `acGame06_aMissedTileComesBackExactlyOnce` |
| AC-GAME-07 | There is no fail state | `acGame07_thereIsNoFailState` |

**Automation (must run before claiming done):**

```bash
cd android
./gradlew :core:notes:test :core:pitch:test :core:calibration:test \
          :core:learning:test :core:diagnostics:test :core:curriculum:test
./gradlew :app:assembleDebug
```

Expected: 0 failures. Read the new total from `build/test-results` and put that number in ACCEPTANCE_CRITERIA.

On-device checklist (gradle cannot see any of this):

1. Level 1 asks for C4 six times and the hero is red.
2. Play E4 when C4 is asked — the E4 key flashes amber and the words name both keys.
3. Level 3 prompts are not simply C D C D.
4. Hold a correct C4 for five seconds on a repeated prompt — it must not auto-answer the next one.
5. Finish level 4, take the song, play Hot Cross Buns to the end.
6. In the game, a note played half a second late still counts.
7. Stay silent through a whole game run — it ends calmly with no "you lose".

- [ ] **Step 1–3:** AC rows, gradle, APK copy, commit, push, update the PR.

```bash
git add android docs releases
git commit -m "Ship 0.6.0 levels, songs, and the falling-notes game"
git push -u origin HEAD
```

---

## Self-review

**Spec coverage:** one key → Task 1/2 level 1. Two keys → level 2. Jumble two keys → level 3 + `acPro04`. Third key → level 4, then 5 and 6. Falling game → Tasks 6 and 12. Feedback on every press → Task 5 `heardMidi` + Task 8 amber flash + the feedback table in the spec. Songs playable on the learned keys, offered at the end of a session → Tasks 4, 11, 13. Attractive kid screens → key colours (Task 1), level map (Task 9), bubbles and stars (Tasks 11, 13). Structure before code → this plan plus `docs/ui-mockups/kids-prototype.html`.

**Placeholders:** none. Every melody is written out as MIDI with matching syllables and counts.

**Type consistency:** `LevelSpec`, `LevelKind`, `promptsFor`, `starsFor`, `Song`, `songsFor`, `FallingTile`, `TileOutcome`, `FallingGameSession`, `LessonSnapshot.heardMidi`, `PianoKeyboardView(highlightMidi, flashMidi, coloredMidi)` are spelled the same in every task that uses them.

**Risk being carried on purpose:** the falling game is the only place where microphone latency becomes visible to the child. The ±700 ms window is the mitigation, and `acGame02` pins it so nobody "tightens" it later without arguing with a test.

## Execution (only after the human approves the screens)

Subagent-driven, one task per subagent, JVM tests after each. Tasks 8–13 are Compose and have no unit tests, so they get a reviewer pass instead, checking that the held-note lock, the never-wrong-on-unclear rule, and the no-fail-state rule survived contact with the UI.
