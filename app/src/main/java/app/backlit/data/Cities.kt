package app.backlit.data

import android.content.res.AssetManager

data class City(val name: String, val country: String, val lat: Double, val lon: Double) {
    val display: String get() = "$name, $country"
}

object Cities {

    fun parse(lines: Sequence<String>): List<City> = lines.mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 4) return@mapNotNull null
        val lat = p[2].toDoubleOrNull() ?: return@mapNotNull null
        val lon = p[3].toDoubleOrNull() ?: return@mapNotNull null
        City(p[0], p[1], lat, lon)
    }.toList()

    /** Case-insensitive; names starting with the query first, then names containing it. */
    fun search(all: List<City>, query: String, limit: Int = 20): List<City> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val prefix = all.filter { it.name.lowercase().startsWith(q) }
        val contains = all.filter { !it.name.lowercase().startsWith(q) && it.name.lowercase().contains(q) }
        return (prefix + contains).take(limit)
    }

    fun load(assets: AssetManager): List<City> =
        assets.open("cities.tsv").bufferedReader().useLines { parse(it) }
}
