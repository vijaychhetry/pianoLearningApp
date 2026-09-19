# Falling-Notes Game Implementation Plan (0.7.0)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add level 8 — coloured note tiles fall to a hit line above the keyboard and the child plays each one as it lands. Forgiving timing, no way to lose.

**Do not start this before 0.6.0 is on a real phone and a real session log has been exported.** The one number this feature's correctness depends on — how long it takes from hammer to judgement — is the one number no unit test in this repo can produce. The arithmetic below is an estimate; the log is the measurement. `docs/superpowers/specs/2026-09-19-levels-songs-and-falling-game-design.md` explains this under "Why the game ships second".

**Architecture:** Two new pieces. `PressStream` is the frame loop lifted out of `LessonSession` into `:core:pitch`, so the game's audio path is the *same* code that already enforces "unclear is never wrong" and the held-note lock — not a fourth hand-written copy of it in a ViewModel. `FallingGameSession` is a pure clock over an append-only tile schedule, so every timing rule is testable to the millisecond.

**Tech Stack:** Kotlin, JUnit 5, Jetpack Compose Material 3. No new third-party libraries.

## Global Constraints

- Ships as **0.7.0** / `versionCode 7`, on top of 0.6.0.
- Timing constants, all named in one place:

| Constant | Value | Why |
| --- | --- | --- |
| `FALL_MS` | 3000 | slow enough to read the letter and find the key |
| `SPAWN_GAP_MS` | 2500 | two tiles share the screen for ~500 ms; windows never touch |
| `EARLY_MS` | 450 | grace before the line |
| `LATE_MS` | 900 | grace after the line |
| `TILE_COUNT` | 12 | one run |

- **The window is asymmetric, and that is the whole point.** Everything between hammer and judgement costs time and none of it can be negative: ~30 ms of microphone and capture buffer, 46–93 ms for the first 2048-sample block that ends after the onset and carries usable pitch, **92.9 ms** for the two further agreeing frames that `DebounceConfig(stableFrames = 3)` requires, ~10 ms of compute. That is ~200 ms typically and 250–300 ms when the attack transient costs a frame. The app is **never early**. A window centred on the detection timestamp would hand the child ~950 ms of early grace and only ~450 ms of late grace, and beginners are late, not early.
- **Compensate once, not twice.** The asymmetry *is* the latency compensation. Do not also subtract a latency constant from `atMs` — that double-counts and pushes the effective window back before the tile is even readable.
- Lanes are `FIVE_KEYS.indexOf(midi)`, five lanes left to right in pitch order. That is the only mapping a child can use without being told, and the Compose layer must not invent another.
- A missed tile gets **one** second chance, appended **after the last tile of the run**. Never requeued nearby: with `SPAWN_GAP_MS = 2500` a nearby requeue lands its window on top of a live tile, and a struggling child misses several in a row, so the collisions stack. `acGame10` pins this.
- Tile ids are **unique for the whole run and never reused**, including second chances. Two entries with the same id would throw in any `LazyRow`/`key(tile.id)`.
- A tile stays drawn past the line, fading, until its window closes. Over a third of the window lives past the line, and that is exactly when a struggling child finally presses.
- Unclear / two-note / too-quiet audio never hits, never misses, and never consumes a tile. A press with no window open is ignored, not scolded.
- The on-screen counter counts **catches only**. No miss tally, no health bar, no "game over". A visible failure count is a health bar with extra steps.
- Stars come from catches: 12 → ★★★, 8 or more → ★★, 1 or more → ★, none → no stars but no punishment either. Scoring the game on wrong presses alone hands three stars to a child who stood in silence, which is the opposite of no-fail.
- Tests first, and **every new test must fail under a plausible mutation** (`docs/android/ACCEPTANCE_CRITERIA.md`, "How we know these tests have teeth").
- Debug APK goes to `releases/KidsPiano-debug-0.7.0.apk`.

## File map

