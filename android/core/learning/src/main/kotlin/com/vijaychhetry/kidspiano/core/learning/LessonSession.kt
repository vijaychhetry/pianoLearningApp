package com.vijaychhetry.kidspiano.core.learning

import com.vijaychhetry.kidspiano.core.calibration.CalibrationProfile
import com.vijaychhetry.kidspiano.core.calibration.MVP_CALIBRATION_MIDI
import com.vijaychhetry.kidspiano.core.calibration.concertA4Hz
import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.PitchResult
import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.common.UnclearReason
import com.vijaychhetry.kidspiano.core.common.Verdict
import com.vijaychhetry.kidspiano.core.notes.DefaultNoteValidator
import com.vijaychhetry.kidspiano.core.notes.NoteValidator
import com.vijaychhetry.kidspiano.core.notes.displayNoteName
import com.vijaychhetry.kidspiano.core.notes.letterOf
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import com.vijaychhetry.kidspiano.core.pitch.DefaultNoteRecognizer
import com.vijaychhetry.kidspiano.core.pitch.FrameArrivalMonitor
import com.vijaychhetry.kidspiano.core.pitch.NoteDebouncer
import com.vijaychhetry.kidspiano.core.pitch.NotePhase
import com.vijaychhetry.kidspiano.core.pitch.NoteRecognizer
import com.vijaychhetry.kidspiano.core.pitch.PitchDetector
import com.vijaychhetry.kidspiano.core.pitch.PressTracker
import com.vijaychhetry.kidspiano.core.pitch.SilenceWatchdog
import com.vijaychhetry.kidspiano.core.pitch.YinHpsPitchDetector

@Suppress("unused")

enum class LessonCue { LISTEN, YES, TRY, UNCLEAR, OCTAVE, DONE }

data class LessonSnapshot(
    val expectedMidi: Int?,
    val heardMidi: Int?,
    val letter: String,
    val noteName: String,
    val completedCount: Int,
    val total: Int,
    val progress: Float,
    val feedback: String,
    val line2: String?,
    val status: RecognitionStatus,
    val cue: LessonCue,
    val level: Double,
    val complete: Boolean,
    val verdict: Verdict? = null,
)

fun lessonMayStart(profile: CalibrationProfile?): Boolean =
    profile != null && profile.usableAsDefault

