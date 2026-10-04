package app.backlit.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/** Alert rules and the import index, stored as JSON strings in the app's settings DataStore. */
class AlertStore(private val store: DataStore<Preferences>) {

    val config: Flow<AlertConfig> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { decode(it) }

    suspend fun update(transform: (AlertConfig) -> AlertConfig) {
        store.edit { prefs -> encode(prefs, transform(decode(prefs))) }
    }

    private companion object {
        val CONTACTS = stringPreferencesKey("alert_contacts")
        val DEVICES = stringPreferencesKey("alert_devices")
        val IMPORTS = stringPreferencesKey("anim_imports")
        val json = Json { ignoreUnknownKeys = true }

        fun decode(p: Preferences) = AlertConfig(
            contacts = runCatching { json.decodeFromString<List<ContactRule>>(p[CONTACTS] ?: "[]") }.getOrDefault(emptyList()),
            devices = runCatching { json.decodeFromString<List<DeviceRule>>(p[DEVICES] ?: "[]") }.getOrDefault(emptyList()),
            imports = runCatching { json.decodeFromString<List<AnimIndexEntry>>(p[IMPORTS] ?: "[]") }.getOrDefault(emptyList()),
        )

        fun encode(p: MutablePreferences, c: AlertConfig) {
            p[CONTACTS] = json.encodeToString(c.contacts)
            p[DEVICES] = json.encodeToString(c.devices)
            p[IMPORTS] = json.encodeToString(c.imports)
        }
    }
}
