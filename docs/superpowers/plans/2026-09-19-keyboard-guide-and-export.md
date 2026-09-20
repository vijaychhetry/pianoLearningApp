# Keyboard Guide and Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show which physical PSR-F52 key to press, let a grown-up pick the Calibrate key, and export the calibration profile plus a session log off the phone.

**Architecture:** Keep `:core:notes`, `:core:calibration`, `:core:learning`, `:core:pitch` as JVM modules. Add keyboard geometry and session-log types there so they are testable without Android. Compose draws the keyboard from that model. `FileExporter` in `:app` is the only new Android I/O type (share sheet + `filesDir`). Practice still requires a `GOOD`/`EXCELLENT` C4–G4 profile. Do not add a bottom tab bar; Files is a third Grown-ups top tab.

**Tech Stack:** Kotlin, JUnit 5, Jetpack Compose Material 3, `FileProvider` (already on `androidx.core.ktx`), `Intent.ACTION_SEND`. No Room, no network, no raw audio files, no third-party JSON library.

## Global Constraints

- Package id stays `com.vijaychhetry.kidspiano`.
- Version becomes `0.5.0` / `versionCode 5`.
- PSR-F52 range is C2–C7 (MIDI 36–96).
- Default lesson set remains C4 D4 E4 F4 G4 (MIDI 60, 62, 64, 65, 67).
- Extra lesson set this slice: C3 D3 E3 F3 G3 (MIDI 48, 50, 52, 53, 55).
- Unclear / two notes / too quiet is never `INCORRECT`.
- Held correct note is not re-scored as the next key (`judgedMidi` until `IDLE`).
- Exports contain JSON only — never PCM or WAV.
- Calibration decimals stay `Locale.US` (dot decimal).
- Debug APK goes to `releases/KidsPiano-debug-0.5.0.apk`.
- Tests first: write the failing test, watch it fail, then implement.
- On-screen keyboard is not tappable and has no Yamaha logo.
- Do not implement until the human approves the mockups in `docs/ui-mockups/`.

## Reviewer corrections (Opus 5, 2026-09-19)

These override any older sentence in a later task.

1. **Dropdown range is A2–F6, not C2–C7.** `YinHpsPitchDetector.MIN_FREQ_HZ = 110` / `MAX_FREQ_HZ = 1400` (A2 MIDI 45 … F6 MIDI 89). `selectableMidi()` = white keys in that window (27 keys). The 61-key *mini-map* still draws C2–C7; out-of-range keys are faded and omitted from the menu. Canonical append test uses **A4 (69)** or **B3 (59)**, never C2.
2. **No jump after a finished profile.** After `CalibrationRunner.profile != null`, the Key dropdown is disabled until Redo. Do not clear `profile` from `jumpTo`. This avoids the ViewModel auto-`stop()`, `LaunchedEffect(profileToSave)` re-save, and the result card gating.
3. **`jumpTo` resets the press.** `CalibrationRunner.jumpTo` must call `debouncer.reset()`, `session.onRelease()`, then `session.jumpTo`. A held note must not become a sample for the new key (`AC-SESS-09`). If the target already has `samplesPerNote` samples, **clear that key's bucket** so it is re-measured. Add a `CalibrationRunner` test, not only a session test.
4. **Two-layer keyboard, not 36 × 9 dp keys.** Mini-map (all six Cs) + zoom window (~C3–C5 for a C4 target). Target name is drawn *above* the zoomed key. Black key width ≈ 0.55 × white. Geometry lives in `:core:notes` as tested fractions (`whiteKeyLeftFraction`, `blackKeyLeftFraction`, `zoomWindow(highlightMidi)`).
5. **Copy uses note names**, not letters, anywhere a key is named (`Try D4.`, `Yes! Now D4.`, `Play the C4 key.`, `That was C. Please play C2.` is forbidden). Octave copy is `That was C5. Press C4.` — no color word.
6. **`LessonViewModel.useProfile(profile, notes)`.** `LessonScreen` already has a `CalibrationStore`; it passes `store.lessonMidi()`. Map `expectedMidi` in `toUi`.
7. **Session log:** parse/serialize in `:core:diagnostics` (round-trip tested). Append-only file write on `Dispatchers.IO`. Rotate at 2000 lines (`session.jsonl` → `session.1.jsonl`). Log a calibrate event only when `Offer` *changes* or a new press starts — never per-frame `UNCLEAR`.
8. **Quality still requires C4–G4** after an extra key: add a test. **C3–G3 against a C4–G4 profile** scores a synthesized C3 `CORRECT`. **`lessonSessionFor(profile, notes)`** is the failing Task 4 surface (`acLearn12` constructor-only is characterization).
9. **Task 8:** *replace* `AC-LEARN-06` (keep “does not advance”). Give every new test an AC row. Export filenames `yyyy-MM-dd-HHmm` in UTC. JSON is compact (no spaces). `selectableMidi()` reuses `isWhiteMidi` / `whiteKeys()`.

## File map