fun lessonSessionFor(
    profile: CalibrationProfile,
    notes: List<Int> = MVP_CALIBRATION_MIDI,
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

/**
 * Level 1 "find this key". One physical press is one judgement.
 * PressTracker owns the onset lock; Judge owns the verdict.
 */
class LessonSession(
    private val notes: List<Int> = MVP_CALIBRATION_MIDI,
    private val completionCopy: String = "You found all five keys!",
    private val detector: PitchDetector = YinHpsPitchDetector(),
    private val recognizer: NoteRecognizer = DefaultNoteRecognizer(),
    private val validator: NoteValidator = DefaultNoteValidator(),
    private val debouncer: NoteDebouncer = NoteDebouncer(),
    private val silence: SilenceWatchdog = SilenceWatchdog(),
    private val arrival: FrameArrivalMonitor = FrameArrivalMonitor(),
    private val tracker: PressTracker = PressTracker(detector = detector),
) {
    init {
        require(notes.isNotEmpty()) { "a lesson needs at least one prompt" }
    }

    private var index = 0
    private var judgedMidi: Int? = null
    private var lastHeardMidi: Int? = null
    private var lastVerdict: Verdict? = null
    private var lastPitch: PitchResult? = null
    private var lastPhase: NotePhase = NotePhase.IDLE
    private var lastFeedback: String = Copy.playThis(notes.first())
    private var lastLine2: String? = null
    private var promptShownAtNanos: Long = 0L
    private var lastStatus: RecognitionStatus = RecognitionStatus.LISTENING

    val expectedMidi: Int? get() = notes.getOrNull(index)
    val complete: Boolean get() = index >= notes.size

    var sourceLooksDead: Boolean = false
        private set

    fun begin(atMs: Long) {
        sourceLooksDead = false
        silence.reset()
        arrival.start(atMs)
        debouncer.reset()
        tracker.reset()
        promptShownAtNanos = atMs * 1_000_000L
    }

    fun snapshot(): LessonSnapshot = snapshotFrom(lastFeedback, lastLine2)

    fun onFrame(frame: AudioFrame): LessonSnapshot {
        arrival.onFrame(frame.capturedAtMs)
        val pitch = detector.detect(frame)
        lastPitch = pitch
        if (silence.onFrame(pitch.signalStrength, frame.capturedAtMs)) sourceLooksDead = true
        val phase = debouncer.onFrame(if (pitch.ambiguous) null else pitch.midiNote)
        lastPhase = phase
        if (phase == NotePhase.IDLE) judgedMidi = null

        val target = expectedMidi
        if (target == null) {
            lastFeedback = completionCopy
            lastLine2 = null
            lastStatus = RecognitionStatus.CORRECT
            return snapshotFrom(lastFeedback, null)
        }

        val press = tracker.onFrame(frame)
        if (press != null && judgedMidi == null) {
            val heard = press.midi
            val withEvidence = if (heard != null) {
                press.copy(
                    octaveEvidence = com.vijaychhetry.kidspiano.core.pitch.octaveEvidenceFromSamples(
                        frame.samples,
                        frame.sampleRate,
                        target,
                        heard,
                    ),
                )
            } else {
                press
            }
            val verdict = judge(target, withEvidence, promptShownAtNanos)
            lastVerdict = verdict
            applyVerdict(verdict, target)
        } else if (lastVerdict == null) {
            lastFeedback = if (pitch.signalStrength < YinHpsPitchDetector.MIN_RMS) {
                Copy.playThis(target)
            } else {
                "Hold the ${midiToNoteName(target)} key so I can hear it clearly."
            }
            lastStatus = if (pitch.signalStrength < YinHpsPitchDetector.MIN_RMS) {
                RecognitionStatus.NO_SIGNAL
            } else {
                RecognitionStatus.LISTENING
            }
        }
        return snapshotFrom(lastFeedback, lastLine2)
    }

    fun onTick(nowMs: Long): LessonSnapshot? {
        if (!arrival.isStalled(nowMs)) return null
        sourceLooksDead = true
        lastFeedback = "This microphone is not sending any audio."
        lastLine2 = null
        return snapshotFrom(lastFeedback, null)
    }

    fun restart(): LessonSnapshot {
        index = 0
        judgedMidi = null
        lastHeardMidi = null
        lastVerdict = null
        lastPitch = null
        lastPhase = NotePhase.IDLE
        lastFeedback = Copy.playThis(notes.first())
        lastLine2 = null
        lastStatus = RecognitionStatus.LISTENING
        sourceLooksDead = false
        silence.reset()
        debouncer.reset()
        tracker.reset()
        promptShownAtNanos = 0L
        return snapshotFrom(lastFeedback, null)
    }

    fun onSourceSwitched() {
        sourceLooksDead = false
        silence.reset()
        debouncer.reset()
        tracker.reset()
    }

    private fun applyVerdict(verdict: Verdict, target: Int) {
        lastHeardMidi = when (verdict) {
            is Verdict.Correct -> verdict.press.midi
            is Verdict.WrongKey -> verdict.heardMidi
            is Verdict.WrongOctave -> verdict.heardMidi
            else -> lastHeardMidi
        }
        judgedMidi = lastHeardMidi
        when (verdict) {
            is Verdict.Correct -> {
                val previous = target
                index++
                promptShownAtNanos = (verdict.press.verdictNanos)
                lastStatus = RecognitionStatus.CORRECT
                if (complete) {
                    lastFeedback = completionCopy
                    lastLine2 = null
                } else {
                    val next = expectedMidi!!
                    lastFeedback = Copy.yesNow(next, next == previous)
                    lastLine2 = null
                }
            }
            is Verdict.WrongKey -> {
                lastStatus = RecognitionStatus.INCORRECT
                val lines = Copy.wrongKey(verdict.heardMidi, target)
                lastFeedback = "${lines.first} ${lines.second ?: ""}".trim()
                lastLine2 = lines.second
            }
            is Verdict.WrongOctave -> {
                lastStatus = RecognitionStatus.CORRECT_OCTAVE_MISMATCH
                val lines = Copy.wrongOctave(verdict.heardMidi, target)
                lastFeedback = "${lines.first} ${lines.second}"
                lastLine2 = lines.second
            }
            is Verdict.Unclear -> {
                lastStatus = if (verdict.reason == UnclearReason.TWO_NOTES) {
                    RecognitionStatus.AMBIGUOUS
                } else {
                    RecognitionStatus.UNCLEAR
                }
                lastFeedback = Copy.unclear(verdict.reason)
                lastLine2 = null
            }
            is Verdict.IgnoredStale -> Unit
        }
    }

    private fun snapshotFrom(feedback: String, line2: String?): LessonSnapshot {
        val target = expectedMidi
        val cue = cueOf()
        return LessonSnapshot(
            expectedMidi = target,
            heardMidi = lastHeardMidi,
            letter = target?.let { letterOf(it) } ?: "—",
            noteName = target?.let { displayNoteName(it) } ?: "Done",
            completedCount = index.coerceAtMost(notes.size),
            total = notes.size,
            progress = (index.coerceAtMost(notes.size)).toFloat() / notes.size,
            feedback = feedback,
            line2 = line2,
            status = lastStatus,
            cue = cue,
            level = lastPitch?.signalStrength ?: 0.0,
            complete = complete,
            verdict = lastVerdict,
        )
    }

    private fun cueOf(): LessonCue {
        if (complete) return LessonCue.DONE
        return when (lastStatus) {
            RecognitionStatus.CORRECT -> LessonCue.YES
            RecognitionStatus.INCORRECT -> LessonCue.TRY
            RecognitionStatus.CORRECT_OCTAVE_MISMATCH -> LessonCue.OCTAVE
            RecognitionStatus.AMBIGUOUS,
            RecognitionStatus.UNCLEAR,
            RecognitionStatus.NO_SIGNAL,
            RecognitionStatus.LOW_CONFIDENCE,
            -> LessonCue.UNCLEAR
            else -> LessonCue.LISTEN
        }
    }
}
