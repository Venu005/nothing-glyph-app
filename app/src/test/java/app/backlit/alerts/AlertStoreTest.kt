package app.backlit.alerts

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AlertStoreTest {

    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private fun store() = AlertStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { tmp.root.resolve("a.preferences_pb") }))

    @After fun tearDown() = scope.cancel()

    @Test
    fun emptyByDefault() = runBlocking { assertEquals(AlertConfig(), store().config.first()) }

    @Test
    fun roundTripsRulesAndImports() = runBlocking {
        val s = store()
        val cfg = AlertConfig(
            contacts = listOf(ContactRule("Mom", "builtin:heart"), ContactRule("Dad \"D\"", "import:x")),
            devices = listOf(DeviceRule("AA:BB:CC:DD:EE:FF", "Buds", "builtin:link")),
            imports = listOf(AnimIndexEntry("import:x", "Fireworks", 1)),
        )
        s.update { cfg }
        assertEquals(cfg, s.config.first())
    }

    @Test
    fun oldImportIndexWithoutKindStillDecodes() = runBlocking {
        val prefs = PreferenceDataStoreFactory.create(scope = scope, produceFile = { tmp.root.resolve("old.preferences_pb") })
        prefs.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("anim_imports")] = """[{"id":"import:a","name":"Old","sourceV":1}]""" }
        val c = AlertStore(prefs).config.first()
        assertEquals(listOf(AnimIndexEntry("import:a", "Old", 1, kind = "import", fps = 0)), c.imports)
        val s = AlertStore(prefs)
        s.update { it.copy(imports = it.imports + AnimIndexEntry("import:d", "Mine", 4, kind = KIND_DRAWING, fps = 12)) }
        assertEquals(AnimIndexEntry("import:d", "Mine", 4, KIND_DRAWING, 12), s.config.first().imports.last())
    }
}