- Create: `android/core/pitch/src/main/kotlin/com/vijaychhetry/kidspiano/core/pitch/PressStream.kt`
- Create: `android/core/pitch/src/test/kotlin/com/vijaychhetry/kidspiano/core/pitch/PressStreamTest.kt`
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/FallingGame.kt`
- Create: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/FallingGameTest.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/game/FallingGameScreen.kt`, `FallingGameViewModel.kt`
- Modify: `android/core/learning/.../LessonSession.kt` — delegate the frame loop to `PressStream`
- Modify: `android/core/curriculum/.../Levels.kt` — append level 8, expose `jumbleKeys`
- Modify: `android/core/curriculum/.../Prompts.kt` — make the jumble reusable
- Modify: `android/core/curriculum/src/test/.../LevelsTest.kt` — `acLvl01` becomes eight levels
- Modify: `MainActivity.kt`, `android/app/build.gradle.kts`, `docs/android/ACCEPTANCE_CRITERIA.md`, `releases/README.md`

## Interfaces

```kotlin
// :core:pitch — PressStream.kt
data class Press(val midi: Int, val atMs: Long, val confidence: Double)

sealed interface FrameEvent {
    /** One physical press, judged once. */
    data class Judged(val press: Press) : FrameEvent
    /** Two notes at once, or a pitch between keys. Never wrong. */
    data object Unclear : FrameEvent
    /** ATTACK with no STABLE: a poke too short to name. */
    data object TooShort : FrameEvent
    /** A press already judged and still held down. */
    data object Holding : FrameEvent
    data object Quiet : FrameEvent
    data object Listening : FrameEvent
}

class PressStream(
    private val detector: PitchDetector = YinHpsPitchDetector(),
    private val debouncer: NoteDebouncer = NoteDebouncer(),
) {
    val lastPitch: PitchResult?
    val phase: NotePhase
    val heldMidi: Int?          // judged and still down; null at IDLE
    fun onFrame(frame: AudioFrame): FrameEvent
    fun reset()
}

// :core:curriculum — FallingGame.kt
data class FallingTile(
    val id: Int,                       // unique for the whole run, never reused
    val midi: Int,
    val landsAtMs: Long,
    val secondChanceFor: Int? = null,  // makes requeue-once structural
)

enum class TileState { WAITING, FALLING, HIT, EXPIRED }
enum class PressOutcome { HIT, WRONG_KEY, IGNORED }

const val FALL_MS = 3000L
const val SPAWN_GAP_MS = 2500L
const val EARLY_MS = 450L
const val LATE_MS = 900L
const val TILE_COUNT = 12

class FallingGameSession(
    schedule: List<FallingTile>,
    private val earlyMs: Long = EARLY_MS,
    private val lateMs: Long = LATE_MS,
    private val fallMs: Long = FALL_MS,
    private val gapMs: Long = SPAWN_GAP_MS,
) {
    val schedule: List<FallingTile>        // append-only, unique ids
    val hitCount: Int
    val wrongCount: Int
    val expiredIds: List<Int>
    val complete: Boolean
    fun stateOf(id: Int): TileState
    fun activeTiles(nowMs: Long): List<FallingTile>
    fun tileProgress(tile: FallingTile, nowMs: Long): Float   // 0 top, 1 line, >1 past
    fun laneOf(tile: FallingTile): Int
    fun onPress(midi: Int?, atMs: Long): PressOutcome
    fun onTick(nowMs: Long): List<FallingTile>                // newly expired
    fun starsForRun(): Int
}

fun defaultTileSchedule(seed: Long, count: Int = TILE_COUNT): List<FallingTile>

// :core:curriculum — Prompts.kt, promoted from private
fun jumbleKeys(keys: List<Int>, count: Int, seed: Long, precededBy: List<Int> = emptyList()): List<Int>
```

---

### Task 0: Extract `PressStream` from `LessonSession`

**Files:**
- Create: `android/core/pitch/src/main/kotlin/com/vijaychhetry/kidspiano/core/pitch/PressStream.kt`
- Create: `android/core/pitch/src/test/kotlin/com/vijaychhetry/kidspiano/core/pitch/PressStreamTest.kt`
- Modify: `android/core/learning/.../LessonSession.kt`

**Interfaces:**
- Consumes: `PitchDetector`, `NoteDebouncer`, `NotePhase`, `PitchResult`
- Produces: `Press`, `FrameEvent`, `PressStream`

