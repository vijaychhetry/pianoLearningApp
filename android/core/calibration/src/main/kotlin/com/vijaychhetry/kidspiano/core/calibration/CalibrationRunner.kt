package com.vijaychhetry.kidspiano.core.calibration

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.notes.letterOf as noteLetter
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import com.vijaychhetry.kidspiano.core.pitch.DefaultNoteRecognizer
import com.vijaychhetry.kidspiano.core.pitch.FrameArrivalMonitor
import com.vijaychhetry.kidspiano.core.pitch.NoteDebouncer
import com.vijaychhetry.kidspiano.core.pitch.NotePhase
import com.vijaychhetry.kidspiano.core.pitch.NoteRecognizer
import com.vijaychhetry.kidspiano.core.pitch.PitchDetector
import com.vijaychhetry.kidspiano.core.pitch.SilenceWatchdog
import com.vijaychhetry.kidspiano.core.pitch.YinHpsPitchDetector

/**
 * The whole guided-calibration loop with no Android types, so the prompt
 * sequence, the wrong-key rejection and the one-sample-per-press rule can be
 * tested against synthesised audio.
 */
class CalibrationRunner(
    private val detector: PitchDetector = YinHpsPitchDetector(),
    private val recognizer: NoteRecognizer = DefaultNoteRecognizer(),
    private val debouncer: NoteDebouncer = NoteDebouncer(),
    private val engine: CalibrationEngine = MedianCalibrationEngine(),
    private val silence: SilenceWatchdog = SilenceWatchdog(),
    private val arrival: FrameArrivalMonitor = FrameArrivalMonitor(),
    private val newSession: () -> CalibrationSession = { CalibrationSession() },
) {
    data class Snapshot(
        val targetMidi: Int?,
        val letter: String,
        val noteName: String,
        val samplesDone: Int,
        val samplesNeeded: Int,
        val progress: Float,
        val feedback: String,
        val level: Double,
        val profile: CalibrationProfile? = null,
    )

    private var session = newSession()
    private var lastLevel = 0.0
    private var waitForIdle = false

    /** Labels the saved profile; the caller sets it from the live mic source. */
    var microphoneLabel: String = "unknown"
    var sampleRate: Int = 44100
        private set

    var sourceLooksDead: Boolean = false
        private set

    var profile: CalibrationProfile? = null
        private set

    fun begin(atMs: Long) {
        sourceLooksDead = false
        silence.reset()
        arrival.start(atMs)
        debouncer.reset()
        session.onRelease()
    }

    fun onFrame(frame: AudioFrame): Snapshot {
        sampleRate = frame.sampleRate
        arrival.onFrame(frame.capturedAtMs)
        val pitch = detector.detect(frame)
        lastLevel = pitch.signalStrength
        if (silence.onFrame(pitch.signalStrength, frame.capturedAtMs)) sourceLooksDead = true
        val phase = debouncer.onFrame(if (pitch.ambiguous) null else pitch.midiNote)
        if (phase == NotePhase.IDLE) waitForIdle = false
        if (phase != NotePhase.STABLE) {
            // The key was let go: the next steady note is a new press, and only
            // a new press may contribute another sample.
            session.onRelease()
        }
        val target = session.currentMidi
        if (target == null) {
            finishIfComplete()
            return snapshot("All five keys captured.")
        }

        val feedback = when {
            pitch.signalStrength < YinHpsPitchDetector.MIN_RMS ->
                "Too quiet — move the phone closer, then play ${letterOf(target)}."
            waitForIdle ->
                "Let go, then play ${nameOf(target)}."
            phase != NotePhase.STABLE ->
                "Hold the ${nameOf(target)} key so I can hear it clearly."
            else -> {
                val heard = recognizer.recognize(pitch)
                when (session.offer(heard?.midi, heard?.frequency, pitch.confidence)) {
                    CalibrationSession.Offer.ACCEPTED ->
                        "Good — ${nameOf(target)} ${session.acceptedCount(target)} of ${session.samplesPerNote}. " +
                            "Let go, then play it again."
                    CalibrationSession.Offer.SAME_PRESS ->
                        "Let go of ${nameOf(target)}, then play it again."
                    CalibrationSession.Offer.WRONG_KEY ->
                        "That was ${nameOf(heard?.midi)}. Please play ${nameOf(target)}."
                    CalibrationSession.Offer.UNCLEAR ->
                        "I couldn't hear that clearly. Play ${nameOf(target)} again."
                    CalibrationSession.Offer.DONE -> "All five keys captured."
                }
            }
        }
        finishIfComplete()
        return snapshot(feedback)
    }

    /** Timer-driven, so a source that never delivers a frame is still noticed. */
    fun onTick(nowMs: Long): Snapshot? {
        if (!arrival.isStalled(nowMs)) return null
        sourceLooksDead = true
        return snapshot("This microphone is not sending any audio.")
    }

    fun jumpTo(midi: Int): Snapshot {
        if (profile != null) return snapshot()
        debouncer.reset()
        session.onRelease()
        session.jumpTo(midi)
        waitForIdle = true
        val target = session.currentMidi
        return snapshot(target?.let { "Now play ${nameOf(it)}." } ?: "Finished.")
    }

    fun skip(): Snapshot {
        session.skipCurrent()
        finishIfComplete()
        val target = session.currentMidi
        return snapshot(target?.let { "Skipped. Now play ${nameOf(it)}." } ?: "Finished.")
    }

    fun restart(): Snapshot {
        session = newSession()
        profile = null
        sourceLooksDead = false
        silence.reset()
        waitForIdle = false
        return snapshot("Tap Start, then play the ${nameOf(session.currentMidi)} key.")
    }

    fun onSourceSwitched() {
        sourceLooksDead = false
        silence.reset()
    }

    fun prompt(): String =
        session.currentMidi?.let { "Play the ${nameOf(it)} key." } ?: "Finished."

    fun snapshot(feedback: String = prompt()): Snapshot {
        val target = session.currentMidi
        return Snapshot(
            targetMidi = target,
            letter = letterOf(target),
            noteName = target?.let { midiToNoteName(it) } ?: "Done",
            samplesDone = target?.let { session.acceptedCount(it) } ?: 0,
            samplesNeeded = session.samplesPerNote,
            progress = session.progress,
            feedback = feedback,
            level = lastLevel,
            profile = profile,
        )
    }

    private fun finishIfComplete() {
        if (profile == null && session.isComplete) {
            profile = session.buildProfile(engine, sampleRate, microphoneLabel)
        }
    }

    private fun letterOf(midi: Int?): String = midi?.let { noteLetter(it) } ?: "—"

    private fun nameOf(midi: Int?): String = midi?.let { midiToNoteName(it) } ?: "—"
}
