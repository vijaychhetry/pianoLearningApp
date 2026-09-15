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
| AC-PITCH-06 | ~452 Hz sine must stay A4 (MIDI 69), **never** HPS-fold to MIDI 50 | `acPitch06_sharpA4SineDoesNotFoldToMidi50` |

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

## Calibration (spec §§8–11)

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

On-device `AudioRecord` source, real sample-rate, PSR-F52 latency, and live onset/release (spec §41) need a physical device. This environment has no Android SDK / phone. Do **not** add empty `androidTest` methods that always pass.