This is a refactor with no behaviour change. It exists because `LessonSession.onFrame` currently owns four rules that the game also needs and that live nowhere else: the ambiguity guard, the held-note lock, the one-judgement-per-press rule, and the too-short-press detection. `CalibrationRunner` and `LabSession` already each reimplement their own variant; a fourth copy inside `FallingGameViewModel` would put the two hardest safety rules in the app in the one layer `ACCEPTANCE_CRITERIA.md` admits is untested.

**The existing AC-LEARN and AC-DEB suites are the regression net for this task. Not one of them may change.** If any existing assertion has to be edited to make this compile or pass, stop: the extraction changed behaviour and is wrong.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.notes.midiToFreq
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private const val SR = 44100
private const val FRAME_MS = 46L

class PressStreamTest {
    @Test
    fun acPress01_onePhysicalPressIsJudgedExactlyOnce() {
        val stream = PressStream()
        val events = (0 until 10).map { stream.onFrame(tone(midiToFreq(60), it)) }
        val judged = events.filterIsInstance<FrameEvent.Judged>()
        assertEquals(1, judged.size, "got: $events")
        assertEquals(60, judged.single().press.midi)
        // Every later frame of the same hold reports Holding, not a new press.
        assertInstanceOf(FrameEvent.Holding::class.java, events.last())
    }

    @Test
    fun acPress02_aHeldNoteIsNotJudgedAgainUntilItIsReleased() {
        val stream = PressStream()
        repeat(10) { stream.onFrame(tone(midiToFreq(60), it)) }
        repeat(4) { i -> stream.onFrame(silence(10 + i)) }   // releaseFrames = 4
        assertNull(stream.heldMidi)
        val again = (0 until 8).map { stream.onFrame(tone(midiToFreq(60), 14 + it)) }
        assertEquals(1, again.filterIsInstance<FrameEvent.Judged>().size)
    }

    @Test
    fun acPress03_twoNotesAtOnceAreUnclearAndNeverAPress() {
        val stream = PressStream()
        val mix = mixTones(
            sineTone(midiToFreq(60), SR, 0.6, amplitude = 0.35f),
            sineTone(midiToFreq(64), SR, 0.6, amplitude = 0.35f),
        )
        val events = (0 until 10).map { i ->
            val start = (2000 + i * 64).coerceAtMost(mix.size - 2048)
            stream.onFrame(AudioFrame(mix.copyOfRange(start, start + 2048), SR, i * FRAME_MS))
        }
        assertEquals(0, events.filterIsInstance<FrameEvent.Judged>().size, "got: $events")
        assertInstanceOf(FrameEvent.Unclear::class.java, events.last())
    }

    @Test
    fun acPress04_aPokeTooShortToNameReportsTooShortAndNoPress() {
        val stream = PressStream()
        stream.onFrame(tone(midiToFreq(62), 0))
        stream.onFrame(tone(midiToFreq(62), 1))     // ATTACK; stableFrames = 3
        val event = stream.onFrame(silence(2))
        assertInstanceOf(FrameEvent.TooShort::class.java, event)
    }

    @Test
    fun acPress05_silenceIsQuietAndNotUnclear() {
        val stream = PressStream()
        assertInstanceOf(FrameEvent.Quiet::class.java, stream.onFrame(silence(0)))
    }

    @Test
    fun acPress06_thePressCarriesTheFrameTimestamp() {
        val stream = PressStream()
        var press: Press? = null
        repeat(8) { i ->
            (stream.onFrame(tone(midiToFreq(60), i)) as? FrameEvent.Judged)?.let { press = it.press }
        }
        // stableFrames = 3, so the judgement lands on the third agreeing frame.
        assertEquals(2 * FRAME_MS, press!!.atMs)
    }

    private fun tone(hz: Double, index: Int): AudioFrame {
        val t = pianoTone(hz, SR, 1.0)
        val start = (2000 + index % 8 * 64).coerceAtMost(t.size - 2048)
        return AudioFrame(t.copyOfRange(start, start + 2048), SR, index * FRAME_MS)
    }

