package com.vijaychhetry.kidspiano.export

import android.content.Context
import com.vijaychhetry.kidspiano.core.diagnostics.SessionLogLine
import java.io.File

/** Append-only JSONL in filesDir. Rotates at 2000 lines. Never writes audio. */
class SessionLogStore(context: Context) {
    private val dir = File(context.filesDir, "logs").apply { mkdirs() }
    private val file = File(dir, "session.jsonl")
    private val rotated = File(dir, "session.1.jsonl")

    @Synchronized
    fun append(line: SessionLogLine) {
        if (lineCount() >= MAX_LINES) rotate()
        file.appendText(line.toJsonl() + "\n")
    }

    @Synchronized
    fun readAll(): String {
        val older = if (rotated.exists()) rotated.readText() else ""
        val current = if (file.exists()) file.readText() else ""
        return older + current
    }

    @Synchronized
    fun lineCount(): Int {
        if (!file.exists()) return 0
        return file.useLines { it.count() }
    }

    @Synchronized
    fun clear() {
        file.delete()
        rotated.delete()
    }

    private fun rotate() {
        if (rotated.exists()) rotated.delete()
        if (file.exists()) file.renameTo(rotated)
    }

    private companion object {
        const val MAX_LINES = 2000
    }
}
