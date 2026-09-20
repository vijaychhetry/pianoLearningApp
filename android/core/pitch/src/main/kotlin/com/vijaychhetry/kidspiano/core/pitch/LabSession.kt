package com.vijaychhetry.kidspiano.core.pitch

import com.vijaychhetry.kidspiano.core.common.AudioFrame
import com.vijaychhetry.kidspiano.core.common.PitchResult
import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName

data class LabSnapshot(
    val status: RecognitionStatus,
    val noteName: String,
    val pitch: PitchResult?,
    val level: Double,
    val peakLevel: Double,
    val frameCount: Long,
    val phase: NotePhase,
    val hint: String,
    val log: List<LabLogEntry>,
)

/**
 * All of the Audio Lab's per-frame behaviour, with no Android types, so the
 * screen's promises (spec §15) can be tested against real synthesised audio.
 */
class LabSession(
    private val detector: PitchDetector = YinHpsPitchDetector(),
    private val debouncer: NoteDebouncer = NoteDebouncer(),
    private val log: LabEventLog = LabEventLog(),
    private val silence: SilenceWatchdog = SilenceWatchdog(),
    private val arrival: FrameArrivalMonitor = FrameArrivalMonitor(),
) {
    private var frames = 0L
    private var peak = 0.0
    private var last: LabSnapshot = idleSnapshot(START_HINT)

    val snapshot: LabSnapshot get() = last.copy(log = log.entries)

    /** Silence or a stall was detected and the caller should try the next mic source. */
    var sourceLooksDead: Boolean = false
        private set

    fun begin(atMs: Long) {
        frames = 0
        peak = 0.0
        sourceLooksDead = false
        silence.reset()
        debouncer.reset()
        arrival.start(atMs)
        last = idleSnapshot("Listening. Play one key.")
    }

    /** Keep the log across an automatic source switch; only a fresh run clears it. */
    fun clearLog() = log.clear()

    fun onFrame(frame: AudioFrame): LabSnapshot {
        val pitch = detector.detect(frame)
        val phase = debouncer.onFrame(if (pitch.ambiguous) null else pitch.midiNote)
        val name = pitch.midiNote?.let { midiToNoteName(it) } ?: NO_NOTE
        val status = statusOf(pitch, phase)
        frames++
        peak = maxOf(peak, pitch.signalStrength)
        arrival.onFrame(frame.capturedAtMs)
        log.onFrame(status, name, pitch.confidence, pitch.frequency, frame.capturedAtMs)
        if (silence.onFrame(pitch.signalStrength, frame.capturedAtMs)) {
            sourceLooksDead = true
        }
        last = LabSnapshot(
            status = status,
            noteName = name,
            pitch = pitch,
            level = pitch.signalStrength,
            peakLevel = peak,
            frameCount = frames,
            phase = phase,
            hint = hintFor(status, pitch),
            log = log.entries,
        )
        return last
    }

    /**
     * Drive from a timer, not the audio flow: a source that opens but never
     * delivers a frame has to be visible too.
     */
    fun onTick(nowMs: Long): LabSnapshot? {
        if (!arrival.isStalled(nowMs)) return null
        sourceLooksDead = true
        log.onFrame(RecognitionStatus.NO_SIGNAL, NO_NOTE, 0.0, null, nowMs)
        last = last.copy(
            status = RecognitionStatus.NO_SIGNAL,
            hint = "This microphone is not sending any audio.",
            log = log.entries,
        )
        return last
    }

    private fun idleSnapshot(hint: String) = LabSnapshot(
        status = RecognitionStatus.NO_SIGNAL,
        noteName = NO_NOTE,
        pitch = null,
        level = 0.0,
        peakLevel = 0.0,
        frameCount = 0,
        phase = NotePhase.IDLE,
        hint = hint,
        log = log.entries,
    )

    private fun statusOf(pitch: PitchResult, phase: NotePhase): RecognitionStatus = when {
        pitch.signalStrength < YinHpsPitchDetector.MIN_RMS -> RecognitionStatus.NO_SIGNAL
        pitch.ambiguous -> RecognitionStatus.AMBIGUOUS
        pitch.midiNote == null -> RecognitionStatus.LISTENING
        pitch.confidence < LOW_CONFIDENCE -> RecognitionStatus.LOW_CONFIDENCE
        phase == NotePhase.STABLE -> RecognitionStatus.HIGH_CONFIDENCE
        else -> RecognitionStatus.NOTE_DETECTED
    }

    private fun hintFor(status: RecognitionStatus, pitch: PitchResult): String = when (status) {
        RecognitionStatus.NO_SIGNAL ->
            "Too quiet. Move the phone closer to the piano, or turn the volume up."
        RecognitionStatus.LISTENING ->
            "I hear sound but no clear note yet. Play a single key and hold it."
        RecognitionStatus.AMBIGUOUS ->
            "That sounds like more than one note. Play one key on its own."
        RecognitionStatus.LOW_CONFIDENCE ->
            "Almost — hold the key a little longer."
        else -> "Heard ${pitch.midiNote?.let { midiToNoteName(it) } ?: "a note"}."
    }

    companion object {
        const val NO_NOTE = "—"
        const val LOW_CONFIDENCE = 0.55
        const val START_HINT = "Tap Start listening, then play one key."
    }
}
