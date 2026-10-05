package app.backlit.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.backlit.anim.ImportedAnimation
import app.backlit.anim.MuseumFormat
import java.io.File

/** Shares an animation as a Glyph Museum JSON file through the system share sheet. */
fun shareAnimation(context: Context, anim: ImportedAnimation, name: String) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val safe = name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim().ifBlank { "drawing" }
    val file = File(dir, "$safe.json").apply { writeText(MuseumFormat.toJson(anim)) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share $safe").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
