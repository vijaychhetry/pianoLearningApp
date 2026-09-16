# Acceptance criteria — audio, calibration, recognition

Canonical product rules: `docs/KIDS_PIANO_MASTER_SPEC.md` §§5–11, §41, §44.

These IDs are **Tier 1 JVM unit tests**. Each test asserts numbers or exact statuses that would fail if the implementation rubber-ducked (`assertTrue(true)`, “any MIDI”, quality-enum-only).

Run:

```bash
cd android
./gradlew :core:notes:test :core:pitch:test :core:calibration:test
```

## Pitch detection (spec §5, §6, §16)

| ID | Criterion | Test |
| --- | --- | --- |
| AC-PITCH-01 | C4–G4 harmonic piano tones lock the **named MIDI** and frequency within **2 Hz** of concert pitch, confidence **> 0.7** | `YinHpsPitchDetectorTest.acPitch01_locksCMajorWhiteKeysWithinTwoHz` |
| AC-PITCH-02 | Silence and below-RMS-gate audio → no MIDI, confidence **0**, not a guessed note | `acPitch02_silenceIsNoPitchAndBelowRmsGate`, `acPitch02b_belowRmsGateIsNoPitchEvenIfSineIsPresent` |
| AC-PITCH-03 | C4 must **not** report A4 / 440 Hz | `acPitch03_c4PianoToneIsNotA4` |
| AC-PITCH-04 | Two simultaneous fundamentals (C4+E4) → `ambiguous = true` (do not guess) | `acPitch04_twoSimultaneousFundamentalsAreAmbiguous` |
| AC-PITCH-05 | Harmonic-rich **single** C4 is **not** ambiguous | `acPitch05_harmonicRichSingleC4IsNotAmbiguous` |
| AC-PITCH-05b | A **miked piano** note (stretched partials, hammer noise, 50 Hz hum) is not read as two notes | `acPitch05b_realPianoPartialsAndRoomNoiseAreNotAmbiguous` |
| AC-PITCH-06 | ~452 Hz sine must stay A4 (MIDI 69), **never** HPS-fold to MIDI 50 | `acPitch06_sharpA4SineDoesNotFoldToMidi50` |
| AC-PITCH-07 | Keys **outside** the five-note MVP (A2–G5) still report a note, so exploring the keyboard is never a dead screen | `acPitch07_keysOutsideTheFiveNoteMvpStillReadOut` |

## Note math (spec §7)

| ID | Criterion | Test |
| --- | --- | --- |
| AC-NOTE-01 | A4 = 440 Hz = MIDI 69; C4/C5 concert pitch | `NotesTest.acNote01_a4Is440HzAndMidi69`, `acNote01b_c4AndC5MatchConcertPitch` |
| AC-NOTE-02 | Geometric midpoint C4/C♯4 crosses MIDI 60 → 61 | `acNote02_boundaryBetweenC4AndCSharp4` |

## Validation / recognition policy (spec §5, §44)

| ID | Criterion | Test |
| --- | --- | --- |
| AC-VAL-01 | Expected note + high confidence → `CORRECT` | `NoteValidatorTest.acVal01_highConfidenceExpectedIsCorrect` |
| AC-VAL-02 | Other letter + high confidence → `INCORRECT` | `acVal02_highConfidenceOtherLetterIsIncorrect` |
| AC-VAL-03 | Same pitch class, other octave → `CORRECT_OCTAVE_MISMATCH`, not hard wrong | `acVal03_octaveMismatchIsSoftCorrectNotIncorrect` |
| AC-VAL-04 | Low/medium confidence is **never** `INCORRECT` or `CORRECT` | `acVal04_*` |
| AC-VAL-05 | Pitch between C and C♯ (269 Hz, >40¢ from named) → `AMBIGUOUS`, never scored | `acVal05_pitchBetweenCAndCSharpIsAmbiguousNeverScored` |
| AC-VAL-06 | Null detection → `NO_SIGNAL` | `acVal06_missingSignalIsNoSignal` |
| AC-REC-01 | Recognizer maps a stable pitch to the named note | `NoteRecognizerTest.acRec01_stablePitchBecomesNamedNote` |
| AC-REC-02 | Ambiguous pitch is **not** turned into a note | `acRec02_ambiguousPitchIsNotGuessed` |

## Debounce (spec §41)

| ID | Criterion | Test |
| --- | --- | --- |
| AC-DEB-01 | A note does not lock until N matching frames | `NoteDebouncerTest.acDeb01_doesNotLockUntilStableFrames` |
| AC-DEB-02 | Switching MIDI before lock resets; neither note locks early | `acDeb02_competingCandidateResetsAndDoesNotLockEitherNote` |
| AC-DEB-03 | One held pitch = **one** STABLE press; release + new pitch = second press | `acDeb03_oneHeldNoteIsASinglePress`, `acDeb03b_twoSeparatedNotesAreTwoPresses` |

## Audio Lab log and microphone health (spec §15, §39)

