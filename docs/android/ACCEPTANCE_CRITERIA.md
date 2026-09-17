# Acceptance criteria — audio, calibration, recognition

Canonical product rules: `docs/KIDS_PIANO_MASTER_SPEC.md` §§5–11, §41, §44.

These IDs are **Tier 1 JVM unit tests** (113 of them). Each test asserts numbers or exact statuses that would fail if the implementation rubber-ducked (`assertTrue(true)`, “any MIDI”, quality-enum-only).

Run:

```bash
cd android
./gradlew :core:notes:test :core:pitch:test :core:calibration:test
```

## How we know these tests have teeth

Claiming a test is not a rubber stamp is cheap; proving it is not is mutation testing. Every rule below was checked by deliberately breaking the implementation and confirming the suite goes red. Reviews of this suite have run 34 such mutations — low confidence returning `INCORRECT`, the median returning the first sample, the quality score always returning `EXCELLENT`, the event log only recording high-confidence frames, polyphony detection wired on or off, the silence watchdog neutered, the debouncer locking on the first frame — and each one is caught by a named test.

Two classes of gap turned up this way and are now closed:

- Tests that construct a subject with **explicit** parameters leave the **default** unpinned, and production uses the defaults. The heartbeat interval, the silence window, the frames needed to call a note stable, the samples needed per note and the engine's minimum sample count all now have a test that constructs the object the way the app does.
- A branch no test asserts is a branch nothing protects. `GOOD` was previously unreachable by assertion, so collapsing it into `NEEDS_IMPROVEMENT` broke nothing.

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
| AC-PITCH-07 | Keys **outside** the five-note MVP (A2–C6) still report a note, so exploring the keyboard is never a dead screen | `acPitch07_everyKeyFromA2ToC6ReadsOut` |
| AC-PITCH-07b | The ceiling is genuinely above the old 800 Hz limit: C6 (1046.5 Hz) reads as MIDI 84 | `acPitch07b_theTopOfTheRangeIsWiderThanTheOldEightHundredHertzCeiling` |

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
| AC-DEB-00 | The **default** config — the one the app builds — needs three agreeing frames | `NoteDebouncerTest.acDeb00_defaultConfigNeedsThreeAgreeingFramesBecauseProductionUsesTheDefault` |
| AC-DEB-01 | A note does not lock until N matching frames | `acDeb01_doesNotLockUntilStableFrames` |
| AC-DEB-02 | Switching MIDI before lock resets; neither note locks early | `acDeb02_competingCandidateResetsAndDoesNotLockEitherNote` |
| AC-DEB-03 | One held pitch = **one** STABLE press; release + new pitch = second press | `acDeb03_oneHeldNoteIsASinglePress`, `acDeb03b_twoSeparatedNotesAreTwoPresses` |
| AC-DEB-04 | A reset drops a press in flight, so a mic switch cannot leave a stale note locked | `acDeb04_resetDropsAPressInFlight` |

## Audio Lab log and microphone health (spec §15, §39)

The log must never sit empty while the mic runs — a silent room is itself a reading. `AudioRecord.STATE_INITIALIZED` does not prove a source works; some phones accept `UNPROCESSED` and return digital silence.

| ID | Criterion | Test |
| --- | --- | --- |
| AC-LOG-01 | The very first frame is logged, even with no signal | `LabEventLogTest.acLog01_firstFrameIsAlwaysLoggedEvenWithNoSignal` |
| AC-LOG-02 | Identical frames do not spam, but a heartbeat line proves frames still arrive | `acLog02_identicalFramesDoNotSpamButHeartbeatProvesLiveness` |
| AC-LOG-03/04 | A new note, or a new status on the same note, is logged at once | `acLog03_noteChangeIsLoggedImmediately`, `acLog04_statusChangeOnSameNoteIsLogged` |
| AC-LOG-05 | `AMBIGUOUS` and `LOW_CONFIDENCE` are shown, not swallowed | `acLog05_ambiguousAndLowConfidenceAreVisibleNotSwallowed` |
| AC-LOG-07 | The **default** heartbeat is the two seconds the app relies on | `acLog07_defaultHeartbeatIsTwoSecondsBecauseProductionUsesTheDefault` |
| AC-MIC-01 | A source that returns only silence is reported once after the window | `SilenceWatchdogTest.acMic01_allSilentFramesReportOnceAfterWindow` |
| AC-MIC-02 | Real signal never trips the watchdog | `acMic02_realSignalNeverTripsTheWatchdog` |
| AC-MIC-03 | Reset re-arms the watchdog for the next source | `acMic03_resetArmsTheNextSource` |
| AC-MIC-04 | The **default** window notices a silent source within 1.5 s | `acMic04_defaultWindowFiresWithinTwoSecondsBecauseProductionUsesTheDefault` |

## What the Audio Lab screen itself promises (spec §15)

The reported bug was a screen that showed nothing at all. `LabSession` owns every per-frame decision the screen makes, so these run the real detector over synthesised audio rather than trusting the UI code.