- Create: `android/core/notes/src/main/kotlin/com/vijaychhetry/kidspiano/core/notes/PianoKeyboard.kt`
- Create: `android/core/notes/src/test/kotlin/com/vijaychhetry/kidspiano/core/notes/PianoKeyboardTest.kt`
- Create: `android/core/diagnostics/build.gradle.kts`
- Create: `android/core/diagnostics/src/main/kotlin/com/vijaychhetry/kidspiano/core/diagnostics/SessionLog.kt`
- Create: `android/core/diagnostics/src/main/kotlin/com/vijaychhetry/kidspiano/core/diagnostics/CalibrationExport.kt`
- Create: `android/core/diagnostics/src/test/kotlin/com/vijaychhetry/kidspiano/core/diagnostics/SessionLogTest.kt`
- Create: `android/core/diagnostics/src/test/kotlin/com/vijaychhetry/kidspiano/core/diagnostics/CalibrationExportTest.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/ui/PianoKeyboardView.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/export/FileExporter.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/export/SessionLogStore.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/grownups/FilesScreen.kt`
- Create: `android/app/src/main/res/xml/file_paths.xml`
- Create: `diagnostics/inbox/README.md`
- Modify: `android/settings.gradle.kts` — include `:core:diagnostics` next to the other cores (not inside the SDK-gated block)
- Modify: `android/core/calibration/.../CalibrationSession.kt` — mutable `noteOrder`, `jumpTo`, `selectableMidi`
- Modify: `android/core/calibration/.../CalibrationRunner.kt` — `jumpTo` + snapshot
- Modify: `android/core/calibration/.../Calibration.kt` — persist spread as 4th CSV field
- Modify: `android/core/learning/.../LessonSession.kt` — octave copy names both keys; `lessonSessionFor(profile, notes)`
- Modify: `android/app/.../lesson/LessonScreen.kt` — keyboard, hero `noteName`
- Modify: `android/app/.../lesson/LessonViewModel.kt` — `expectedMidi`, lesson-set notes
- Modify: `android/app/.../calibration/CalibrationScreen.kt` — key dropdown + keyboard + export
- Modify: `android/app/.../MainActivity.kt` — `GrownUpsTab.FILES`
- Modify: `android/app/src/main/AndroidManifest.xml` — `FileProvider`
- Modify: `android/app/build.gradle.kts` — version 0.5.0, depend on `:core:diagnostics`
- Modify: `releases/README.md` — export how-to
- Modify: `docs/android/ACCEPTANCE_CRITERIA.md` — new AC-KEY / AC-CAL-JUMP / AC-EXPORT rows
- Modify: `.gitignore` — ignore `diagnostics/inbox/*` except README

## Interfaces

```kotlin
// PianoKeyboard.kt
const val PSR_F52_LOW_MIDI = 36  // C2
const val PSR_F52_HIGH_MIDI = 96 // C7

data class KeyboardKey(
    val midi: Int,
    val isWhite: Boolean,
    val whiteIndex: Int, // 0..35 for white keys; -1 for black
    val letter: String,  // "C", "C#", …
    val octave: Int,
    val name: String,    // "C4"
)

fun psrF52Keys(): List<KeyboardKey>
fun whiteKeys(): List<KeyboardKey> // 36 whites
const val DETECTABLE_LOW_MIDI = 45  // A2, YinHpsPitchDetector.MIN_FREQ_HZ
const val DETECTABLE_HIGH_MIDI = 89 // F6
fun selectableMidi(): List<Int> // whites in 45..89 (27 keys)
fun defaultLessonMidi(): List<Int> = listOf(60, 62, 64, 65, 67)
fun lowerClusterLessonMidi(): List<Int> = listOf(48, 50, 52, 53, 55)
fun zoomWindow(highlightMidi: Int): List<KeyboardKey> // ~15 whites around the target
fun whiteKeyLeftFraction(whiteIndex: Int, whiteCount: Int): Float
fun blackKeyLeftFraction(precedingWhiteIndex: Int, whiteCount: Int): Float
fun lessonSets(): List<Pair<String, List<Int>>> = listOf(
    "C4–G4 (first)" to defaultLessonMidi(),
    "C3–G3 (lower)" to lowerClusterLessonMidi(),
)

// CalibrationSession — notes constructor still accepted; copied into noteOrder
fun jumpTo(midi: Int): Boolean   // false for black / out of detectable range

// SessionLog — in-memory only. App persists toJsonl().
data class SessionLogLine(
    val atEpochMs: Long,
    val screen: String,
    val expectedMidi: Int?,
    val heardMidi: Int?,
    val hz: Double?,
    val status: String,
    val confidence: Double,
    val message: String,
)
class SessionLog(private val maxLines: Int = 2000) {
    val lineCount: Int
    fun append(line: SessionLogLine)
    fun toJsonl(): String
}

fun calibrationToJson(profile: CalibrationProfile): String
fun calibrationFromJson(json: String): CalibrationProfile?
```

`LessonSession` already has `notes: List<Int> = MVP_CALIBRATION_MIDI`. Do **not** add a second constructor. Change `lessonSessionFor` to:

```kotlin
fun lessonSessionFor(
    profile: CalibrationProfile,
    notes: List<Int> = defaultLessonMidi(),
): LessonSession
```

---

### Task 1: PSR-F52 keyboard model

**Files:**
- Create: `android/core/notes/src/main/kotlin/com/vijaychhetry/kidspiano/core/notes/PianoKeyboard.kt`
- Test: `android/core/notes/src/test/kotlin/com/vijaychhetry/kidspiano/core/notes/PianoKeyboardTest.kt`

