package com.vijaychhetry.kidspiano.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.vijaychhetry.kidspiano.core.notes.FIVE_KEYS
import com.vijaychhetry.kidspiano.core.notes.displayNoteName
import com.vijaychhetry.kidspiano.core.notes.keyColorHex
import com.vijaychhetry.kidspiano.core.notes.letterOf
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object FileExporter {
    private val stamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(ZoneOffset.UTC)

    fun shareText(
        context: Context,
        basename: String,
        body: String,
        mime: String = "application/json",
    ) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${stamp.format(Instant.now())}-$basename")
        file.writeText(body)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, basename)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share $basename"))
    }

    fun shareStickerPdf(context: Context) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${stamp.format(Instant.now())}-stickers.pdf")
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val title = Paint().apply {
            textSize = 22f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val body = Paint().apply {
            textSize = 14f
            color = Color.BLACK
        }
        canvas.drawText("Kids Piano stickers — C4 to G4", 48f, 64f, title)
        canvas.drawText("Yamaha PSR-F52. Middle C is C4, the third C from the left.", 48f, 92f, body)
        FIVE_KEYS.forEachIndexed { i, midi ->
            val y = 140f + i * 90f
            val fillColor = Color.parseColor(keyColorHex(midi))
            val fill = Paint().apply { this.color = fillColor }
            canvas.drawRect(48f, y, 200f, y + 72f, fill)
            val ink = Paint().apply {
                textSize = 28f
                isFakeBoldText = true
                this.color = Color.WHITE
            }
            canvas.drawText(letterOf(midi), 70f, y + 46f, ink)
            canvas.drawText(displayNoteName(midi), 220f, y + 46f, body)
        }
        canvas.drawText("Colour is never the only signal. The letter is on the sticker.", 48f, 640f, body)
        pdf.finishPage(page)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share stickers PDF"))
    }
}