| ID | Criterion | Test |
| --- | --- | --- |
| AC-LAB-01 | A silent room still produces log lines, a frame count and an honest “too quiet” hint | `LabSessionTest.acLab01_silentRoomStillProducesLogLinesAndALevelReading` |
| AC-LAB-02 | A held C4 reaches the log as a high-confidence C4 within 2 Hz | `acLab02_heldC4IsLoggedAsAHighConfidenceNote` |
| AC-LAB-03 | The level meter moves with the room, not only when a note is recognised | `acLab03_levelMovesWithTheRoomEvenWhenNoNoteIsRecognised` |
| AC-LAB-04 | A mic that **opens but never delivers a frame** is reported by the clock, not by the audio flow | `acLab04_aSourceThatNeverDeliversAFrameIsReportedByTheClock` |
| AC-LAB-05 | Arriving frames keep the stall detector quiet | `acLab05_frameArrivalKeepsTheStallDetectorQuiet` |
| AC-LAB-06 | A digitally silent source is flagged for a switch | `acLab06_digitallySilentSourceIsFlaggedForASourceSwitch` |
| AC-LAB-07 | The log **survives** an automatic mic switch; only an explicit clear empties it | `acLab07_logSurvivesASourceSwitchSoTheUserCanSeeWhatHappened` |
| AC-LAB-08 | Two notes at once are reported as ambiguous, with a hint that says so | `acLab08_twoNotesAtOnceAreReportedAsAmbiguousNotGuessed` |
| AC-MICCYCLE-01 | Recovery walks every source **once** and then gives up; a muted phone is not cycled forever | `MicSourceCyclerTest.acMicCycle01_walksEverySourceExactlyOnceThenGivesUp` |
| AC-MICCYCLE-02 | Healthy audio re-arms the cycle | `acMicCycle02_healthyAudioReArmsTheCycle` |
| AC-MICCYCLE-03 | Asking by hand always moves and re-arms the automatic pass | `acMicCycle03_manualChoiceAlwaysMovesAndReArms` |
| AC-STALL-01 | A stall is reported once, not once per tick | `FrameArrivalMonitorTest.acStall01_reportsOnceWhenNoFrameEverArrives` |
| AC-STALL-02 | Frames re-arm the detector, so a second stall is reported too | `acStall02_arrivingFramesKeepItQuietAndReArmIt` |
| AC-STALL-03 | Never fires before capture starts | `acStall03_neverFiresBeforeStart` |
| AC-METER-01/02/03 | The bar is clamped, monotonic, and makes room noise and a struck key visibly different | `LevelScaleTest.acMeter01_*`, `acMeter02_*`, `acMeter03_*` |
| AC-METER-04 | `NaN` can never reach Compose’s `fillMaxWidth` | `acMeter04_nanCannotReachCompose` |

## Guided calibration flow (spec §§9–11)

The child is asked for **one key at a time**: C, D, E, F, G.

**One sample means one key press.** Frames arrive roughly every 46 ms, so sampling per frame would fill a note's profile from a single sustained press and measure the detector's own jitter rather than the piano's spread. The session refuses a second sample until the key is released.

| ID | Criterion | Test |
| --- | --- | --- |
| AC-SESS-01 | The session opens by asking for C4 | `CalibrationSessionTest.acSess01_startsByAskingForC4` |
| AC-SESS-02 | A wrong key is rejected, does not advance, and is not banked under its own note | `acSess02_wrongKeyIsRejectedAndDoesNotAdvance` |
| AC-SESS-03 | Low confidence or a badly off-centre pitch is `UNCLEAR`, never a sample | `acSess03_lowConfidenceIsUnclearNotAccepted`, `acSess03b_badlyOutOfTunePitchIsUnclearEvenWhenConfident` |
| AC-SESS-04 | Advances to the next key only after enough clean presses | `acSess04_advancesOnlyAfterEnoughCleanPresses` |
| AC-SESS-05 | A full C–G run completes and scores `EXCELLENT`, medians on concert pitch | `acSess05_fullRunCompletesAndScoresExcellent` |
| AC-SESS-06 | A skipped key can **never** yield `EXCELLENT` | `acSess06_skippedNoteCannotProduceAnExcellentProfile` |
| AC-SESS-08 | Wrong keys do not move the progress bar | `acSess08_progressTracksAcceptedSamplesOnly` |
| AC-SESS-09 | **One held key contributes exactly one sample**, however many frames arrive | `acSess09_oneHeldKeyContributesExactlyOneSample` |
| AC-SESS-10 | The **default** is four presses per note | `acSess10_defaultAsksForFourPressesPerNote` |
| AC-SESS-11 | A wrong key heard mid-press does not consume the press | `acSess11_wrongKeyDuringAPressDoesNotConsumeThePress` |

## The calibration screen end to end (spec §§9–11)

`CalibrationRunner` owns the frame loop, so the prompts the child sees are driven by the real detector over synthesised key presses.

