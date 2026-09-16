package com.vijaychhetry.kidspiano.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vijaychhetry.kidspiano.core.audio.AudioRecordInput
import com.vijaychhetry.kidspiano.core.calibration.CalibrationProfile
import com.vijaychhetry.kidspiano.core.calibration.CalibrationSession
import com.vijaychhetry.kidspiano.core.calibration.MedianCalibrationEngine
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import com.vijaychhetry.kidspiano.core.pitch.DefaultNoteRecognizer
import com.vijaychhetry.kidspiano.core.pitch.NoteDebouncer
import com.vijaychhetry.kidspiano.core.pitch.NotePhase
import com.vijaychhetry.kidspiano.core.pitch.SilenceWatchdog
import com.vijaychhetry.kidspiano.core.pitch.YinHpsPitchDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalibrationUiState(
    val running: Boolean = false,
    val error: String? = null,
    val askLetter: String = "C",
    val askNoteName: String = "C4",
    val samplesDone: Int = 0,
    val samplesNeeded: Int = SAMPLES_PER_NOTE,
    val progress: Float = 0f,
    val feedback: String = "Tap Start, then play the C key.",
    val level: Double = 0.0,
    val sourceLabel: String = "—",
    val profile: CalibrationProfile? = null,
    val savedSummary: String? = null,
) {
    companion object {
        const val SAMPLES_PER_NOTE = 8
    }
}

class CalibrationViewModel : ViewModel() {
    private val detector = YinHpsPitchDetector()
    private val recognizer = DefaultNoteRecognizer()
    private val debouncer = NoteDebouncer()
    private val watchdog = SilenceWatchdog()
    private val input = AudioRecordInput()
    private var session = CalibrationSession(samplesPerNote = CalibrationUiState.SAMPLES_PER_NOTE)
    private var collectJob: Job? = null
    private var pinnedSource: Int? = null

    private val _state = MutableStateFlow(CalibrationUiState())
    val state: StateFlow<CalibrationUiState> = _state

    fun setSavedSummary(summary: String?) {
        _state.update { it.copy(savedSummary = summary) }
    }

    fun start() {
        if (_state.value.running) return
        watchdog.reset()
        try {
            input.start(pinnedSource)
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Microphone failed", running = false) }
            return
        }
        _state.update {
            it.copy(
                running = true,
                error = null,
                sourceLabel = AudioRecordInput.sourceName(input.appliedSource),
                feedback = "Play the ${letterOf(session.currentMidi)} key.",
            )
        }
        collectJob = viewModelScope.launch {
            input.audioFrames().collect { frame ->
                val pitch = detector.detect(frame)
                val phase = debouncer.onFrame(if (pitch.ambiguous) null else pitch.midiNote)
                if (watchdog.onFrame(pitch.signalStrength, frame.capturedAtMs)) {
                    switchSource(auto = true)
                    return@collect
                }
                val target = session.currentMidi
                var feedback = _state.value.feedback
                if (target == null) {
                    feedback = "All five keys captured."
                } else if (pitch.signalStrength < YinHpsPitchDetector.MIN_RMS) {
                    feedback = "Too quiet — move the phone closer, then play ${letterOf(target)}."
                } else if (phase != NotePhase.STABLE) {
                    feedback = "Hold the ${letterOf(target)} key so I can hear it clearly."
                } else {
                    val heard = recognizer.recognize(pitch)
                    feedback = when (session.offer(heard?.midi, heard?.frequency, pitch.confidence)) {
                        CalibrationSession.Offer.ACCEPTED ->
                            "Good — ${letterOf(target)} ${session.acceptedCount(target)} of ${CalibrationUiState.SAMPLES_PER_NOTE}."
                        CalibrationSession.Offer.WRONG_KEY ->
                            "That was ${letterOf(heard?.midi)}. Please play ${letterOf(target)}."
                        CalibrationSession.Offer.UNCLEAR ->
                            "I couldn't hear that clearly. Play ${letterOf(target)} again."
                        CalibrationSession.Offer.DONE -> "All five keys captured."
                    }
                }
                val nowTarget = session.currentMidi
                _state.update { current ->
                    current.copy(
                        askLetter = letterOf(nowTarget),
                        askNoteName = nowTarget?.let { midiToNoteName(it) } ?: "Done",
                        samplesDone = nowTarget?.let { session.acceptedCount(it) } ?: 0,
                        progress = session.progress,
                        feedback = feedback,
                        level = pitch.signalStrength,
                    )
                }
                if (session.isComplete && _state.value.profile == null) {
                    finish()
                }
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        input.stop()
        _state.update { it.copy(running = false) }
    }

    fun restart() {
        stop()
        session = CalibrationSession(samplesPerNote = CalibrationUiState.SAMPLES_PER_NOTE)
        _state.update {
            it.copy(
                profile = null,
                progress = 0f,
                samplesDone = 0,
                askLetter = "C",
                askNoteName = "C4",
                feedback = "Tap Start, then play the C key.",
            )
        }
    }

    /** Skip a key the child cannot find; the quality score will reflect the gap. */
    fun skip() {
        session.skipCurrent()
        val target = session.currentMidi
        _state.update {
            it.copy(
                askLetter = letterOf(target),
                askNoteName = target?.let { midi -> midiToNoteName(midi) } ?: "Done",
                samplesDone = target?.let { midi -> session.acceptedCount(midi) } ?: 0,
                progress = session.progress,
                feedback = target?.let { "Skipped. Now play ${letterOf(it)}." } ?: "Finished.",
            )
        }
        if (session.isComplete && _state.value.profile == null) finish()
    }

    fun switchSource(auto: Boolean = false) {
        val next = AudioRecordInput.nextSource(input.appliedSource)
        val wasRunning = _state.value.running
        stop()
        pinnedSource = next
        if (wasRunning) start()
        _state.update {
            it.copy(
                feedback = if (auto) {
                    "That microphone was silent — switched to ${AudioRecordInput.sourceName(next)}."
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

    private fun finish() {
        val profile = session.buildProfile(
            MedianCalibrationEngine(),
            input.appliedSampleRate,
            AudioRecordInput.sourceName(input.appliedSource),
        )
        stop()
        _state.update {
            it.copy(
                profile = profile,
                progress = 1f,
                feedback = "Calibration ${profile.calibrationQuality.name.lowercase().replace('_', ' ')}.",
            )
        }
    }

    private fun letterOf(midi: Int?): String =
        midi?.let { midiToNoteName(it).dropLast(1) } ?: "—"
}