    private fun silence(index: Int) = AudioFrame(FloatArray(2048), SR, index * FRAME_MS)
}
```

`acPress06` pins the detection timestamp, which is what the whole hit window is measured against. If the judgement ever moves to a different frame, every timing number in this plan shifts and this test says so.

- [ ] **Step 2: Run — FAIL.** `./gradlew :core:pitch:test --tests '*PressStreamTest'`

- [ ] **Step 3: Implement `PressStream`, then make `LessonSession` delegate.**

`PressStream.onFrame` is `LessonSession.onFrame` lines 87–95 plus the branch conditions, and nothing else:

```kotlin
fun onFrame(frame: AudioFrame): FrameEvent {
    val pitch = detector.detect(frame)
    lastPitch = pitch
    val phase = debouncer.onFrame(if (pitch.ambiguous) null else pitch.midiNote)
    this.phase = phase
    if (phase == NotePhase.ATTACK) reachedAttack = true
    if (phase == NotePhase.STABLE) reachedStable = true
    var poke = false
    if (phase == NotePhase.IDLE) {
        judgedMidi = null
        poke = reachedAttack && !reachedStable
        reachedAttack = false
        reachedStable = false
    }
    val holding = judgedMidi != null && phase != NotePhase.IDLE
    return when {
        pitch.ambiguous && !holding -> FrameEvent.Unclear
        poke -> FrameEvent.TooShort
        phase == NotePhase.STABLE && pitch.midiNote != judgedMidi -> {
            judgedMidi = pitch.midiNote
            FrameEvent.Judged(Press(pitch.midiNote!!, frame.capturedAtMs, pitch.confidence))
        }
        holding -> FrameEvent.Holding
        pitch.signalStrength < YinHpsPitchDetector.MIN_RMS -> FrameEvent.Quiet
        else -> FrameEvent.Listening
    }
}
```

`heldMidi` exposes `judgedMidi`. `reset()` clears everything and calls `debouncer.reset()`.

`LessonSession` keeps `silence`, `arrival`, `recognizer`, `validator`, the prompt list, the copy and `lastHeardMidi`, and its `onFrame` becomes a `when` over the returned `FrameEvent`. It keeps its own `detector`/`debouncer` constructor parameters by handing them to the `PressStream` it builds, so `lessonSessionFor` still threads the calibrated A4 through.

- [ ] **Step 4: Run the whole suite, not just the new test.**

```bash
./gradlew :core:pitch:test :core:notes:test :core:calibration:test \
          :core:learning:test :core:curriculum:test :core:diagnostics:test
```

Expected: every pre-existing test green **without edits**. `acLearn03`, `acLearn03b`, `acLearn04`, `acLearn05`, `acLearn13`, `acLearn14` and `acLearn17` are the ones that matter most here.

- [ ] **Step 5: Commit** `git commit -m "Extract the press detector so the game shares it"`

---

### Task 1: Make the jumble reusable and add level 8

**Files:**
- Modify: `android/core/curriculum/.../Prompts.kt` — `private fun jumble` becomes `fun jumbleKeys`
- Modify: `android/core/curriculum/.../Levels.kt` — append level 8, add `LevelKind.FALLING`
- Modify: `android/core/curriculum/src/test/.../LevelsTest.kt` — `acLvl01`

**Interfaces:**
- Produces: `jumbleKeys(keys, count, seed, precededBy)`, `LevelKind.FALLING`, level 8

The tile schedule needs exactly the properties the prompt jumble already has — balanced across keys, and never the same key three times running. Reuse it rather than writing a second shuffler with its own bugs.

- [ ] **Step 1: Update the failing test**

```kotlin
    @Test
    fun acLvl01_theLadderStartsOnOneKeyAndEndsOnTheGame() {
        val levels = curriculum()
        assertEquals((1..levels.size).toList(), levels.map { it.id })
        assertEquals(listOf(60), levels.first().keys)
        assertEquals(LevelKind.SINGLE, levels.first().kind)
        assertEquals(LevelKind.SONG, levels[6].kind)
        assertEquals(LevelKind.FALLING, levels[7].kind)
        assertEquals(FIVE_KEYS, levels[7].keys)
        assertEquals(8, levels.size)
    }