The log must never sit empty while the mic runs — a silent room is itself a reading. `AudioRecord.STATE_INITIALIZED` does not prove a source works; some phones accept `UNPROCESSED` and return digital silence.

| ID | Criterion | Test |
| --- | --- | --- |
| AC-LOG-01 | The very first frame is logged, even with no signal | `LabEventLogTest.acLog01_firstFrameIsAlwaysLoggedEvenWithNoSignal` |
| AC-LOG-02 | Identical frames do not spam, but a heartbeat line proves frames still arrive | `acLog02_identicalFramesDoNotSpamButHeartbeatProvesLiveness` |
| AC-LOG-03/04 | A new note, or a new status on the same note, is logged at once | `acLog03_noteChangeIsLoggedImmediately`, `acLog04_statusChangeOnSameNoteIsLogged` |
| AC-LOG-05 | `AMBIGUOUS` and `LOW_CONFIDENCE` are shown, not swallowed | `acLog05_ambiguousAndLowConfidenceAreVisibleNotSwallowed` |
| AC-MIC-01 | A source that returns only silence is reported once after the window | `SilenceWatchdogTest.acMic01_allSilentFramesReportOnceAfterWindow` |
| AC-MIC-02 | Real signal never trips the watchdog | `acMic02_realSignalNeverTripsTheWatchdog` |
| AC-MIC-03 | Reset re-arms the watchdog for the next source | `acMic03_resetArmsTheNextSource` |

## Guided calibration flow (spec §§9–11)

The child is asked for **one key at a time**: C, D, E, F, G.

| ID | Criterion | Test |
| --- | --- | --- |
| AC-SESS-01 | The session opens by asking for C4 | `CalibrationSessionTest.acSess01_startsByAskingForC4` |
| AC-SESS-02 | A wrong key is rejected, does not advance, and is not banked under its own note | `acSess02_wrongKeyIsRejectedAndDoesNotAdvance` |
| AC-SESS-03 | Low confidence or a badly off-centre pitch is `UNCLEAR`, never a sample | `acSess03_lowConfidenceIsUnclearNotAccepted`, `acSess03b_badlyOutOfTunePitchIsUnclearEvenWhenConfident` |
| AC-SESS-04 | Advances to the next key only after enough clean samples | `acSess04_advancesOnlyAfterEnoughCleanSamples` |
| AC-SESS-05 | A full C–G run completes and scores `EXCELLENT`, medians on concert pitch | `acSess05_fullRunCompletesAndScoresExcellent` |
| AC-SESS-06 | A skipped key can **never** yield `EXCELLENT` | `acSess06_skippedNoteCannotProduceAnExcellentProfile` |
| AC-SESS-08 | Wrong keys do not move the progress bar | `acSess08_progressTracksAcceptedSamplesOnly` |

## Calibration scoring (spec §§8–11)

MVP notes: **C4 D4 E4 F4 G4** (MIDI 60, 62, 64, 65, 67). Samples more than **40 cents** from the expected frequency are dropped (wrong key during capture). The stored value is the **median**, not the first sample.

| ID | Criterion | Test |
| --- | --- | --- |
| AC-CAL-01 | Too few samples → `NEEDS_IMPROVEMENT` | `CalibrationEngineTest.acCal01_tooFewSamplesCannotBeExcellentOrGood` |
| AC-CAL-02 | Missing any of C–G → cannot be `EXCELLENT` | `acCal02_missingG4CannotBeExcellent` |
| AC-CAL-03 | Median ≠ first sample; 400 Hz outlier on C4 is dropped | `acCal03_medianIgnoresFirstSampleAndDropsWrongKey` |
| AC-CAL-04 | `EXCELLENT` only with all five notes, ≥8 samples, IQR < 3 Hz, median near concert pitch | `acCal04_excellentRequiresAllFiveNotesTightSpreadAndEnoughSamples` |
| AC-CAL-05 | Wide IQR on every note → `NEEDS_IMPROVEMENT` | `acCal05_wideSpreadOnEveryNoteIsNeedsImprovement` |

## Pipeline (spec §5 “never convert uncertainty into a wrong answer”)

| ID | Criterion | Test |
| --- | --- | --- |
| AC-PIPE-01 | C4 tone vs expected C4 → `CORRECT`; vs expected D4 → `INCORRECT` | `RecognitionPipelineTest.acPipe01_*` |
| AC-PIPE-02 | C4+E4 mix → `AMBIGUOUS`, never `INCORRECT`/`CORRECT` | `acPipe02_twoNoteMixIsAmbiguousNeverIncorrect` |
| AC-PIPE-03 | Silence vs expected C4 → `NO_SIGNAL`, never `INCORRECT` | `acPipe03_silenceExpectedC4IsNoSignalNeverIncorrect` |

## Tier 2 — not claimed by these tests

Real `AudioRecord` behaviour per device, true sample-rate, PSR-F52 latency, and live onset/release (spec §41) need a physical phone next to a real piano. An emulator can prove the app launches, asks for the right key, and keeps logging frames — it cannot prove recognition accuracy, because its microphone is not a piano. Do **not** add empty `androidTest` methods that always pass.
