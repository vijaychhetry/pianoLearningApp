package com.vijaychhetry.kidspiano.lesson

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vijaychhetry.kidspiano.BuildConfig
import com.vijaychhetry.kidspiano.core.audio.AudioRecordInput
import com.vijaychhetry.kidspiano.core.calibration.CalibrationProfile
import com.vijaychhetry.kidspiano.core.common.Config
import com.vijaychhetry.kidspiano.core.common.RecognitionStatus
import com.vijaychhetry.kidspiano.core.diagnostics.SessionLogLine
import com.vijaychhetry.kidspiano.core.learning.Copy
import com.vijaychhetry.kidspiano.core.learning.LessonCue
import com.vijaychhetry.kidspiano.core.learning.LessonSession
import com.vijaychhetry.kidspiano.core.learning.LessonSnapshot
import com.vijaychhetry.kidspiano.core.learning.lessonMayStart
import com.vijaychhetry.kidspiano.core.learning.lessonSessionFor
import com.vijaychhetry.kidspiano.core.notes.defaultLessonMidi
import com.vijaychhetry.kidspiano.core.notes.lessonSets
import com.vijaychhetry.kidspiano.core.pitch.MicSourceCycler
import com.vijaychhetry.kidspiano.export.SessionLogStore
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

data class LessonUiState(
    val ready: Boolean = false,
    val running: Boolean = false,
    val error: String? = null,
    val letter: String = "C",
    val noteName: String = "C4",
    val feedback: String = "Ask a grown-up to set up the piano first.",
    val cue: LessonCue = LessonCue.LISTEN,
    val status: RecognitionStatus = RecognitionStatus.NO_SIGNAL,
    val completedCount: Int = 0,
    val total: Int = 5,
    val progress: Float = 0f,
    val level: Double = 0.0,
    val complete: Boolean = false,
    val sourceLabel: String = "—",
    val expectedMidi: Int? = 60,
    val heardMidi: Int? = null,
    val line2: String? = null,
    val lessonSetLabel: String = "C4–G4 (first)",
)

class LessonViewModel(app: Application) : AndroidViewModel(app) {
    private val input = AudioRecordInput()
    private val sources = MicSourceCycler(AudioRecordInput.SOURCE_ORDER)
    private val logStore = SessionLogStore(app)
    private val control = Mutex()
    private var session: LessonSession? = null
    private var notes: List<Int> = defaultLessonMidi()
    private var lastLoggedOnset: Long? = null
    private var lastHeardMs = 0L
    private var sessionId: String = "s-${System.currentTimeMillis()}"
    private var collectJob: Job? = null
    private var tickJob: Job? = null
    private var wantRunning = false

    private val _state = MutableStateFlow(LessonUiState())
    val state: StateFlow<LessonUiState> = _state

    fun useProfile(profile: CalibrationProfile?, lessonNotes: List<Int> = defaultLessonMidi()) {
        if (session != null && notes == lessonNotes && _state.value.ready) return
        notes = lessonNotes
        if (!lessonMayStart(profile) || profile == null) {
            session = null
            _state.value = LessonUiState()
            return
        }
        val next = lessonSessionFor(profile, notes)
        session = next
        sessionId = "s-${System.currentTimeMillis()}"
        lastLoggedOnset = null
        _state.value = next.snapshot().toUi(ready = true, running = false)
    }

    fun start() {
        if (session == null || wantRunning) return
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

    fun playAgain() {
        viewModelScope.launch {
            control.withLock {
                session?.restart()
                if (wantRunning) {
                    session?.snapshot()?.let { publish(it, running = true) }
                } else {
                    wantRunning = true
                    sources.reset()
                    beginCapture()
                    startTicker()
                }
            }
        }
    }

    fun switchSource() {
        viewModelScope.launch {
            control.withLock {
                val next = sources.forceAdvance()
                session?.onSourceSwitched()
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
                    val now = System.currentTimeMillis()
                    session?.onTick(now)?.let { publish(it, running = true) }
                    if (session?.sourceLooksDead == true) rotateAfterSilence()
                    if (lastHeardMs > 0L && now - lastHeardMs > Config.MIC_RELEASE_MS) {
                        wantRunning = false
                        stopTicker()
                        endCapture()
                        _state.update { it.copy(running = false, feedback = Copy.MIC_PAUSED) }
                    }
                }
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
    }

    private suspend fun beginCapture() {
        val active = session ?: return
        while (true) {
            try {
                withContext(Dispatchers.IO) { input.start(sources.current) }
                break
            } catch (e: CancellationException) {
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
        lastHeardMs = System.currentTimeMillis()
        active.begin(lastHeardMs)
        _state.update {
            it.copy(
                running = true,
                error = null,
                sourceLabel = AudioRecordInput.sourceName(input.appliedSource),
            )
        }
        publish(active.snapshot(), running = true)
        collectJob = viewModelScope.launch {
            input.audioFrames().collect { frame ->
                val snapshot = active.onFrame(frame)
                if (snapshot.level > HEALTHY_RMS) {
                    sources.reset()
                    lastHeardMs = System.currentTimeMillis()
                }
                logIfNeeded(snapshot)
                publish(snapshot, running = true)
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
        session?.onSourceSwitched()
        if (next == null) {
            wantRunning = false
            stopTicker()
            endCapture()
            _state.update {
                it.copy(
                    running = false,
                    feedback = "No microphone is sending audio. Check permission, then tap Start.",
                )
            }
            return
        }
        endCapture()
        beginCapture()
    }

    private fun publish(snapshot: LessonSnapshot, running: Boolean) {
        _state.value = snapshot.toUi(ready = true, running = running, error = _state.value.error)
    }

    private fun LessonSnapshot.toUi(
        ready: Boolean,
        running: Boolean,
        error: String? = null,
    ) = LessonUiState(
        ready = ready,
        running = running,
        error = error,
        letter = letter,
        noteName = noteName,
        feedback = feedback,
        cue = cue,
        status = status,
        completedCount = completedCount,
        total = total,
        progress = progress,
        level = level,
        complete = complete,
        sourceLabel = _state.value.sourceLabel,
        expectedMidi = expectedMidi,
        heardMidi = heardMidi,
        line2 = line2,
        lessonSetLabel = lessonSets().firstOrNull { it.second == notes }?.first
            ?: "C4–G4 (first)",
    )

    private fun logIfNeeded(snapshot: LessonSnapshot) {
        val verdict = snapshot.verdict ?: return
        val onset = verdict.press?.onsetNanos
        if (onset != null && onset == lastLoggedOnset) return
        if (onset == null && lastLoggedOnset == Long.MIN_VALUE) return
        lastLoggedOnset = onset ?: Long.MIN_VALUE
        viewModelScope.launch(Dispatchers.IO) {
            logStore.append(
                SessionLogLine(
                    ts = System.currentTimeMillis(),
                    sessionId = sessionId,
                    appVersion = BuildConfig.VERSION_NAME,
                    activity = "learn",
                    levelId = 1,
                    promptIndex = snapshot.completedCount,
                    expectedMidi = if (verdict is com.vijaychhetry.kidspiano.core.common.Verdict.Correct) {
                        notes.getOrNull(snapshot.completedCount - 1) ?: snapshot.expectedMidi
                    } else {
                        snapshot.expectedMidi
                    },
                    verdict = verdict,
                ),
            )
        }
    }

    private companion object {
        const val TICK_MS = 250L
        const val HEALTHY_RMS = 0.0008
    }
}