**Interfaces:**
- Consumes: `midiToNoteName`, `pitchClass` from `Notes.kt`
- Produces: `psrF52Keys()`, `whiteKeys()`, `KeyboardKey`, `defaultLessonMidi()`, `lowerClusterLessonMidi()`, `lessonSets()`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vijaychhetry.kidspiano.core.notes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PianoKeyboardTest {
    @Test
    fun acKey01_psrF52HasSixtyOneKeysFromC2ToC7() {
        val keys = psrF52Keys()
        assertEquals(61, keys.size)
        assertEquals(36, keys.first().midi)
        assertEquals("C2", keys.first().name)
        assertEquals(96, keys.last().midi)
        assertEquals("C7", keys.last().name)
    }

    @Test
    fun acKey02_thereAreSixCAndFiveDWhiteKeys() {
        val white = whiteKeys()
        assertEquals(36, white.size)
        assertEquals(6, white.count { it.letter == "C" })
        assertEquals(5, white.count { it.letter == "D" })
        assertEquals(5, white.count { it.letter == "G" })
    }

    @Test
    fun acKey03_middleCIsTheThirdCWhiteKey() {
        val cKeys = whiteKeys().filter { it.letter == "C" }
        assertEquals(listOf("C2", "C3", "C4", "C5", "C6", "C7"), cKeys.map { it.name })
        assertEquals(60, cKeys[2].midi)
        assertEquals(2, cKeys[2].whiteIndex)
    }

    @Test
    fun acKey04_defaultLessonIsC4ToG4() {
        assertEquals(listOf(60, 62, 64, 65, 67), defaultLessonMidi())
        assertEquals(listOf(48, 50, 52, 53, 55), lowerClusterLessonMidi())
        assertEquals(2, lessonSets().size)
    }

    @Test
    fun acKey05_blackKeysHaveNoWhiteIndex() {
        val cSharp4 = psrF52Keys().single { it.midi == 61 }
        assertEquals(false, cSharp4.isWhite)
        assertEquals(-1, cSharp4.whiteIndex)
        assertEquals("C#", cSharp4.letter)
    }

    @Test
    fun acKey06_selectableWhitesAreOnlyWhatTheDetectorCanHear() {
        val midis = selectableMidi()
        assertEquals(27, midis.size)
        assertEquals(45, midis.first()) // A2
        assertEquals(89, midis.last())  // F6
        assertFalse(36 in midis)        // C2 is on the mini-map only
        assertFalse(96 in midis)        // C7 is on the mini-map only
        assertTrue(60 in midis)
    }

    @Test
    fun acKey07_zoomWindowAroundC4IsC3ToC5() {
        val zoom = zoomWindow(60)
        assertEquals("C3", zoom.first { it.isWhite }.name)
        assertEquals("C5", zoom.last { it.isWhite }.name)
        assertTrue(zoom.any { it.midi == 60 && it.isWhite })
        assertEquals(0f, whiteKeyLeftFraction(0, 36), 0.0001f)
        assertTrue(blackKeyLeftFraction(0, 36) > 0f)
        assertTrue(blackKeyLeftFraction(0, 36) < whiteKeyLeftFraction(1, 36))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :core:notes:test --tests com.vijaychhetry.kidspiano.core.notes.PianoKeyboardTest`

Expected: compile fail (`psrF52Keys` unresolved) or FAIL.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.vijaychhetry.kidspiano.core.notes

const val PSR_F52_LOW_MIDI = 36
const val PSR_F52_HIGH_MIDI = 96

data class KeyboardKey(
    val midi: Int,
    val isWhite: Boolean,
    val whiteIndex: Int,
    val letter: String,
    val octave: Int,
    val name: String,
)

fun isWhiteMidi(midi: Int): Boolean {
    val pc = pitchClass(midi)
    return pc == 0 || pc == 2 || pc == 4 || pc == 5 || pc == 7 || pc == 9 || pc == 11
}

fun psrF52Keys(): List<KeyboardKey> {
    var whiteIndex = 0
    return (PSR_F52_LOW_MIDI..PSR_F52_HIGH_MIDI).map { midi ->
        val name = midiToNoteName(midi)
        val white = isWhiteMidi(midi)
        val key = KeyboardKey(
            midi = midi,
            isWhite = white,
            whiteIndex = if (white) whiteIndex else -1,
            letter = name.dropLast(1),
            octave = name.last().digitToInt(),
            name = name,
        )
        if (white) whiteIndex++
        key
    }
}

fun whiteKeys(): List<KeyboardKey> = psrF52Keys().filter { it.isWhite }

fun defaultLessonMidi(): List<Int> = listOf(60, 62, 64, 65, 67)
fun lowerClusterLessonMidi(): List<Int> = listOf(48, 50, 52, 53, 55)
fun lessonSets(): List<Pair<String, List<Int>>> = listOf(
    "C4–G4 (first)" to defaultLessonMidi(),
    "C3–G3 (lower)" to lowerClusterLessonMidi(),
)

const val DETECTABLE_LOW_MIDI = 45
const val DETECTABLE_HIGH_MIDI = 89

fun selectableMidi(): List<Int> =
    whiteKeys().map { it.midi }.filter { it in DETECTABLE_LOW_MIDI..DETECTABLE_HIGH_MIDI }

fun zoomWindow(highlightMidi: Int): List<KeyboardKey> {
    val keys = psrF52Keys()
    val whites = keys.filter { it.isWhite }
    val center = whites.indexOfFirst { it.midi == highlightMidi }.coerceAtLeast(0)
    val start = (center - 7).coerceAtLeast(0)
    val end = (start + 14).coerceAtMost(whites.lastIndex)
    val from = whites[start].midi
    val to = whites[end].midi
    return keys.filter { it.midi in from..to }
}

fun whiteKeyLeftFraction(whiteIndex: Int, whiteCount: Int): Float =
    whiteIndex.toFloat() / whiteCount

fun blackKeyLeftFraction(precedingWhiteIndex: Int, whiteCount: Int): Float {
    val whiteW = 1f / whiteCount
    return (precedingWhiteIndex + 1) * whiteW - 0.275f * whiteW
}
```

- [ ] **Step 4: Run tests and make sure they pass**

Run: `./gradlew :core:notes:test`

Expected: BUILD SUCCESSFUL, including `PianoKeyboardTest`.

- [ ] **Step 5: Commit**

```bash
git add android/core/notes
git commit -m "Add PSR-F52 keyboard model with six C keys"
```

---

### Task 2: Calibration jump-to-key

**Files:**
- Modify: `android/core/calibration/src/main/kotlin/com/vijaychhetry/kidspiano/core/calibration/CalibrationSession.kt`
- Modify: `android/core/calibration/src/main/kotlin/com/vijaychhetry/kidspiano/core/calibration/CalibrationRunner.kt`
- Modify: `android/core/calibration/src/test/kotlin/com/vijaychhetry/kidspiano/core/calibration/CalibrationSessionTest.kt`
- Test: keep existing AC-SESS tests green — `notes` must remain readable

**Interfaces:**
- Consumes: constructor `notes: List<Int> = MVP_CALIBRATION_MIDI` (already exists)
- Produces: `jumpTo(midi: Int): Boolean`, `selectableMidi(): List<Int>`

`CalibrationSession` today stores `val notes: List<Int>`. Change it to:

```kotlin
class CalibrationSession(
    notes: List<Int> = MVP_CALIBRATION_MIDI,
    val samplesPerNote: Int = 4,
    ...
) {
    private val noteOrder = notes.toMutableList()
    val notes: List<Int> get() = noteOrder.toList()
    ...
}
```

Copy the constructor list. Do not mutate the caller's list. `progress` already uses `notes.size`, so an appended extra key correctly lengthens the bar. `MedianCalibrationEngine` still scores only `MVP_CALIBRATION_MIDI`.

- [ ] **Step 1: Write the failing test** (append to `CalibrationSessionTest`)

```kotlin
@Test
fun acCalJump01_dropdownCanMoveFromCToFWithoutPlayingC() {
    val session = CalibrationSession()
    assertEquals(60, session.currentMidi)
    assertTrue(session.jumpTo(65))
    assertEquals(65, session.currentMidi)
    assertEquals(0, session.acceptedCount(60), "C samples stay empty")
}

@Test
fun acCalJump02_unknownBlackKeyIsRefused() {
    val session = CalibrationSession()
    assertFalse(session.jumpTo(61)) // C#4
    assertEquals(60, session.currentMidi)
}

@Test
fun acCalJump03_newDetectableWhiteKeyIsAppended() {
    val session = CalibrationSession()
    assertTrue(session.jumpTo(69)) // A4 — inside A2–F6
    assertEquals(69, session.currentMidi)
    assertTrue(session.notes.containsAll(MVP_CALIBRATION_MIDI + 69))
    assertEquals(6, session.notes.size)
}

@Test
fun acCalJump04_c2IsRefusedBecauseTheDetectorCannotHearIt() {
    val session = CalibrationSession()
    assertFalse(session.jumpTo(36))
    assertEquals(60, session.currentMidi)
}

@Test
fun acCalJump05_jumpingToAFinishedKeyClearsItsSamples() {
    val session = CalibrationSession(samplesPerNote = 2)
    repeat(2) { session.press(60, midiToFreq(60)); session.onRelease() }
    assertEquals(62, session.currentMidi)
    assertTrue(session.jumpTo(60))
    assertEquals(0, session.acceptedCount(60))
    assertEquals(60, session.currentMidi)
}
```

Import `selectableMidi` from `:core:notes`. Do not re-derive pitch classes in the calibration module.

- [ ] **Step 2: Run test — expect FAIL** (`jumpTo` unresolved).

Run: `./gradlew :core:calibration:test --tests com.vijaychhetry.kidspiano.core.calibration.CalibrationSessionTest`

- [ ] **Step 3: Implement**

```kotlin
fun jumpTo(midi: Int): Boolean {
    if (midi !in selectableMidi()) return false
    if (acceptedCount(midi) >= samplesPerNote) accepted.remove(midi)
    if (midi !in noteOrder) noteOrder.add(midi)
    index = noteOrder.indexOf(midi)
    sampledThisPress = false
    return true
}
```

Add `fun jumpTo(midi: Int): Snapshot` on `CalibrationRunner`:

```kotlin
fun jumpTo(midi: Int): Snapshot {
    if (profile != null) return snapshot("Redo first if you want a different key.")
    debouncer.reset()
    session.onRelease()
    val ok = session.jumpTo(midi)
    val target = session.currentMidi
    return snapshot(
        if (ok) "Now play ${target?.let { midiToNoteName(it) }}."
        else "This piano has that key; the phone cannot hear it yet.",
    )
}
```

Add `CalibrationRunnerTest.acCalJump06_heldNoteDoesNotSampleTheNewKey` (hold C4, `jumpTo(65)`, more C4 frames → F4 `acceptedCount` stays 0) and `acCalJump07_jumpAfterProfileIsIgnored`.

Expose `targetMidi` on `CalibrationUiState`.

- [ ] **Step 4: Run** `./gradlew :core:calibration:test` — PASS, including the original AC-SESS suite.

- [ ] **Step 5: Commit** `git commit -m "Let calibration jump to a chosen white key"`

---

### Task 3: Session log + calibration JSON (no Android)

**Files:**
- Create: `android/core/diagnostics/build.gradle.kts` — copy `android/core/notes/build.gradle.kts`, then add `implementation(project(":core:calibration"))` and `implementation(project(":core:notes"))`
- Create: `android/core/diagnostics/src/main/kotlin/com/vijaychhetry/kidspiano/core/diagnostics/SessionLog.kt`
- Create: `android/core/diagnostics/src/main/kotlin/com/vijaychhetry/kidspiano/core/diagnostics/CalibrationExport.kt`
- Test: `android/core/diagnostics/src/test/kotlin/com/vijaychhetry/kidspiano/core/diagnostics/SessionLogTest.kt`
- Test: `android/core/diagnostics/src/test/kotlin/com/vijaychhetry/kidspiano/core/diagnostics/CalibrationExportTest.kt`
- Modify: `android/settings.gradle.kts` — add `include(":core:diagnostics")` next to `:core:learning`
- Modify: `android/core/calibration/src/main/kotlin/com/vijaychhetry/kidspiano/core/calibration/Calibration.kt` — `formatSavedNotes` / `parseSavedProfile` 4th field
- Test: existing `CalibrationTuningTest.acTune03` must still pass with the old 3-field CSV

**Interfaces:**
- Consumes: `CalibrationProfile`, `NoteCalibration`, `midiToNoteName`, `midiToFreq`
- Produces: `SessionLog`, `SessionLogLine`, `calibrationToJson`, `calibrationFromJson`

- [ ] **Step 1: Failing tests**

```kotlin
@Test
fun acLog01_appendWritesOneJsonObjectPerLine() {
    val log = SessionLog(maxLines = 10)
    log.append(SessionLogLine(1L, "practice", 60, 60, 261.9, "CORRECT", 0.95, "Yes"))
    val lines = log.toJsonl().trim().lines()
    assertEquals(1, lines.size)
    assertTrue(lines[0].contains("\"expectedMidi\":60"))
    assertTrue(lines[0].contains("\"screen\":\"practice\""))
    assertFalse(lines[0].contains("pcm"), lines[0])
    assertFalse(lines[0].contains("wav"), lines[0])
    assertFalse(lines[0].contains("base64"), lines[0])
}

@Test
fun acLog02_capsAtMaxLinesByDroppingOldest() {
    val log = SessionLog(maxLines = 3)
    repeat(5) { i ->
        log.append(SessionLogLine(i.toLong(), "lab", null, 60, 261.6, "HIGH_CONFIDENCE", 0.9, ""))
    }
    assertEquals(3, log.lineCount)
    val first = log.toJsonl().lineSequence().first()
    assertTrue(first.contains("\"atEpochMs\":2"), first)
}

@Test
fun acLog03_nullMidiAndHzBecomeJsonNull() {
    val log = SessionLog()
    log.append(SessionLogLine(1L, "lab", null, null, null, "NO_SIGNAL", 0.0, "too quiet"))
    assertTrue(log.toJsonl().contains("\"expectedMidi\":null"))
    assertTrue(log.toJsonl().contains("\"hz\":null"))
}

@Test
fun acExport01_roundTripPreservesMediansAndQuality() {
    val profile = MedianCalibrationEngine().buildProfile(
        MVP_CALIBRATION_MIDI.associateWith { midi -> List(4) { midiToFreq(midi) } },
        44100,
        "VOICE_RECOGNITION",
    )
    val json = calibrationToJson(profile)
    assertTrue(json.contains("\"schema\":\"kidspiano.calibration.v1\""))
    assertTrue(json.contains("\"quality\":\"EXCELLENT\""))
    val back = calibrationFromJson(json)!!
    assertEquals(profile.calibrationQuality, back.calibrationQuality)
    assertEquals(profile.notes.map { it.midiNote }, back.notes.map { it.midiNote })
    assertEquals(
        profile.notes.first().observedMedianFrequency,
        back.notes.first().observedMedianFrequency,
        0.05,
    )
    assertEquals(
        profile.notes.first().frequencySpread,
        back.notes.first().frequencySpread,
        0.05,
    )
}

@Test
fun acExport02_garbageJsonIsRefused() {
    assertEquals(null, calibrationFromJson("{not-json"))
    assertEquals(null, calibrationFromJson("{\"schema\":\"nope\"}"))
}
```

Also add to `CalibrationTuningTest`:

```kotlin
@Test
fun acTune06_savedNotesRoundTripSpreadWhenPresent() {
    val csv = "60:261.90:4:0.15"
    val profile = parseSavedProfile("EXCELLENT", csv, "VOICE_RECOGNITION", 44100, 1L)!!
    assertEquals(0.15, profile.notes.single().frequencySpread, 0.001)
    assertTrue(formatSavedNotes(profile.notes).contains("0.15"))
}
```

Keep `acTune03` on a **3-field** string so old phones still load.

JSON shape (hand-written encoder + small field parser, `Locale.US`):

```json
{
  "schema": "kidspiano.calibration.v1",
  "quality": "EXCELLENT",
  "microphoneSource": "VOICE_RECOGNITION",
  "sampleRate": 44100,
  "createdAtEpochMs": 1,
  "notes": [
    {"midi": 60, "name": "C4", "expectedHz": 261.63, "medianHz": 261.90, "spreadHz": 0.10, "samples": 4}
  ]
}
```

Session line:

```json
{"atEpochMs":1,"screen":"practice","expectedMidi":60,"heardMidi":60,"hz":261.9,"status":"CORRECT","confidence":0.95,"message":"Yes"}
```

Escape `message` backslashes and quotes. Numbers via `String.format(Locale.US, …)`.

- [ ] **Step 2: Run** `./gradlew :core:diagnostics:test` — FAIL (module missing).

- [ ] **Step 3: Implement module + encoder.** `SessionLog` holds an `ArrayDeque<SessionLogLine>`. `append` drops from the front when `lineCount == maxLines`. `toJsonl()` joins with `\n` and a trailing newline. `calibrationFromJson` requires `schema` exactly `kidspiano.calibration.v1`.

`formatSavedNotes` becomes `midi:freq:count:spread` with `%.2f` for both doubles. `parseSavedProfile` reads parts[3] when present, else spread `0.0`.

- [ ] **Step 4: PASS** `./gradlew :core:diagnostics:test :core:calibration:test`

- [ ] **Step 5: Commit** `git commit -m "Add exportable calibration JSON and session log lines"`

---

### Task 4: Lesson copy names the heard octave

**Files:**
- Modify: `android/core/learning/src/main/kotlin/com/vijaychhetry/kidspiano/core/learning/LessonSession.kt`
- Modify: `android/core/learning/src/test/kotlin/com/vijaychhetry/kidspiano/core/learning/LessonSessionTest.kt`

**Interfaces:**
- Consumes: existing `LessonSession(notes: List<Int> = MVP_CALIBRATION_MIDI, …)` — already on line 54 of `LessonSession.kt`. `midiToNoteName`.
- Produces: `childCopy` that names both keys; `lessonSessionFor(profile, notes)`

- [ ] **Step 1: Replace / extend `acLearn06` and add `acLearn12`**

```kotlin
@Test
fun acLearn06_octaveMismatchNamesBothKeys() {
    val session = LessonSession()
    session.begin(0)
    val snap = play(session, midi = 72, atFrame = 0)
    assertEquals(RecognitionStatus.CORRECT_OCTAVE_MISMATCH, snap.status)
    assertTrue(snap.feedback.contains("C5"), snap.feedback)
    assertTrue(snap.feedback.contains("C4"), snap.feedback)
    assertEquals(0, snap.completedCount)
}

@Test
fun acLearn12_lowerClusterStartsOnC3() {
    val session = LessonSession(notes = lowerClusterLessonMidi())
    session.begin(0)
    assertEquals(48, session.snapshot().expectedMidi)
    assertEquals("C3", session.snapshot().noteName)
    assertEquals("C", session.snapshot().letter)
}
```

`play()` helper already exists in `LessonSessionTest.kt`. Import `lowerClusterLessonMidi` from `:core:notes` — add `implementation(project(":core:notes"))` is already on `:core:learning`.

- [ ] **Step 2: Run — FAIL** (copy is still “Right letter, try the other C”). `acLearn12` should already pass if the constructor is used; if it fails, the `notes` parameter is not wired to `expectedMidi`.

- [ ] **Step 3: Change `childCopy`**

```kotlin
private fun childCopy(status: RecognitionStatus, expected: Int, detected: Int?): String {
    val letter = letterOf(expected)
    return when (status) {
        RecognitionStatus.INCORRECT -> "Try $letter."
        RecognitionStatus.CORRECT_OCTAVE_MISMATCH -> {
            val heard = detected?.let { midiToNoteName(it) } ?: letter
            "That was $heard. Press ${midiToNoteName(expected)}."
        }
        else -> UNCLEAR_COPY
    }
}
```

Call site in `onFrame` already has `judgedMidi` / `pitch.midiNote`. Pass `pitch.midiNote`.

```kotlin
fun lessonSessionFor(
    profile: CalibrationProfile,
    notes: List<Int> = defaultLessonMidi(),
): LessonSession {
    val a4 = concertA4Hz(profile)
    return LessonSession(
        notes = notes,
        detector = YinHpsPitchDetector(a4Hz = a4),
        validator = DefaultNoteValidator(a4Hz = a4),
    )
}
```

- [ ] **Step 4: PASS** `./gradlew :core:learning:test`

- [ ] **Step 5: Commit** `git commit -m "Name both octaves when the child hits the other C"`

---

### Task 5: Compose keyboard + Practice screen

**Files:**
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/ui/PianoKeyboardView.kt`
- Modify: `android/app/src/main/java/com/vijaychhetry/kidspiano/lesson/LessonScreen.kt`
- Modify: `android/app/src/main/java/com/vijaychhetry/kidspiano/lesson/LessonViewModel.kt` — expose `expectedMidi` on `LessonUiState` (add the field; `noteName` already exists)

**Interfaces:**
- Consumes: `psrF52Keys()`, `LessonSnapshot.expectedMidi`, `LessonUiState.noteName`
- Produces: `PianoKeyboardView(highlightMidi: Int?, modifier: Modifier = Modifier)`

Keyboard drawing rules:

- **Mini-map:** 36 equal-width whites, height ~28.dp, C2–C7 labels, faded outside A2–F6, target is a tall tick.
- **Zoom:** `zoomWindow(highlightMidi)` (~15 whites). Height ~88.dp. Black width 0.55 × white, height 0.62 × white. Target filled `#5B3CC4`. Name (`C4`) drawn **above** the key, 12.sp. Same-letter keys in the zoom get a 30% outline.
- Not a playable toy: no `clickable` / no tap-to-inject.
- No brand marks.

Lesson screen layout (top to bottom): Home, “Play this key”, hero **`state.noteName`** at ~72.sp (not `state.letter` alone), cue, feedback, progress, `PianoKeyboardView(highlightMidi = state.expectedMidi)`, Pause.

```kotlin
data class LessonUiState(
    ...
    val expectedMidi: Int? = 60,
    val letter: String = "C",
    val noteName: String = "C4",
    ...
)
```

No new JVM test for Compose. Proof for this task is `:app:assembleDebug` plus the existing lesson tests still green.

- [ ] **Step 1:** There is no Compose test harness in this repo. Do **not** add empty `androidTest`. After the view exists, run `./gradlew :core:learning:test :app:assembleDebug`.

- [ ] **Step 2:** Implement `PianoKeyboardView` as a column: mini-map `Row` (36 whites) + zoom `Box` using `whiteKeyLeftFraction` / `blackKeyLeftFraction` from Task 1.

- [ ] **Step 3:** Swap Lesson hero text from `state.letter` to `state.noteName`. Pass `highlightMidi = state.expectedMidi`.

- [ ] **Step 4:** `assembleDebug` SUCCESS.

- [ ] **Step 5:** Commit `git commit -m "Show a 61-key guide on Practice with C4 highlighted"`

---

### Task 6: Calibrate dropdown + keyboard + Grown-ups Files shell

**Files:**
- Modify: `android/app/src/main/java/com/vijaychhetry/kidspiano/calibration/CalibrationScreen.kt`
- Modify: `android/app/src/main/java/com/vijaychhetry/kidspiano/calibration/CalibrationViewModel.kt` — `jumpTo(midi)`, `selectableNotes: List<Pair<Int,String>>`
- Modify: `android/app/src/main/java/com/vijaychhetry/kidspiano/calibration/CalibrationStore.kt` — `lessonSet` get/set (`default` | `lower`)
- Modify: `android/app/src/main/java/com/vijaychhetry/kidspiano/MainActivity.kt` — `GrownUpsTab { CALIBRATE, LAB, FILES }`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/grownups/FilesScreen.kt` (UI only this task: lesson-set dropdown; export buttons disabled until Task 7)
- Modify: `android/app/src/main/java/com/vijaychhetry/kidspiano/lesson/LessonViewModel.kt` — `useProfile(profile, notes)`

**Interfaces:**
- Consumes: `CalibrationRunner.jumpTo`, `selectableMidi()`, `defaultLessonMidi()`, `lowerClusterLessonMidi()`
- Produces: working `ExposedDropdownMenuBox` labeled **Key**; Files tab; lesson-set pref

Dropdown items: `selectableMidi()` names (A2–F6 whites). Selecting one calls `model.jumpTo(midi)` before Start or while running. Disabled after a finished profile until Redo.

Under the dropdown, `PianoKeyboardView(highlightMidi = current target)`.

`CalibrationStore`:

```kotlin
fun lessonMidi(): List<Int> = when (prefs.getString("lessonSet", "default")) {
    "lower" -> lowerClusterLessonMidi()
    else -> defaultLessonMidi()
}
fun setLessonSet(id: String) { prefs.edit().putString("lessonSet", id).apply() }
```

`LessonScreen` calls `model.useProfile(store.load(), store.lessonMidi())`. `CalibrationStore.lessonMidi()` uses JVM helper `lessonMidiFor(prefs.getString("lessonSet", "default"))` so the mapping is tested.

Files tab copy (this task): heading `Files`, body `Export lands in the next change.`, lesson-set dropdown live now.

- [ ] **Step 1:** JVM coverage for the lesson set is `acLearn12` from Task 4. Add a tiny test next to `CalibrationTuningTest` only if you put `lessonMidi` parsing in a JVM function. Prefer keeping the pref key in `CalibrationStore` and not inventing a new module.

- [ ] **Step 2:** Wire dropdown + two-layer keyboard on `CalibrationScreen`. Dropdown enabled before Start and while running; disabled when `state.profile != null` until Redo.

- [ ] **Step 3:** Add the Files tab button beside Audio Lab. Do not add a bottom bar.

- [ ] **Step 4:** `./gradlew :core:learning:test :core:calibration:test :app:assembleDebug`

- [ ] **Step 5:** Commit `git commit -m "Add a working Calibrate key dropdown and Files tab shell"`

---

### Task 7: Persist and share exports

**Files:**
- Create: `android/app/src/main/res/xml/file_paths.xml`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/export/FileExporter.kt`
- Create: `android/app/src/main/java/com/vijaychhetry/kidspiano/export/SessionLogStore.kt`
- Create: `diagnostics/inbox/README.md`
- Modify: `android/app/src/main/AndroidManifest.xml` — `FileProvider` inside `<application>`
- Modify: `FilesScreen.kt` — enable the two export buttons
- Modify: `CalibrationScreen.kt` — Export calibration when `store.load() != null` or `state.profile != null`
- Modify: `LessonViewModel.kt`, `CalibrationViewModel.kt`, `AudioLabViewModel.kt` — append one log line on each scored press / accepted sample / lab note lock
- Modify: `releases/README.md`
- Modify: `.gitignore`

**file_paths.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="exports" path="exports/" />
    <files-path name="logs" path="logs/" />
</paths>
```

**Manifest** (inside `<application>`):

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.files"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

**SessionLogStore**

```kotlin
class SessionLogStore(private val context: Context) {
    private val log = SessionLog(maxLines = 2000)
    private val file = File(context.filesDir, "logs/session.jsonl")

    init {
        file.parentFile?.mkdirs()
        if (file.exists()) {
            file.readLines().forEach { line ->
                parseLine(line)?.let { log.append(it) }
            }
        }
    }

    fun append(line: SessionLogLine) {
        log.append(line)
        file.writeText(log.toJsonl())
    }

    fun exportFile(): File = file
}
```

Keep `parseLine` private and tiny (split on known keys or reuse `SessionLogLine` fields). If a historic line fails to parse, skip it — do not crash start-up.

**FileExporter.share(context, file, mime)** copies to `filesDir/exports/<name>` if needed, then:

```kotlin
val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
val intent = Intent(Intent.ACTION_SEND)
    .setType(mime) // "application/json" for both; JSONL is text-compatible
    .putExtra(Intent.EXTRA_STREAM, uri)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
context.startActivity(Intent.createChooser(intent, title))
```

Calibration export prefers `state.profile` (has spread) else `store.load()`. Write `exports/calibration-yyyy-MM-dd.json` using `yyyy-MM-dd` in `UTC` via `SimpleDateFormat("yyyy-MM-dd", Locale.US)`.

Session export copies `logs/session.jsonl` to `exports/session-yyyy-MM-dd.jsonl`.

When to append (one line, not per frame):

- Practice: when `LessonSession` first assigns `judgedMidi` (same moment as a score).
- Calibrate: when `Offer.ACCEPTED` or `WRONG_KEY` or `UNCLEAR` on a new press — not `SAME_PRESS`.
- Lab: when the lab log records a new locked note (reuse the existing “note change” event, not the heartbeat).

README section to add verbatim to `releases/README.md`:

```markdown
## Export files for analysis

On the phone: Grown-ups → 2+5 → **Files** (or Calibrate) → **Export calibration** or **Export session log**.
Choose Drive, Gmail, or Files. Then download those two files onto the computer that has this repo.

Suggested drop folder: `diagnostics/inbox/` (gitignored except `README.md`).

What is inside:

- `calibration-*.json` — quality, mic source, per-note median Hz and spread. No recordings.
- `session-*.jsonl` — one event per line (expected MIDI, heard MIDI, Hz, status).

The live profile on the phone is still
`/data/data/com.vijaychhetry.kidspiano/shared_prefs/calibration.xml`
and is not user-visible. Export is the supported way to get it out.
```

`diagnostics/inbox/README.md`:

```markdown
Drop exported `calibration-*.json` and `session-*.jsonl` here so we can analyse a phone session.
Do not commit the JSON files.
```

`.gitignore` entry:

```
diagnostics/inbox/*
!diagnostics/inbox/README.md
```

- [ ] **Step 1:** JVM tests for “no pcm/wav/base64” already exist (`acLog01`). Add `acExport03_jsonHasNoAudioKeys` asserting `calibrationToJson` does not contain those strings.

- [ ] **Step 2–4:** Implement store + exporter + buttons. `assembleDebug`.

- [ ] **Step 5: Commit** `git commit -m "Export calibration JSON and session logs through the share sheet"`

---

### Task 8: Version, APK, acceptance rows, automation

**Files:**
- Modify: `android/app/build.gradle.kts` — `versionCode = 5`, `versionName = "0.5.0"`
- Modify: `docs/android/ACCEPTANCE_CRITERIA.md` — add the rows below; bump the test-count sentence after you read `build/test-results`
- Modify: `android/README.md`, `releases/README.md` — download link for 0.5.0
- Copy: `android/app/build/outputs/apk/debug/app-debug.apk` → `releases/KidsPiano-debug-0.5.0.apk`

New AC rows (IDs must match test method names):

| ID | Criterion | Test |
| --- | --- | --- |
| AC-KEY-01 | PSR-F52 model is 61 keys C2–C7 | `PianoKeyboardTest.acKey01_psrF52HasSixtyOneKeysFromC2ToC7` |
| AC-KEY-02 | Six C whites, five D, five G | `acKey02_thereAreSixCAndFiveDWhiteKeys` |
| AC-KEY-03 | Middle C is the third C | `acKey03_middleCIsTheThirdCWhiteKey` |
| AC-KEY-04 | Default lesson C4–G4, extra set C3–G3 | `acKey04_defaultLessonIsC4ToG4` |
| AC-CAL-JUMP-01 | Dropdown can jump C→F with C still empty | `acCalJump01_dropdownCanMoveFromCToFWithoutPlayingC` |
| AC-CAL-JUMP-02 | Black key refused | `acCalJump02_unknownBlackKeyIsRefused` |
| AC-CAL-JUMP-03 | New white key appended | `acCalJump03_newWhiteKeyIsAppended` |
| AC-LOG-EXPORT-01 | One JSON object per line, no audio | `SessionLogTest.acLog01_appendWritesOneJsonObjectPerLine` |
| AC-LOG-EXPORT-02 | Cap drops oldest | `acLog02_capsAtMaxLinesByDroppingOldest` |
| AC-EXPORT-01 | Calibration JSON round-trips medians | `CalibrationExportTest.acExport01_roundTripPreservesMediansAndQuality` |
| AC-LEARN-06 | Octave mismatch names C5 and C4 and does not advance | `acLearn06_octaveMismatchNamesBothKeys` *(replaces the 0.4.0 row)* |
| AC-LEARN-12 | Lower cluster starts on C3 | `acLearn12_lowerClusterStartsOnC3` |

**Automation (must run before claiming done):**

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties   # if SDK present
./gradlew :core:notes:test :core:pitch:test :core:calibration:test :core:learning:test :core:diagnostics:test
./gradlew :app:assembleDebug
```

Expected: 0 test failures. APK path exists. `releases/README.md` contains the Export section. Count JUnit XML results and put that number in ACCEPTANCE_CRITERIA.

- [ ] **Step 1:** Add the AC rows matching the test method names above.
- [ ] **Step 2:** Run the two gradle commands. Read the test count from `build/test-results`.
- [ ] **Step 3:** Copy APK. Commit. Push. Update the PR.

```bash
git add android docs releases diagnostics
git commit -m "Ship 0.5.0 keyboard guide and exportable diagnostics"
git push -u origin HEAD
```

---

## Self-review

**Spec coverage:** Keyboard highlight → Tasks 1, 5. Which C → Tasks 1, 4, 5. Other keys without 36-key dump → Task 6 lesson set. Calibrate dropdown → Tasks 2, 6. Where the file lives + export → design doc + Task 7. Session log export → Tasks 3, 7. README how to export → Task 7–8. APK upload → Task 8. Files tab not a new bottom bar → Task 6. Spread preserved for analysis → Task 3 `acTune06` + `acExport01`.

**Placeholders:** none. Dropdown “not working” is treated as missing, not a hidden bug. `LessonSession` constructor already exists — Task 4 does not re-add it.

**Types:** `KeyboardKey`, `SessionLogLine`, `jumpTo(midi: Int): Boolean`, `calibrationToJson` / `calibrationFromJson`, `SessionLog` without a sink callback.

## Execution (only after the human approves the mockups)

Use **Subagent-Driven** development, one task per subagent, JVM tests after each task. After Task 8, a reviewer (same as requesting-code-review) must re-check the held-note lock and that exports contain no audio.