| ID | Criterion | Test |
| --- | --- | --- |
| AC-RUN-01 | Opens by asking for the **C** key, by letter and by note name | `CalibrationRunnerTest.acRun01_startsByAskingForTheCKeyAndSaysSo` |
| AC-RUN-02 | Playing E when asked for C says which key was heard and does not advance | `acRun02_playingTheWrongKeySaysWhichKeyWasHeardAndDoesNotAdvance` |
| AC-RUN-03 | Two seconds of one held key is **one** sample, and the app asks the child to let go | `acRun03_oneLongHeldKeyIsOneSampleNotAWholeNote` |
| AC-RUN-04 | Four separate presses finish the note and move on to D | `acRun04_fourSeparatePressesFinishTheNoteAndMoveToD` |
| AC-RUN-05 | A whole run yields a savable five-note profile with medians within 2 Hz | `acRun05_aWholeSessionProducesASavableProfileWithFiveNotes` |
| AC-RUN-06 | Skipping every key yields a profile that **may not** become the default | `acRun06_skippingKeysProducesAProfileThatMayNotBecomeTheDefault` |
| AC-RUN-07 | Too quiet says so, instead of saying nothing | `acRun07_tooQuietSaysSoInsteadOfSayingNothing` |
| AC-RUN-08 | A mic that sends nothing is reported by the clock | `acRun08_aMicThatSendsNothingIsReportedByTheClock` |
| AC-RUN-09 | Redo clears the profile and asks for C again | `acRun09_redoClearsTheProfileAndAsksForCAgain` |

## Calibration scoring (spec §§8–11)

MVP notes: **C4 D4 E4 F4 G4** (MIDI 60, 62, 64, 65, 67). Samples more than **40 cents** from the expected frequency are dropped (wrong key during capture). The stored value is the **median**, not the first sample.

| ID | Criterion | Test |
| --- | --- | --- |
| AC-CAL-01 | Too few samples → `NEEDS_IMPROVEMENT` | `CalibrationEngineTest.acCal01_tooFewSamplesCannotBeExcellentOrGood` |
| AC-CAL-02 | Missing any of C–G → cannot be `EXCELLENT` | `acCal02_missingG4CannotBeExcellent` |
| AC-CAL-03 | Median ≠ first sample; 400 Hz outlier on C4 is dropped | `acCal03_medianIgnoresFirstSampleAndDropsWrongKey` |
| AC-CAL-04 | `EXCELLENT` only with all five notes, enough presses, IQR < 3 Hz, median near concert pitch | `acCal04_excellentRequiresAllFiveNotesTightSpreadAndEnoughSamples` |
| AC-CAL-05 | Wide IQR on every note → `NEEDS_IMPROVEMENT` | `acCal05_wideSpreadOnEveryNoteIsNeedsImprovement` |
| AC-CAL-06 | `GOOD` is reachable and is **not** `EXCELLENT` — all three bands mean something | `acCal06_goodIsReachableAndIsNotExcellent`, `acCal06b_aWobblyPlayerOnEveryKeyIsGoodNotExcellent` |
| AC-CAL-07 | Two presses per note is never good enough, however tight they look | `acCal07_twoPressesPerNoteIsNeverGoodEnough` |
| AC-CAL-08 | Only `EXCELLENT` or `GOOD` may become the stored default (spec §11) | `acCal08_onlyExcellentOrGoodMayBecomeTheDefault` |

Measured against simulated press-to-press variation, the bands discriminate: within ±5 cents scores `EXCELLENT`, ±10–20 cents scores `GOOD`, and ±25 cents or worse scores `NEEDS_IMPROVEMENT` and is refused as a default.

## Pipeline (spec §5 “never convert uncertainty into a wrong answer”)

| ID | Criterion | Test |
| --- | --- | --- |
| AC-PIPE-01 | C4 tone vs expected C4 → `CORRECT`; vs expected D4 → `INCORRECT` | `RecognitionPipelineTest.acPipe01_*` |
| AC-PIPE-02 | C4+E4 mix → `AMBIGUOUS`, never `INCORRECT`/`CORRECT` | `acPipe02_twoNoteMixIsAmbiguousNeverIncorrect` |
| AC-PIPE-03 | Silence vs expected C4 → `NO_SIGNAL`, never `INCORRECT` | `acPipe03_silenceExpectedC4IsNoSignalNeverIncorrect` |

## Tier 2 — not claimed by these tests

Real `AudioRecord` behaviour per device, true sample-rate, PSR-F52 latency, and live onset/release (spec §41) need a physical phone next to a real piano. An emulator can prove the app launches, asks for the right key, and keeps logging frames — it cannot prove recognition accuracy, because its microphone is not a piano. Do **not** add empty `androidTest` methods that always pass.

Specifically still unproven, and honestly so:

- Whether `UNPROCESSED` returns digital silence on a given phone, and whether the one-pass source walk lands on a working source there.
- End-to-end acoustic-to-display latency. The Lab reports **compute** time only, and labels it that way.
- Recognition accuracy against a miked piano in a room. All pitch evidence is synthesised; `realisticPianoTone` models stretched partials, hammer noise and mains hum, but it is not a recording.
- The Compose layer, and the glue that remains in the ViewModels: the lock, the ticker lifecycle, and teardown. The per-frame decisions were moved out into `LabSession` and `CalibrationRunner` precisely so they could be tested; what is left around them is not.
