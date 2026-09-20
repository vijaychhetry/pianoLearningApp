package com.vijaychhetry.kidspiano.core.notes

const val A4_HZ = 440.0
const val A4_MIDI = 69
const val MIN_PIANO_MIDI = 21
const val MAX_PIANO_MIDI = 108

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

fun midiToFreq(midi: Int, a4Hz: Double = A4_HZ): Double =
    a4Hz * Math.pow(2.0, (midi - A4_MIDI) / 12.0)

fun freqToMidiFloat(freq: Double, a4Hz: Double = A4_HZ): Double =
    A4_MIDI + 12.0 * (Math.log(freq / a4Hz) / Math.log(2.0))

fun freqToMidi(freq: Double, a4Hz: Double = A4_HZ): Int = Math.round(freqToMidiFloat(freq, a4Hz)).toInt()

fun centsOff(freq: Double, midi: Int, a4Hz: Double = A4_HZ): Double {
    val target = midiToFreq(midi, a4Hz)
    return 1200.0 * (Math.log(freq / target) / Math.log(2.0))
}

fun midiToNoteName(midi: Int): String {
    val pc = ((midi % 12) + 12) % 12
    val octave = midi / 12 - 1
    return "${NOTE_NAMES[pc]}$octave"
}

fun pitchClass(midi: Int): Int = ((midi % 12) + 12) % 12
