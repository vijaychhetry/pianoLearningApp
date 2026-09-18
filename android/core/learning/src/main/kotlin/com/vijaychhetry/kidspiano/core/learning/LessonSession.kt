package com.vijaychhetry.kidspiano.core.learning

import com.vijaychhetry.kidspiano.core.calibration.CalibrationProfile
import com.vijaychhetry.kidspiano.core.calibration.MVP_CALIBRATION_MIDI
import com.vijaychhetry.kidspiano.core.calibration.concertA4Hz
import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.PitchResult
import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.common.ValidationResult
import com.vijaychhetry.kidspiano.core.notes.DefaultNoteValidator
import com.vijaychhetry.kidspiano.core.notes.NoteValidator
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import com.vijaychhetry.kidspiano.core.pitch.DefaultNoteRecognizer
import com.vijaychhetry.kidspiano.core.pitch.FrameArrivalMonitor
import com.vijaychhetry.kidspiano.core.pitch.NoteDebouncer
import com.vijaychhetry.kidspiano.core.pitch.NotePhase
import com.vijaychhetry.kidspiano.core.pitch.NoteRecognizer
import com.vijaychhetry.kidspiano.core.pitch.PitchDetector
import com.vijaychhetry.kidspiano.core.pitch.SilenceWatchdog
import com.vijaychhetry.kidspiano.core.pitch.YinHpsPitchDetector

enum class LessonCue { LISTEN, YES, TRY, UNCLEAR, OCTAVE, DONE }

data class LessonSnapshot(
    val expectedMidi: Int?,
    val letter: String,
    val noteName: String,
    val completedCount: Int,
    val total: Int,
    val progress: Float,
    val feedback: String,
    val status: RecognitionStatus,
    val cue: LessonCue,
    val level: Double,
    val complete: Boolean,
)

fun lessonMayStart(profile: CalibrationProfile?): Boolean =
    profile != null && profile.usableAsDefault

fun lessonSessionFor(profile: CalibrationProfile): LessonSession {
    val a4 = concertA4Hz(profile)
    return LessonSession(
        detector = YinHpsPitchDetector(a4Hz = a4),
        validator = DefaultNoteValidator(a4Hz = a4),
    )
}

/**
 * Level 1 "find this key" loop with no Android types. One physical press is
 * one judgement; a held correct C must not be scored as a wrong D.
 */
