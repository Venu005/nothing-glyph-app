package app.backlit.data

import android.content.Context

object LocationRefresher {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Refreshes approximate coordinates at most once a day, only while the app is open. */
    suspend fun refreshIfStale(context: Context, repo: SettingsRepo, settings: Settings, nowMillis: Long) {
        if (settings.locationMode != LocationMode.APPROXIMATE) return
        if (nowMillis - settings.locationUpdatedAt < DAY_MS) return
        val src = LocationSource(context)
        if (!src.hasPermission()) return
        val c = src.current() ?: return
        repo.update { it.copy(lat = c.lat, lon = c.lon, locationUpdatedAt = nowMillis) }
    }
}
