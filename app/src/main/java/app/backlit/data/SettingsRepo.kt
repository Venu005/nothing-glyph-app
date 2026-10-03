package app.backlit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepo(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    companion object {
        private val FACE = stringPreferencesKey("face_id")
        private val SECOND = booleanPreferencesKey("second_hand")
        private val BRIGHTNESS = intPreferencesKey("brightness")
        private val USE24 = booleanPreferencesKey("use_24h")
        private val LOC_MODE = stringPreferencesKey("location_mode")
        private val LAT = doublePreferencesKey("lat")
        private val LON = doublePreferencesKey("lon")
        private val PLACE = stringPreferencesKey("place_name")
        private val LOC_AT = longPreferencesKey("location_updated_at")
        private val BOUND = booleanPreferencesKey("toy_ever_bound")
        private val MUSIC_STYLE = stringPreferencesKey("music_style")
        private val MUSIC_SENS = stringPreferencesKey("music_sensitivity")

        fun get(context: Context): SettingsRepo = SettingsRepo(context.applicationContext.settingsDataStore)

        fun parseMode(s: String?): LocationMode =
            LocationMode.entries.firstOrNull { it.name == s } ?: LocationMode.FIXED

        fun parseSensitivity(s: String?): Sensitivity =
            Sensitivity.entries.firstOrNull { it.name == s } ?: Sensitivity.MED

        private fun Preferences.toSettings(): Settings {
            val d = Settings()
            return Settings(
                faceId = this[FACE] ?: d.faceId,
                secondHand = this[SECOND] ?: d.secondHand,
                brightness = this[BRIGHTNESS] ?: d.brightness,
                use24h = this[USE24] ?: d.use24h,
                locationMode = parseMode(this[LOC_MODE]),
                lat = this[LAT],
                lon = this[LON],
                placeName = this[PLACE],
                locationUpdatedAt = this[LOC_AT] ?: d.locationUpdatedAt,
                toyEverBound = this[BOUND] ?: d.toyEverBound,
                musicStyle = this[MUSIC_STYLE] ?: d.musicStyle,
                musicSensitivity = parseSensitivity(this[MUSIC_SENS]),
            )
        }

        private fun MutablePreferences.write(s: Settings) {
            this[FACE] = s.faceId
            this[SECOND] = s.secondHand
            this[BRIGHTNESS] = s.brightness
            this[USE24] = s.use24h
            this[LOC_MODE] = s.locationMode.name
            if (s.lat != null) this[LAT] = s.lat else remove(LAT)
            if (s.lon != null) this[LON] = s.lon else remove(LON)
            if (s.placeName != null) this[PLACE] = s.placeName else remove(PLACE)
            this[LOC_AT] = s.locationUpdatedAt
            this[BOUND] = s.toyEverBound
            this[MUSIC_STYLE] = s.musicStyle
            this[MUSIC_SENS] = s.musicSensitivity.name
        }
    }
}
