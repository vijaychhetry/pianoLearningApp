package com.vijaychhetry.kidspiano.lab

import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vijaychhetry.kidspiano.core.audio.AudioRecordInput
import com.vijaychhetry.kidspiano.core.common.PitchResult
import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import com.vijaychhetry.kidspiano.core.pitch.NoteDebouncer
import com.vijaychhetry.kidspiano.core.pitch.NotePhase
import com.vijaychhetry.kidspiano.core.pitch.YinHpsPitchDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LabEvent(
    val timeMs: Long,
    val note: String,
    val confidence: Double,
    val status: RecognitionStatus,
)

data class AudioLabState(
    val running: Boolean = false,
    val permissionNeeded: Boolean = true,
    val error: String? = null,
    val sourceLabel: String = "—",
    val sampleRate: Int = 0,
    val pitch: PitchResult? = null,
    val noteName: String = "—",
    val phase: NotePhase = NotePhase.IDLE,
    val events: List<LabEvent> = emptyList(),
)

class AudioLabViewModel : ViewModel() {
    private val detector = YinHpsPitchDetector()
    private val debouncer = NoteDebouncer()
    private val input = AudioRecordInput()
    private var collectJob: Job? = null

    private val _state = MutableStateFlow(AudioLabState())
    val state: StateFlow<AudioLabState> = _state

    fun onPermission(granted: Boolean) {
        _state.update { it.copy(permissionNeeded = !granted) }
    }

    fun start() {
        if (_state.value.running) return
        try {
            input.start()
            _state.update {
                it.copy(
                    running = true,
                    error = null,
                    sourceLabel = sourceName(input.appliedSource),
                    sampleRate = input.appliedSampleRate,
                )
            }
            collectJob = viewModelScope.launch {
                input.audioFrames().collect { frame ->
                    val pitch = detector.detect(frame)
                    val phase = debouncer.onFrame(pitch.midiNote)
                    val name = pitch.midiNote?.let { midiToNoteName(it) } ?: "—"
                    val status = when {
                        pitch.midiNote == null -> RecognitionStatus.NO_SIGNAL
                        pitch.confidence < 0.55 -> RecognitionStatus.LOW_CONFIDENCE
                        phase == NotePhase.STABLE -> RecognitionStatus.HIGH_CONFIDENCE
                        else -> RecognitionStatus.NOTE_DETECTED
                    }
                    _state.update { current ->
                        val events = if (phase == NotePhase.STABLE && pitch.midiNote != null) {
                            val last = current.events.firstOrNull()
                            val event = LabEvent(pitch.timestampMs, name, pitch.confidence, status)
                            if (last?.note == name) current.events else listOf(event) + current.events.take(24)
                        } else {
                            current.events
                        }
                        current.copy(pitch = pitch, noteName = name, phase = phase, events = events)
                    }
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(running = false, error = e.message ?: "Microphone failed") }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        input.stop()
        _state.update { it.copy(running = false) }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private fun sourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> "source $source"
    }
}
