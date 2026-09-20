package com.vijaychhetry.kidspiano.core.learning

import com.vijaychhetry.kidspiano.core.notes.KEY_C4
import com.vijaychhetry.kidspiano.core.notes.letterOf
import com.vijaychhetry.kidspiano.core.notes.midiToNoteName
import com.vijaychhetry.kidspiano.core.notes.nextLessonSet

/**
 * Screen decisions for Home and Practice, kept off Compose so they can be
 * unit-tested. The widgets must consume these fields; do not re-derive them
 * in the UI.
 */
data class HomeChrome(
    val practiceEnabled: Boolean,
    val courseLabel: String?,
    val statusLine: String,
)

fun homeChrome(
    pianoReady: Boolean,
    courseLabel: String,
    setupSummary: String?,
): HomeChrome = HomeChrome(
    practiceEnabled = pianoReady,
    courseLabel = if (pianoReady) courseLabel else null,
    statusLine = if (pianoReady) setupSummary ?: Copy.READY else Copy.NOT_CALIBRATED,
)

enum class PracticeFooter { START, PAUSE, COMPLETE }

data class PracticeHero(
    val letter: String,
    val subtitle: String,
)

data class PracticeChrome(
    val counterText: String?,
    val doneIsButton: Boolean,
    val nextHint: String?,
    val playAgain: Boolean,
    val nextButtonLabel: String?,
    val footer: PracticeFooter,
    val hero: PracticeHero,
)

fun practiceHero(
    complete: Boolean,
    expectedMidi: Int?,
    letter: String,
    noteName: String,
): PracticeHero {
    if (complete) return PracticeHero("★", Copy.YOU_DID_IT)
    val shown = expectedMidi?.let { letterOf(it) } ?: letter
    val subtitle = when {
        expectedMidi == KEY_C4 -> Copy.MIDDLE_C
        expectedMidi != null -> midiToNoteName(expectedMidi)
        else -> noteName
    }
    return PracticeHero(shown, subtitle)
}

fun practiceChrome(
    complete: Boolean,
    running: Boolean,
    shortLabel: String,
    completedCount: Int,
    total: Int,
    nextLabel: String?,
    expectedMidi: Int?,
    letter: String,
    noteName: String,
): PracticeChrome {
    val footer = when {
        complete -> PracticeFooter.COMPLETE
        running -> PracticeFooter.PAUSE
        else -> PracticeFooter.START
    }
    return PracticeChrome(
        counterText = if (complete) null else Copy.progressCounter(shortLabel, completedCount, total),
        doneIsButton = complete,
        nextHint = when {
            !complete -> null
            nextLabel != null -> Copy.nextHint(nextLabel)
            else -> Copy.ALL_COURSES
        },
        playAgain = complete,
        nextButtonLabel = if (complete && nextLabel != null) Copy.NEXT else null,
        footer = footer,
        hero = practiceHero(complete, expectedMidi, letter, noteName),
    )
}

fun nextCourseLabel(setId: String): String? = nextLessonSet(setId)?.label
