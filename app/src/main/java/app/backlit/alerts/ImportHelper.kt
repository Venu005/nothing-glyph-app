package app.backlit.alerts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Reads a Glyph Museum JSON file from [uri], imports it, and returns a message for the user. */
fun importFromUri(context: Context, runtime: AlertsRuntime, uri: Uri, deviceSize: Int): String {
    val resolver = context.contentResolver
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Imported animation"
    val text = runCatching {
        resolver.openInputStream(uri)?.use { s -> s.readNBytes(MAX_BYTES + 1) }
    }.getOrNull()?.takeIf { it.size <= MAX_BYTES }?.toString(Charsets.UTF_8)
        ?: return BAD
    return when (val r = runtime.importJson(text, name)) {
        ImportOutcome.Invalid -> BAD
        is ImportOutcome.Ok -> {
            val note = when {
                r.sourceSize == deviceSize -> ""
                r.sourceSize == 25 -> " · made for Phone (3), scaled for 4a Pro"
                else -> " · made for 4a Pro, scaled for Phone (3)"
            }
            "Imported \"${r.name}\"$note"
        }
    }
}

private const val MAX_BYTES = 4 * 1024 * 1024
private const val BAD = "This file isn't a Glyph Museum animation."
