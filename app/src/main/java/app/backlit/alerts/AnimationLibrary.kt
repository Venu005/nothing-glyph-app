package app.backlit.alerts

import app.backlit.anim.BuiltInAnimations
import app.backlit.anim.GlyphAnimation
import app.backlit.anim.ImportedAnimation
import app.backlit.anim.MuseumFormat
import java.io.File

/** Imported animations stored as normalised Glyph Museum JSON files in [dir]. */
class AnimationLibrary(private val dir: File) {
    private val cache = mutableMapOf<String, ImportedAnimation>()

    fun save(anim: ImportedAnimation) {
        dir.mkdirs()
        file(anim.id).writeText(MuseumFormat.toJson(anim))
        cache[anim.id] = anim
    }

    fun load(entry: AnimIndexEntry): ImportedAnimation? {
        cache[entry.id]?.let { return it }
        val f = file(entry.id)
        if (!f.exists()) return null
        val result = runCatching { MuseumFormat.parse(f.readText(), entry.id, entry.name) }.getOrNull()
        return (result as? MuseumFormat.Result.Ok)?.animation?.also { cache[entry.id] = it }
    }

    fun delete(id: String) {
        cache.remove(id)
        file(id).delete()
    }

    fun resolve(id: String, imports: List<AnimIndexEntry>, fallback: String): GlyphAnimation =
        BuiltInAnimations.byId(id)
            ?: imports.firstOrNull { it.id == id }?.let { load(it) }
            ?: BuiltInAnimations.byId(fallback)
            ?: BuiltInAnimations.all.first()

    /** Only an imported animation (no built-in fallback); null if [id] isn't a known, loadable import. */
    fun importedOnly(id: String, imports: List<AnimIndexEntry>): GlyphAnimation? =
        if (!id.startsWith("import:")) null else imports.firstOrNull { it.id == id }?.let { load(it) }

    private fun file(id: String) = File(dir, id.removePrefix("import:").filter { it.isLetterOrDigit() || it == '-' } + ".json")
}
