package com.vijaychhetry.kidspiano.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vijaychhetry.kidspiano.core.audio.AudioRecordInput
import com.vijaychhetry.kidspiano.core.common.PitchResult
import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.pitch.LabLogEntry
import com.vijaychhetry.kidspiano.core.pitch.LabSession
import com.vijaychhetry.kidspiano.core.pitch.LabSnapshot
import com.vijaychhetry.kidspiano.core.pitch.MicSourceCycler
import com.vijaychhetry.kidspiano.core.pitch.NotePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AudioLabState(
    val running: Boolean = false,
    val permissionNeeded: Boolean = true,
    val error: String? = null,
    val hint: String = LabSession.START_HINT,
    val sourceLabel: String = "—",
    val sampleRate: Int = 0,
    val pitch: PitchResult? = null,
    val noteName: String = LabSession.NO_NOTE,
    val status: RecognitionStatus = RecognitionStatus.NO_SIGNAL,
    val phase: NotePhase = NotePhase.IDLE,
    val level: Double = 0.0,
    val peakLevel: Double = 0.0,
    val frameCount: Long = 0,
    val events: List<LabLogEntry> = emptyList(),
)

/**
 * Thin adapter over [LabSession]: everything the screen promises is decided
 * there so it can be tested against real audio off-device.
 */
class AudioLabViewModel : ViewModel() {
    private val input = AudioRecordInput()
    private val session = LabSession()
    private val sources = MicSourceCycler(AudioRecordInput.SOURCE_ORDER)
    private val control = Mutex()
    private var collectJob: Job? = null
    private var tickJob: Job? = null

    private val _state = MutableStateFlow(AudioLabState())
    val state: StateFlow<AudioLabState> = _state

    fun onPermission(granted: Boolean) {
        _state.update { it.copy(permissionNeeded = !granted) }
    }

    fun start() {
        if (_state.value.running) return
        viewModelScope.launch {
            control.withLock {
                sources.reset()
                session.clearLog()
                beginCapture()
            }
        }
        startTicker()
    }

    fun stop() {
        viewModelScope.launch {
            control.withLock {
                tickJob?.cancel()
                tickJob = null
                endCapture()
                _state.update {
                    it.copy(running = false, hint = "Stopped. Tap Start listening to try again.")
                }
            }
        }
    }

    /** Try another `AudioSource`; some phones return silence on UNPROCESSED. */
    fun switchSource() {
        viewModelScope.launch {
            control.withLock {
                val next = sources.forceAdvance()
                if (!_state.value.running) {
                    _state.update {
                        it.copy(
                            sourceLabel = AudioRecordInput.sourceName(next),
                            hint = "Will use ${AudioRecordInput.sourceName(next)}. Tap Start listening.",
                        )
                    }
                    return@withLock
                }
                endCapture()
                beginCapture()
                if (_state.value.running) {
                    _state.update { it.copy(hint = "Now using ${AudioRecordInput.sourceName(next)}.") }
                }
            }
        }
    }

    override fun onCleared() {
        tickJob?.cancel()
        collectJob?.cancel()
        input.stop()
        super.onCleared()
    }

    /**
     * Runs off the audio flow so a source that opens but never delivers a
     * frame is still visible, and so the automatic switch is never performed
     * by the collector it is about to cancel.
     */
    private fun startTicker() {
        if (tickJob != null) return
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (!_state.value.running) continue
                control.withLock {
                    session.onTick(System.currentTimeMillis())?.let { publish(it) }
                    if (session.sourceLooksDead) rotateAfterSilence()
                }
            }
        }
    }

    private suspend fun beginCapture() {
        while (true) {
            try {
                withContext(Dispatchers.IO) { input.start(sources.current) }
                break
            } catch (e: Exception) {
                if (sources.advance() == null) {
                    _state.update {
                        it.copy(
                            running = false,
                            error = e.message ?: "Microphone failed",
                            hint = EXHAUSTED_HINT,
                        )
                    }
                    return
                }
            }
        }
        session.begin(System.currentTimeMillis())
        _state.update {
            it.copy(
                running = true,
                error = null,
                hint = "Listening. Play one key.",
                sourceLabel = AudioRecordInput.sourceName(input.appliedSource),
                sampleRate = input.appliedSampleRate,
                peakLevel = 0.0,
                frameCount = 0,
                events = session.snapshot.log,
            )
        }
        collectJob = viewModelScope.launch {
            input.audioFrames().collect { frame ->
                val snapshot = session.onFrame(frame)
                if (snapshot.level > HEALTHY_RMS) sources.reset()
                publish(snapshot)
            }
        }
    }

    private suspend fun endCapture() {
        collectJob?.cancelAndJoin()
        collectJob = null
        withContext(Dispatchers.IO) { input.stop() }
    }

    /**
     * One pass through the sources, then stop. Cycling forever on a muted
     * phone would keep restarting capture instead of saying what is wrong.
     */
    private suspend fun rotateAfterSilence() {
        val next = sources.advance()
        if (next == null) {
            endCapture()
            _state.update {
                it.copy(running = false, hint = EXHAUSTED_HINT, events = session.snapshot.log)
            }
            return
        }
        endCapture()
        // The log deliberately survives the switch: clearing it here is what
        // left the screen blank for the reporter.
        beginCapture()
        if (_state.value.running) {
            _state.update {
                it.copy(
                    hint = "${AudioRecordInput.sourceName(next)}: the last source was silent, " +
                        "switched automatically.",
                    events = session.snapshot.log,
                )
            }
        }
    }

    private fun publish(snapshot: LabSnapshot) {
        _state.update {
            it.copy(
                pitch = snapshot.pitch,
                noteName = snapshot.noteName,
                status = snapshot.status,
                phase = snapshot.phase,
                level = snapshot.level,
                peakLevel = snapshot.peakLevel,
                frameCount = snapshot.frameCount,
                hint = snapshot.hint,
                events = snapshot.log,
            )
        }
    }

    private companion object {
        const val TICK_MS = 250L
        const val HEALTHY_RMS = 0.0008
        const val EXHAUSTED_HINT =
            "No microphone source is sending audio. Check that KidsPiano has microphone " +
                "permission and that nothing else is using the mic, then tap Start listening."
    }
}
