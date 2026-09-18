package com.vijaychhetry.kidspiano.core.learning

/**
 * A tiny sum the child cannot tap through by accident. Grown-up tools
 * (Calibrate, Audio Lab) sit behind this (spec §39a).
 */
data class ParentGate(val a: Int = 2, val b: Int = 5) {
    val prompt: String get() = "What is $a + $b?"

    fun accepts(answer: String): Boolean = answer.trim().toIntOrNull() == a + b
}