```

`acLvl05` and `acLvl07` also assert `7` as the ceiling — update both to `8`. `acPro07` must cover the falling level too: `assertTrue(promptsFor(levelById(8), 1L).isEmpty())`.

- [ ] **Step 2–4:** Make the edits, run `./gradlew :core:curriculum:test`.
- [ ] **Step 5: Commit** `git commit -m "Open level eight and share the jumble with the game"`

---

### Task 2: The falling-notes clock

**Files:**
- Create: `android/core/curriculum/src/main/kotlin/com/vijaychhetry/kidspiano/core/curriculum/FallingGame.kt`
- Test: `android/core/curriculum/src/test/kotlin/com/vijaychhetry/kidspiano/core/curriculum/FallingGameTest.kt`

**Interfaces:**
- Consumes: `FIVE_KEYS`, `jumbleKeys`
- Produces: `FallingTile`, `TileState`, `PressOutcome`, `FallingGameSession`, `defaultTileSchedule`, the five timing constants

No audio and no Compose here. It is a clock plus an append-only list, so every timing rule is testable to the millisecond.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vijaychhetry.kidspiano.core.curriculum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FallingGameTest {
    private fun session(vararg tiles: FallingTile) = FallingGameSession(tiles.toList())
    private fun oneTile() = FallingTile(id = 1, midi = 60, landsAtMs = 3000)

    @Test
    fun acGame01_pressingOnTheLineIsAHit() {
        val s = session(oneTile())
        assertEquals(PressOutcome.HIT, s.onPress(60, atMs = 3000))
        assertEquals(1, s.hitCount)
        assertEquals(TileState.HIT, s.stateOf(1))
        assertTrue(s.complete)
    }

    @Test
    fun acGame02_theWindowIsFourFiftyEarlyAndNineHundredLate() {
        // Late grace is double the early grace, because detection is never early.
        assertEquals(PressOutcome.HIT, session(oneTile()).onPress(60, 3900))
        assertEquals(PressOutcome.IGNORED, session(oneTile()).onPress(60, 3901))
        assertEquals(PressOutcome.HIT, session(oneTile()).onPress(60, 2550))
        assertEquals(PressOutcome.IGNORED, session(oneTile()).onPress(60, 2549))
    }

    @Test
    fun acGame03_pressingFarTooEarlyIsIgnoredNotPunished() {
        val s = session(oneTile())
        assertEquals(PressOutcome.IGNORED, s.onPress(60, atMs = 1000))
        assertEquals(0, s.wrongCount)
        assertEquals(0, s.hitCount)
        assertEquals(TileState.FALLING, s.stateOf(1))
    }

    @Test
    fun acGame04_aWrongKeyInsideTheWindowIsNamedButDoesNotBurnTheTile() {
        val s = session(oneTile())
        assertEquals(PressOutcome.WRONG_KEY, s.onPress(64, atMs = 3000))
        assertEquals(1, s.wrongCount)
        assertEquals(TileState.FALLING, s.stateOf(1))
        assertEquals(PressOutcome.HIT, s.onPress(60, atMs = 3200))
        assertEquals(1, s.hitCount)
    }

    @Test
    fun acGame05_unclearAudioNeverHitsNeverMissesAndNeverBurnsTheTile() {
        val s = session(oneTile())
        assertEquals(PressOutcome.IGNORED, s.onPress(null, atMs = 3000))
        assertEquals(0, s.wrongCount)
        assertEquals(0, s.hitCount)
        // The tile must still be there to catch.
        assertEquals(PressOutcome.HIT, s.onPress(60, atMs = 3100))
    }

    @Test
    fun acGame06_aMissedTileComesBackOnceAtTheEndWithAFreshId() {
        val s = FallingGameSession(listOf(oneTile(), FallingTile(2, 64, 5500)))
        val expired = s.onTick(3901)
        assertEquals(listOf(1), expired.map { it.id })
        assertEquals(TileState.EXPIRED, s.stateOf(1))
        assertFalse(s.complete, "a missed tile is given a second chance, not lost")

        val chance = s.schedule.single { it.secondChanceFor == 1 }
        assertEquals(60, chance.midi)
        assertTrue(chance.id !in listOf(1, 2), "ids are never reused")
        assertEquals(5500 + SPAWN_GAP_MS, chance.landsAtMs, "appended after the last tile")

        // Second chances do not breed.
        s.onTick(chance.landsAtMs + LATE_MS + 1)
        assertEquals(1, s.schedule.count { it.secondChanceFor == 1 })
        assertEquals(1, s.schedule.count { it.secondChanceFor == 2 })
    }

    @Test
    fun acGame07_outstandingTilesNeverHaveOverlappingWindows() {
        // Miss the first four, so four second chances are appended in a row.
        val s = FallingGameSession(defaultTileSchedule(seed = 3L))
        var now = 0L
        repeat(400) {
            now += 100
            s.onTick(now)
            val windows = s.schedule
                .filter { s.stateOf(it.id) != TileState.HIT }
                .map { it.landsAtMs - EARLY_MS to it.landsAtMs + LATE_MS }
                .sortedBy { it.first }
            windows.zipWithNext { a, b ->
                assertTrue(b.first > a.second, "windows $a and $b overlap in ${s.schedule}")
            }
        }
        assertTrue(s.complete)
    }

    @Test
    fun acGame08_aSilentRunEndsCalmlyAndScoresNothingWithoutPunishment() {
        val s = FallingGameSession(defaultTileSchedule(seed = 1L))
        var now = 0L
        repeat(1000) {
            now += 200
            s.onTick(now)
        }
        assertTrue(s.complete)
        assertEquals(0, s.hitCount)
        assertEquals(0, s.wrongCount, "silence is never a wrong press")
        assertEquals(TILE_COUNT * 2, s.schedule.size, "every tile got one second chance")
        assertEquals(0, s.starsForRun(), "doing nothing is not worth three stars")
    }

    @Test
    fun acGame09_starsComeFromCatchesNotFromWrongNotes() {
        assertEquals(3, starsForCatches(12, TILE_COUNT))
        assertEquals(2, starsForCatches(11, TILE_COUNT))
        assertEquals(2, starsForCatches(8, TILE_COUNT))
        assertEquals(1, starsForCatches(7, TILE_COUNT))
        assertEquals(1, starsForCatches(1, TILE_COUNT))
        assertEquals(0, starsForCatches(0, TILE_COUNT))
    }

    @Test
    fun acGame10_theDefaultRunIsTwelveSlowTilesOnTaughtKeysOnly() {
        val tiles = defaultTileSchedule(seed = 5L)
        assertEquals(TILE_COUNT, tiles.size)
        assertEquals(TILE_COUNT, tiles.map { it.id }.toSet().size, "ids must be unique")
        assertTrue(tiles.all { it.midi in FIVE_KEYS })
        assertTrue(tiles.map { it.midi }.toSet().size >= 4, "a run of one key is not a game")
        assertEquals(FALL_MS, tiles.first().landsAtMs, "the first tile is readable from the top")
        assertTrue(tiles.zipWithNext { a, b -> b.landsAtMs - a.landsAtMs }.all { it == SPAWN_GAP_MS })
        tiles.map { it.midi }.windowed(3).forEach {
            assertTrue(it.toSet().size > 1, "three of the same key in a row: $tiles")
        }
    }

    @Test
    fun acGame11_tileProgressRunsPastTheLineAndTheTileStaysDrawn() {
        val tile = oneTile()
        val s = session(tile)
        assertEquals(0f, s.tileProgress(tile, nowMs = 0), 0.01f)
        assertEquals(0.5f, s.tileProgress(tile, nowMs = 1500), 0.01f)
        assertEquals(1f, s.tileProgress(tile, nowMs = 3000), 0.01f)
        // A third of the window lives past the line; that is when a struggling
        // child finally presses, so the tile must still be on screen.
        assertTrue(s.tileProgress(tile, nowMs = 3600) > 1f)
        assertTrue(s.activeTiles(nowMs = 3600).contains(tile))
        assertTrue(s.activeTiles(nowMs = 3902).isEmpty())
    }

    @Test
    fun acGame12_lanesFollowPitchOrder() {
        val s = session(oneTile())
        assertEquals(0, s.laneOf(FallingTile(9, 60, 0)))
        assertEquals(4, s.laneOf(FallingTile(9, 67, 0)))
        assertEquals(FIVE_KEYS.size, FIVE_KEYS.map { s.laneOf(FallingTile(9, it, 0)) }.toSet().size)
    }
}
```

