package app.backlit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
        private val CHARGE_STYLE = stringPreferencesKey("charge_style")
        private val CHARGE_TARGET = intPreferencesKey("charge_target")
        private val CHARGE_PLUG_ANIM = stringPreferencesKey("charge_plugin_anim")
        private val CHARGE_DONE_ANIM = stringPreferencesKey("charge_done_anim")
        private val CHARGE_BOUND = booleanPreferencesKey("charge_toy_ever_bound")
        private val CANVAS_DRAWING = stringPreferencesKey("canvas_drawing_id")
        private val CANVAS_BOUND = booleanPreferencesKey("canvas_toy_ever_bound")
        private val PET_NAME = stringPreferencesKey("pet_name")
        private val PET_MOOD = floatPreferencesKey("pet_mood")
        private val PET_MOOD_AT = longPreferencesKey("pet_mood_at")
        private val PET_SLEEP_START = intPreferencesKey("pet_sleep_start")
        private val PET_SLEEP_END = intPreferencesKey("pet_sleep_end")
        private val PET_BOUND = booleanPreferencesKey("pet_toy_ever_bound")
        private val PET_KIND = stringPreferencesKey("pet_kind")
        private val PET_NAMES = stringPreferencesKey("pet_names")
        private val SAND_PRESETS = stringPreferencesKey("sand_presets")
        private val SAND_ALERT = stringPreferencesKey("sand_alert")
        private val SAND_EXACT = booleanPreferencesKey("sand_exact")
        private val SAND_TIMER = stringPreferencesKey("sand_timer")
        private val SAND_BOUND = booleanPreferencesKey("sand_toy_ever_bound")
        private val BADGE_MESSAGES = stringPreferencesKey("badge_messages")
        private val BADGE_ACTIVE = intPreferencesKey("badge_active")
        private val BADGE_ACTIVE_SINCE = longPreferencesKey("badge_active_since")
        private val BADGE_BOUND = booleanPreferencesKey("badge_toy_ever_bound")
        private val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
        private val CLOCK_BOUND = booleanPreferencesKey("clock_toy_ever_bound")
        private val MUSIC_BOUND = booleanPreferencesKey("music_toy_ever_bound")
        /** Set on the first write after the clock/music flag split; until then a used clock or music counts as on. */
        private val TOY_FLAGS_V2 = booleanPreferencesKey("toy_flags_v2")
        private val namesJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        /** The name to show for [kind]: the ghost keeps the original petName; the others live in petNames. */
        fun petNameFor(s: Settings, kind: app.backlit.pet.PetKind): String =
            if (kind == app.backlit.pet.PetKind.GHOST) cleanPetName(s.petName, kind.defaultName)
            else s.petNames[kind.id]?.let { cleanPetName(it, kind.defaultName) } ?: kind.defaultName

        fun withPetName(s: Settings, kind: app.backlit.pet.PetKind, name: String): Settings {
            val clean = cleanPetName(name, kind.defaultName)
            return if (kind == app.backlit.pet.PetKind.GHOST) s.copy(petName = clean) else s.copy(petNames = s.petNames + (kind.id to clean))
        }

        fun cleanPetName(s: String, fallback: String = "Boo"): String = s.trim().take(12).trim().ifBlank { fallback }

        /** What to save while the user edits the name; null while it's blank (keep the stored name). */
        fun petNameToSave(s: String): String? = s.trim().take(12).trim().ifBlank { null }

        /** 50..100 in steps of 5 (nearest step, halves round up). */
        fun clampTarget(v: Int): Int = (((v.coerceIn(50, 100) + 2) / 5) * 5).coerceIn(50, 100)

        /** 1..99, no duplicates, sorted, at most 8; empty → the defaults. */
        fun cleanPresets(list: List<Int>): List<Int> =
            list.filter { it in 1..99 }.distinct().sorted().take(8).ifEmpty { listOf(1, 3, 5, 10, 25) }

        fun parseSandAlert(s: String?): String = if (s in setOf("glyph", "vibrate", "chime")) s!! else "vibrate"

        fun get(context: Context): SettingsRepo = SettingsRepo(context.applicationContext.settingsDataStore)

        fun parseMode(s: String?): LocationMode =
            LocationMode.entries.firstOrNull { it.name == s } ?: LocationMode.FIXED

        fun parseSensitivity(s: String?): Sensitivity =
            Sensitivity.entries.firstOrNull { it.name == s } ?: Sensitivity.MED

        /** Before the split, one flag covered both the clock and music toys: honour it once for either. */
        private fun Preferences.legacyBound(): Boolean = this[TOY_FLAGS_V2] != true && this[BOUND] == true

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
                chargeStyle = this[CHARGE_STYLE] ?: d.chargeStyle,
                chargeTarget = clampTarget(this[CHARGE_TARGET] ?: d.chargeTarget),
                chargePlugInAnim = this[CHARGE_PLUG_ANIM] ?: d.chargePlugInAnim,
                chargeDoneAnim = this[CHARGE_DONE_ANIM] ?: d.chargeDoneAnim,
                chargeToyEverBound = this[CHARGE_BOUND] ?: d.chargeToyEverBound,
                canvasDrawingId = this[CANVAS_DRAWING] ?: d.canvasDrawingId,
                canvasToyEverBound = this[CANVAS_BOUND] ?: d.canvasToyEverBound,
                petName = cleanPetName(this[PET_NAME] ?: d.petName),
                petMood = (this[PET_MOOD] ?: d.petMood).coerceIn(0f, 100f),
                petMoodAt = this[PET_MOOD_AT] ?: d.petMoodAt,
                petSleepStart = (this[PET_SLEEP_START] ?: d.petSleepStart).coerceIn(0, 23),
                petSleepEnd = (this[PET_SLEEP_END] ?: d.petSleepEnd).coerceIn(0, 23),
                petToyEverBound = this[PET_BOUND] ?: d.petToyEverBound,
                petKind = app.backlit.pet.PetKind.byId(this[PET_KIND] ?: d.petKind).id,
                petNames = runCatching { namesJson.decodeFromString<Map<String, String>>(this[PET_NAMES] ?: "{}") }.getOrDefault(emptyMap()),
                sandPresets = cleanPresets(runCatching { namesJson.decodeFromString<List<Int>>(this[SAND_PRESETS] ?: "") }.getOrDefault(d.sandPresets)),
                sandAlert = parseSandAlert(this[SAND_ALERT]),
                sandExact = this[SAND_EXACT] ?: d.sandExact,
                sandTimer = this[SAND_TIMER] ?: d.sandTimer,
                sandToyEverBound = this[SAND_BOUND] ?: d.sandToyEverBound,
                badgeMessages = this[BADGE_MESSAGES] ?: d.badgeMessages,
                badgeActive = (this[BADGE_ACTIVE] ?: d.badgeActive).coerceAtLeast(0),
                badgeActiveSince = this[BADGE_ACTIVE_SINCE] ?: d.badgeActiveSince,
                badgeToyEverBound = this[BADGE_BOUND] ?: d.badgeToyEverBound,
                welcomeSeen = this[WELCOME_SEEN] ?: d.welcomeSeen,
                clockToyEverBound = (this[CLOCK_BOUND] ?: d.clockToyEverBound) || legacyBound(),
                musicToyEverBound = (this[MUSIC_BOUND] ?: d.musicToyEverBound) || legacyBound(),
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
            this[CHARGE_STYLE] = s.chargeStyle
            this[CHARGE_TARGET] = s.chargeTarget
            this[CHARGE_PLUG_ANIM] = s.chargePlugInAnim
            this[CHARGE_DONE_ANIM] = s.chargeDoneAnim
            this[CHARGE_BOUND] = s.chargeToyEverBound
            this[CANVAS_DRAWING] = s.canvasDrawingId
            this[CANVAS_BOUND] = s.canvasToyEverBound
            this[PET_NAME] = s.petName
            this[PET_MOOD] = s.petMood
            this[PET_MOOD_AT] = s.petMoodAt
            this[PET_SLEEP_START] = s.petSleepStart
            this[PET_SLEEP_END] = s.petSleepEnd
            this[PET_BOUND] = s.petToyEverBound
            this[PET_KIND] = s.petKind
            this[PET_NAMES] = namesJson.encodeToString(s.petNames)
            this[SAND_PRESETS] = namesJson.encodeToString(s.sandPresets)
            this[SAND_ALERT] = s.sandAlert
            this[SAND_EXACT] = s.sandExact
            this[SAND_TIMER] = s.sandTimer
            this[SAND_BOUND] = s.sandToyEverBound
            this[BADGE_MESSAGES] = s.badgeMessages
            this[BADGE_ACTIVE] = s.badgeActive
            this[BADGE_ACTIVE_SINCE] = s.badgeActiveSince
            this[BADGE_BOUND] = s.badgeToyEverBound
            this[WELCOME_SEEN] = s.welcomeSeen
            this[CLOCK_BOUND] = s.clockToyEverBound
            this[MUSIC_BOUND] = s.musicToyEverBound
            this[TOY_FLAGS_V2] = true
        }
    }
}
