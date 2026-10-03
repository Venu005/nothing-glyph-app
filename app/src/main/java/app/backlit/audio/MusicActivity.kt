package app.backlit.audio

import android.content.Context
import android.media.AudioManager

/** Whether any app is playing music. Needs no permission. */
class MusicActivity(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun isPlaying(): Boolean = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
}