`acGame07` is the collision test and it is the one that would have caught the original "requeue at `nowMs + gapMs`" design: with a 2500 ms gap and a 1350 ms window, a nearby requeue overlaps a live tile by 680 ms, and nothing then says which tile a press resolves against.

`acGame08` replaces a "tick 200 times and assert nothing threw" test that proved nothing. `starsForCatches` is exposed as a top-level function precisely so `acGame09` can pin the rule without driving a whole run.

- [ ] **Step 2: Run — FAIL.** `./gradlew :core:curriculum:test --tests '*FallingGameTest'`

- [ ] **Step 3: Implement.**

- A tile's window is `[landsAtMs - earlyMs, landsAtMs + lateMs]`, inclusive both ends.
- `onPress(null, …)` returns `IGNORED` before any other branch.
- `onPress` resolves against the **open window whose `landsAtMs` is nearest `atMs`** among tiles still `FALLING`. With `acGame07` holding, at most one window is ever open, so this is a tie-break that should never fire — keep it anyway and keep the test.
- A press with no open window returns `IGNORED` and does not touch `wrongCount`.
- `onTick(nowMs)` expires every `FALLING` tile whose window has closed, appends one second chance per expired tile that does not already have one, and returns the newly expired tiles (not bare ids, so the UI can animate them). Second-chance `landsAtMs` is `maxOf(schedule.maxOf { it.landsAtMs }, nowMs) + gapMs`, applied one tile at a time so a batch of expiries spaces out rather than piling onto one millisecond — which is what happens the first time the app is backgrounded mid-run.
- `complete` is true when no tile is `WAITING` or `FALLING`.
- `starsForRun()` is `starsForCatches(hitCount, schedule.count { it.secondChanceFor == null })`.