class LessonSession(
    private val notes: List<Int> = MVP_CALIBRATION_MIDI,
    private val detector: PitchDetector = YinHpsPitchDetector(),
    private val recognizer: NoteRecognizer = DefaultNoteRecognizer(),
    private val validator: NoteValidator = DefaultNoteValidator(),
    private val debouncer: NoteDebouncer = NoteDebouncer(),
    private val silence: SilenceWatchdog = SilenceWatchdog(),
    private val arrival: FrameArrivalMonitor = FrameArrivalMonitor(),
) {
    private var index = 0
    private var judgedMidi: Int? = null
    private var lastResult: ValidationResult? = null
    private var lastPitch: PitchResult? = null
    private var lastPhase: NotePhase = NotePhase.IDLE
    private var lastFeedback: String = "Play the C key."

    val expectedMidi: Int? get() = notes.getOrNull(index)
    val complete: Boolean get() = index >= notes.size

    var sourceLooksDead: Boolean = false
        private set

    fun begin(atMs: Long) {
        sourceLooksDead = false
        silence.reset()
        arrival.start(atMs)
        debouncer.reset()
        // Keep judgedMidi: a mic restart mid-hold must not turn a correct C
        // into a wrong D.
    }

    fun snapshot(): LessonSnapshot = snapshotFrom(lastFeedback)

    fun onFrame(frame: AudioFrame): LessonSnapshot {
        arrival.onFrame(frame.capturedAtMs)
        val pitch = detector.detect(frame)
        lastPitch = pitch
        if (silence.onFrame(pitch.signalStrength, frame.capturedAtMs)) sourceLooksDead = true
        val phase = debouncer.onFrame(if (pitch.ambiguous) null else pitch.midiNote)
        lastPhase = phase
        if (phase == NotePhase.IDLE) judgedMidi = null
        val target = expectedMidi
        val pressLocked = judgedMidi != null && phase != NotePhase.IDLE
        when {
            target == null -> lastFeedback = "You found all five keys!"
            pitch.ambiguous && !pressLocked -> {
                lastResult = ValidationResult(
                    status = RecognitionStatus.AMBIGUOUS,
                    expectedNote = target,
                    detectedNote = pitch.midiNote,
                    confidence = pitch.confidence,
                    message = UNCLEAR_COPY,
                )
                lastFeedback = UNCLEAR_COPY
            }
            phase == NotePhase.STABLE && pitch.midiNote != judgedMidi -> {
                judgedMidi = pitch.midiNote
                val result = validator.validate(target, recognizer.recognize(pitch))
                lastResult = result
                if (result.status == RecognitionStatus.CORRECT) {
                    index++
                    lastFeedback = if (complete) {
                        "You found all five keys!"
                    } else {
                        "Yes! Now ${letterOf(expectedMidi)}."
                    }
                } else {
                    lastFeedback = childCopy(result.status, target)
                }
            }
            lastResult == null -> {
                lastFeedback = if (pitch.signalStrength < YinHpsPitchDetector.MIN_RMS) {
                    "Play the ${letterOf(target)} key."
                } else {
                    "Hold the ${letterOf(target)} key so I can hear it clearly."
                }
            }
        }
        return snapshotFrom(lastFeedback)
    }

    fun onTick(nowMs: Long): LessonSnapshot? {
        if (!arrival.isStalled(nowMs)) return null
        sourceLooksDead = true
        lastFeedback = "This microphone is not sending any audio."
        return snapshotFrom(lastFeedback)
    }

    fun restart(): LessonSnapshot {
        index = 0
        judgedMidi = null
        lastResult = null
        lastPitch = null
        lastPhase = NotePhase.IDLE
        lastFeedback = "Play the C key."
        sourceLooksDead = false
        silence.reset()
        debouncer.reset()
        return snapshotFrom(lastFeedback)
    }

    fun onSourceSwitched() {
        sourceLooksDead = false
        silence.reset()
        debouncer.reset()
    }

    private fun snapshotFrom(feedback: String): LessonSnapshot {
        val target = expectedMidi
        val cue = cueOf()
        val status = when {
            pressIsLocked() && lastResult != null -> lastResult!!.status
            lastPitch?.ambiguous == true -> RecognitionStatus.AMBIGUOUS
            lastResult != null -> lastResult!!.status
            (lastPitch?.signalStrength ?: 0.0) < YinHpsPitchDetector.MIN_RMS ->
                RecognitionStatus.NO_SIGNAL
            else -> RecognitionStatus.LISTENING
        }
        return LessonSnapshot(
            expectedMidi = target,
            letter = letterOf(target),
            noteName = target?.let { midiToNoteName(it) } ?: "Done",
            completedCount = index.coerceAtMost(notes.size),
            total = notes.size,
            progress = (index.coerceAtMost(notes.size)).toFloat() / notes.size,
            feedback = feedback,
            status = status,
            cue = cue,
            level = lastPitch?.signalStrength ?: 0.0,
            complete = complete,
        )
    }

    private fun cueOf(): LessonCue {
        if (complete) return LessonCue.DONE
        return when (lastResult?.status) {
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

    private fun childCopy(status: RecognitionStatus, expected: Int): String {
        val letter = letterOf(expected)
        return when (status) {
            RecognitionStatus.INCORRECT -> "Try $letter."
            RecognitionStatus.CORRECT_OCTAVE_MISMATCH ->
                "Right letter, try the other $letter."
            else -> UNCLEAR_COPY
        }
    }

    private fun pressIsLocked(): Boolean =
        judgedMidi != null && lastPhase != NotePhase.IDLE

    private fun letterOf(midi: Int?): String =
        midi?.let { midiToNoteName(it).dropLast(1) } ?: "—"

    private companion object {
        const val UNCLEAR_COPY = "I couldn't hear that. Try again."
    }
}
