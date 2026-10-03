package app.backlit.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

fun hasAudioPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