- [ ] **Step 4: PASS** `./gradlew :core:curriculum:test`
- [ ] **Step 5: Commit** `git commit -m "Add a forgiving falling-notes clock with no fail state"`

---

### Task 3: The game screen

**Files:**
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/game/FallingGameScreen.kt`, `FallingGameViewModel.kt`

The ViewModel owns a `PressStream` and feeds `FrameEvent.Judged` into `session.onPress(press.midi, press.atMs)`. `Unclear`, `TooShort`, `Holding` and `Quiet` are shown as copy and **never** forwarded to the game — that is the whole reason Task 0 exists.

Two clocks, deliberately:

- **Rendering** reads `withFrameMillis` and positions tiles from `tileProgress`. A coarse ticker would make a 3-second fall visibly steppy.
- **Expiry and second chances** run off the existing coarse ticker (`TICK_MS = 250L` in all three current ViewModels — it is 250, not 60). Nothing about expiry needs frame precision.

Tiles are `Box`es keyed on `tile.id`, x from `laneOf(tile)`, y from `tileProgress(tile, now)` against the hit line, which sits directly on the keyboard. Past the line they keep falling and fade out. A hit bursts into stars. `WRONG_KEY` names the key in the feedback line and flashes it on the keyboard via `PianoKeyboardView(flashMidi = …)`. `IGNORED` draws nothing at all.

The header counts catches only. No health bar, no decreasing multiplier, no "game over" string anywhere in the file.

- [ ] **Step 1:** `assembleDebug` baseline.
- [ ] **Step 2–3:** Build the screen.
- [ ] **Step 4:** `assembleDebug`.
- [ ] **Step 5:** Commit `git commit -m "Add the falling-notes game screen"`

---

### Task 4: Wiring and 0.7.0

**Files:**
- Modify: `MainActivity.kt` (`Dest.GAME`), `android/app/build.gradle.kts` (0.7.0 / 7), `docs/android/ACCEPTANCE_CRITERIA.md`, `releases/README.md`
- Copy: APK → `releases/KidsPiano-debug-0.7.0.apk`

New AC rows, one per new test:

| ID | Criterion | Test |
| --- | --- | --- |
| AC-PRESS-01 | One physical press is judged once | `PressStreamTest.acPress01_onePhysicalPressIsJudgedExactlyOnce` |
| AC-PRESS-02 | A held note is not rejudged until released | `acPress02_aHeldNoteIsNotJudgedAgainUntilItIsReleased` |
| AC-PRESS-03 | Two notes at once are unclear, never a press | `acPress03_twoNotesAtOnceAreUnclearAndNeverAPress` |
| AC-PRESS-04 | A too-short poke reports TooShort | `acPress04_aPokeTooShortToNameReportsTooShortAndNoPress` |
| AC-PRESS-05 | Silence is quiet, not unclear | `acPress05_silenceIsQuietAndNotUnclear` |
| AC-PRESS-06 | The press carries the frame timestamp | `acPress06_thePressCarriesTheFrameTimestamp` |
| AC-GAME-01 | Pressing on the line is a hit | `FallingGameTest.acGame01_pressingOnTheLineIsAHit` |
| AC-GAME-02 | Window is 450 early / 900 late, both pinned | `acGame02_theWindowIsFourFiftyEarlyAndNineHundredLate` |
| AC-GAME-03 | Far-too-early is ignored, not punished | `acGame03_pressingFarTooEarlyIsIgnoredNotPunished` |
| AC-GAME-04 | A wrong key does not burn the tile | `acGame04_aWrongKeyInsideTheWindowIsNamedButDoesNotBurnTheTile` |
| AC-GAME-05 | Unclear audio never hits, misses or burns | `acGame05_unclearAudioNeverHitsNeverMissesAndNeverBurnsTheTile` |
| AC-GAME-06 | A missed tile returns once, at the end, with a fresh id | `acGame06_aMissedTileComesBackOnceAtTheEndWithAFreshId` |
| AC-GAME-07 | Outstanding tiles never have overlapping windows | `acGame07_outstandingTilesNeverHaveOverlappingWindows` |
| AC-GAME-08 | A silent run ends calmly and scores nothing | `acGame08_aSilentRunEndsCalmlyAndScoresNothingWithoutPunishment` |
| AC-GAME-09 | Stars come from catches | `acGame09_starsComeFromCatchesNotFromWrongNotes` |
| AC-GAME-10 | Twelve slow tiles, unique ids, taught keys, no triples | `acGame10_theDefaultRunIsTwelveSlowTilesOnTaughtKeysOnly` |
| AC-GAME-11 | Tiles stay drawn past the line | `acGame11_tileProgressRunsPastTheLineAndTheTileStaysDrawn` |
| AC-GAME-12 | Lanes follow pitch order | `acGame12_lanesFollowPitchOrder` |

**Automation:**

```bash
cd android
./gradlew :core:notes:test :core:pitch:test :core:calibration:test \
          :core:learning:test :core:diagnostics:test :core:curriculum:test
