package com.vijaychhetry.kidspiano.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vijaychhetry.kidspiano.core.audio.AudioRecordInput
import com.vijaychhetry.kidspiano.core.calibration.CalibrationProfile
import com.vijaychhetry.kidspiano.core.calibration.CalibrationRunner
import com.vijaychhetry.kidspiano.core.pitch.MicSourceCycler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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

data class CalibrationUiState(
    val running: Boolean = false,
    val error: String? = null,
    val askLetter: String = "C",
    val askNoteName: String = "C4",
    val samplesDone: Int = 0,
    val samplesNeeded: Int = 0,
    val progress: Float = 0f,
    val feedback: String = "Tap Start, then play the C key.",
    val level: Double = 0.0,
    val sourceLabel: String = "—",
    val profile: CalibrationProfile? = null,
    /**
     * Only ever set for a profile good enough to be the default, or one the
     * user explicitly accepted (spec §11).
     */
    val profileToSave: CalibrationProfile? = null,
    val savedSummary: String? = null,
) {
    val needsConfirmation: Boolean get() = profile != null && profileToSave == null
}

/** Thin adapter over [CalibrationRunner]; all of the decisions live there. */
class CalibrationViewModel : ViewModel() {
    private val input = AudioRecordInput()
    private val runner = CalibrationRunner()
    private val sources = MicSourceCycler(AudioRecordInput.SOURCE_ORDER)
    private val control = Mutex()
    private var collectJob: Job? = null
    private var tickJob: Job? = null

    /**
     * What the user last asked for. Start and stop queue behind [control], so
     * a tap must not be judged against state a pending coroutine has yet to
     * write.
     */
    private var wantRunning = false

    private val _state = MutableStateFlow(
        CalibrationUiState(samplesNeeded = runner.snapshot().samplesNeeded),
    )
    val state: StateFlow<CalibrationUiState> = _state

    fun setSavedSummary(summary: String?) {
        _state.update { it.copy(savedSummary = summary) }
    }

    fun start() {
        if (wantRunning) return
        wantRunning = true
        viewModelScope.launch {
            control.withLock {
                if (!wantRunning) return@withLock
                sources.reset()
                beginCapture()
            }
        }
        startTicker()
    }

    fun stop() {
        if (!wantRunning) return
        wantRunning = false
        _state.update { it.copy(running = false) }
        viewModelScope.launch {
            control.withLock {
                stopTicker()
                endCapture()
            }
        }
    }

    fun restart() {
        wantRunning = false
        viewModelScope.launch {
            control.withLock {
                stopTicker()
                endCapture()
                publish(runner.restart())
                _state.update { it.copy(running = false, profile = null, profileToSave = null) }
            }
        }
    }

    /** Skip a key the child cannot find; the quality score will reflect the gap. */
    fun skip() {
        viewModelScope.launch { control.withLock { publish(runner.skip()) } }
    }

    /** Keep a profile the engine scored as needing improvement. */
    fun acceptProfileAnyway() {
        _state.update { it.copy(profileToSave = it.profile) }
    }

    fun switchSource() {
        viewModelScope.launch {
            control.withLock {
                val next = sources.forceAdvance()
                runner.onSourceSwitched()
                if (!wantRunning) {
                    _state.update {
                        it.copy(
                            sourceLabel = AudioRecordInput.sourceName(next),
                            feedback = "Will use ${AudioRecordInput.sourceName(next)}. Tap Start.",
                        )
                    }
                    return@withLock
                }
                endCapture()
                beginCapture()
                if (_state.value.running) {
                    _state.update { it.copy(feedback = "Now using ${AudioRecordInput.sourceName(next)}.") }
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

    private fun startTicker() {
        if (tickJob != null) return
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (!wantRunning) continue
                control.withLock {
                    runner.onTick(System.currentTimeMillis())?.let { publish(it) }
                    if (runner.sourceLooksDead) rotateAfterSilence()
                }
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
    }

    private suspend fun beginCapture() {
        while (true) {
            try {
                withContext(Dispatchers.IO) { input.start(sources.current) }
                break
            } catch (e: CancellationException) {
                // The scope died while the mic was opening. Cancellation is
                // not interruption, so the recorder is live and only this
                // frame of control can still close it.
                withContext(NonCancellable + Dispatchers.IO) { input.stop() }
                throw e
            } catch (e: Exception) {
                if (sources.advance() == null) {
                    wantRunning = false
                    _state.update {
                        it.copy(running = false, error = e.message ?: "Microphone failed")
                    }
                    return
                }
            }
        }
        runner.microphoneLabel = AudioRecordInput.sourceName(input.appliedSource)
        runner.begin(System.currentTimeMillis())
        _state.update {
            it.copy(
                running = true,
                error = null,
                sourceLabel = AudioRecordInput.sourceName(input.appliedSource),
            )
        }
        publish(runner.snapshot())
        collectJob = viewModelScope.launch {
            input.audioFrames().collect { frame ->
                val snapshot = runner.onFrame(frame)
                if (snapshot.level > HEALTHY_RMS) sources.reset()
                publish(snapshot)
                if (snapshot.profile != null) stop()
            }
        }
    }

    private suspend fun endCapture() {
        collectJob?.cancelAndJoin()
        collectJob = null
        withContext(Dispatchers.IO) { input.stop() }
    }

    private suspend fun rotateAfterSilence() {
        val next = sources.advance()
        runner.onSourceSwitched()
        if (next == null) {
            wantRunning = false
            stopTicker()
            endCapture()
            _state.update { it.copy(running = false, feedback = EXHAUSTED_FEEDBACK) }
            return
        }
        endCapture()
        beginCapture()
        if (_state.value.running) {
            _state.update {
                it.copy(
                    feedback = "That microphone was silent — switched to " +
                        AudioRecordInput.sourceName(next) + ".",
                )
            }
        }
    }

    private fun publish(snapshot: CalibrationRunner.Snapshot) {
        val profile = snapshot.profile
        _state.update {
            it.copy(
                askLetter = snapshot.letter,
                askNoteName = snapshot.noteName,
                samplesDone = snapshot.samplesDone,
                samplesNeeded = snapshot.samplesNeeded,
                progress = snapshot.progress,
                feedback = profile?.let(::resultFeedback) ?: snapshot.feedback,
                level = snapshot.level,
                profile = profile,
                profileToSave = when {
                    profile == null -> null
                    profile.usableAsDefault -> profile
                    else -> it.profileToSave
                },
            )
        }
    }

    private fun resultFeedback(profile: CalibrationProfile): String =
        if (profile.usableAsDefault) {
            "Calibration ${profile.calibrationQuality.name.lowercase().replace('_', ' ')} — saved."
        } else {
            "This profile needs improvement, so it was not saved. Redo it, or keep it anyway."
        }

    private companion object {
        const val TICK_MS = 250L
        const val HEALTHY_RMS = 0.0008
        const val EXHAUSTED_FEEDBACK =
            "No microphone source is sending audio. Check microphone permission, then tap Start."
    }
}
