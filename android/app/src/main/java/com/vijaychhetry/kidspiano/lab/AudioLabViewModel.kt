package com.vijaychhetry.kidspiano.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vijaychhetry.kidspiano.core.audio.AudioRecordInput
import com.vijaychhetry.kidspiano.core.common.PitchResult
import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import com.vijaychhetry.kidspiano.core.pitch.LabEventLog
import com.vijaychhetry.kidspiano.core.pitch.LabLogEntry
import com.vijaychhetry.kidspiano.core.pitch.NoteDebouncer
import com.vijaychhetry.kidspiano.core.pitch.NotePhase
import com.vijaychhetry.kidspiano.core.pitch.SilenceWatchdog
import com.vijaychhetry.kidspiano.core.pitch.YinHpsPitchDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AudioLabState(
    val running: Boolean = false,
    val permissionNeeded: Boolean = true,
    val error: String? = null,
    val hint: String = "Tap Start listening, then play one key.",
    val sourceLabel: String = "—",
    val sampleRate: Int = 0,
    val pitch: PitchResult? = null,
    val noteName: String = "—",
    val status: RecognitionStatus = RecognitionStatus.NO_SIGNAL,
    val phase: NotePhase = NotePhase.IDLE,
    val level: Double = 0.0,
    val peakLevel: Double = 0.0,
    val frameCount: Long = 0,
    val events: List<LabLogEntry> = emptyList(),
)

class AudioLabViewModel : ViewModel() {
    private val detector = YinHpsPitchDetector()
    private val debouncer = NoteDebouncer()
    private val input = AudioRecordInput()
    private val log = LabEventLog()
    private val watchdog = SilenceWatchdog()
    private var collectJob: Job? = null
    private var pinnedSource: Int? = null

    private val _state = MutableStateFlow(AudioLabState())
    val state: StateFlow<AudioLabState> = _state

    fun onPermission(granted: Boolean) {
        _state.update { it.copy(permissionNeeded = !granted) }
    }

    fun start() {
        if (_state.value.running) return
        log.clear()
        watchdog.reset()
        try {
            input.start(pinnedSource)
        } catch (e: Exception) {
            _state.update {
                it.copy(running = false, error = e.message ?: "Microphone failed", hint = "")
            }
            return
        }
        _state.update {
            it.copy(
                running = true,
                error = null,
                hint = "Listening. Play one key.",
                sourceLabel = AudioRecordInput.sourceName(input.appliedSource),
                sampleRate = input.appliedSampleRate,
                peakLevel = 0.0,
                frameCount = 0,
                events = emptyList(),
            )
        }
        collectJob = viewModelScope.launch {
            input.audioFrames().collect { frame ->
                val pitch = detector.detect(frame)
                val phase = debouncer.onFrame(if (pitch.ambiguous) null else pitch.midiNote)
                val name = pitch.midiNote?.let { midiToNoteName(it) } ?: "—"
                val status = statusOf(pitch, phase)
                log.onFrame(status, name, pitch.confidence, pitch.frequency, frame.capturedAtMs)
                if (watchdog.onFrame(pitch.signalStrength, frame.capturedAtMs)) {
                    switchSource(auto = true)
                    return@collect
                }
                _state.update { current ->
                    current.copy(
                        pitch = pitch,
                        noteName = name,
                        status = status,
                        phase = phase,
                        level = pitch.signalStrength,
                        peakLevel = maxOf(current.peakLevel, pitch.signalStrength),
                        frameCount = current.frameCount + 1,
                        hint = hintFor(status, pitch),
                        events = log.entries,
                    )
                }
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        input.stop()
        _state.update {
            it.copy(running = false, hint = "Stopped. Tap Start listening to try again.")
        }
    }

    /** Try the next `AudioSource`; some phones return silence on UNPROCESSED. */
    fun switchSource(auto: Boolean = false) {
        val next = AudioRecordInput.nextSource(input.appliedSource)
        val wasRunning = _state.value.running
        stop()
        pinnedSource = next
        if (wasRunning) start()
        _state.update {
            it.copy(
                hint = if (auto) {
                    "${AudioRecordInput.sourceName(next)}: the last source was silent, switched automatically."
                } else {
                    "Now using ${AudioRecordInput.sourceName(next)}."
                },
            )
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private fun statusOf(pitch: PitchResult, phase: NotePhase): RecognitionStatus = when {
        pitch.signalStrength < YinHpsPitchDetector.MIN_RMS -> RecognitionStatus.NO_SIGNAL
        pitch.ambiguous -> RecognitionStatus.AMBIGUOUS
        pitch.midiNote == null -> RecognitionStatus.LISTENING
        pitch.confidence < 0.55 -> RecognitionStatus.LOW_CONFIDENCE
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
}