./gradlew :app:assembleDebug
```

On-device checklist:

1. A note played half a second late still counts.
2. A note played half a second early still counts; a second early does not, and says nothing rude.
3. Stay silent through a whole run — it ends calmly, with no "you lose" and no stars.
4. Miss one tile deliberately — it comes back at the end, once.
5. Watch a tile cross the line — it keeps falling and fades instead of vanishing.
6. Play a wrong key on the line — it is named, and the tile is still catchable.

**Before setting the window from the constants in this plan:** export `session.jsonl` from a real run on the real PSR-F52, read the onset-to-judgement distribution, and adjust `EARLY_MS` / `LATE_MS` to the measured 5th and 95th percentiles. Then update `acGame02` to the measured numbers. The estimates here are a starting point, not a result.

- [ ] **Step 1–3:** AC rows, gradle, APK, commit, push, update the PR.

## Self-review

**Spec coverage:** falling tiles the child plays as they land → Tasks 2 and 3. Feedback on every press during the game → `PressStream` from Task 0 plus the `WRONG_KEY` / `IGNORED` handling in Task 3. No fail state → `acGame08`, and no health bar or game-over string in the screen. Songs at the end of a game session → already built in 0.6.0's summary screen; the game routes into it.

**Placeholders:** none, except the two timing constants that are explicitly marked as estimates to be replaced by measurement before release.

**Type consistency:** `FallingTile(id, midi, landsAtMs, secondChanceFor)`, `PressOutcome`, `TileState`, `FrameEvent`, `Press(midi, atMs, confidence)`, `starsForCatches(hits, tiles)` — one spelling each, and the `schedule` property is declared append-only in the Interfaces block to match what Task 2 actually builds.

**The risk that is being managed rather than removed:** this is the only feature in the app whose correctness depends on end-to-end acoustic latency, which `ACCEPTANCE_CRITERIA.md` already lists as unclaimed by any test. The management is: ship 0.6.0 first, measure from its exported log, then set the window. Everything else here is pure arithmetic over a clock and is fully tested.
