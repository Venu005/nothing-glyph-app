package app.backlit.badge

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One status message: text (font characters only), an icon, and plain / countdown / until-a-time. */
@Serializable
data class BadgeMessage(
    val text: String = "",
    val icon: String = "heart",
    val kind: Kind = Kind.PLAIN,
    val minutes: Int = 5,
    val untilMinuteOfDay: Int = 15 * 60,
) {
    @Serializable
    enum class Kind { PLAIN, COUNTDOWN, UNTIL }

    fun clean(): BadgeMessage = copy(
        text = cleanText(text).trim(),
        icon = if (icon in BadgeIcons.ids) icon else "heart",
        minutes = minutes.coerceIn(1, 180),
        untilMinuteOfDay = untilMinuteOfDay.coerceIn(0, 1439),
    )

    companion object {
        const val MAX_TEXT = 32
        const val MAX_MESSAGES = 8
        val ALLOWED: Set<Char> = (('A'..'Z') + ('0'..'9') + listOf(' ', ':', '!', '?', '.', '\'', '-')).toSet()

        val STARTERS = listOf(
            BadgeMessage("IN A MEETING", "laptop"),
            BadgeMessage("BACK IN", "coffee", Kind.COUNTDOWN, minutes = 5),
            BadgeMessage("ON A CALL", "phone"),
            BadgeMessage("DO NOT DISTURB", "moon"),
            BadgeMessage("THANK YOU", "heart"),
        )

        private val json = Json { ignoreUnknownKeys = true }

        /** For typing: uppercase, font characters only, single spaces, no leading space, 32 max (a trailing space stays). */
        fun cleanText(s: String): String =
            s.uppercase().filter { it in ALLOWED }.replace(Regex(" {2,}"), " ").trimStart().take(MAX_TEXT)

        fun cleanList(list: List<BadgeMessage>): List<BadgeMessage> =
            list.map { it.clean() }.take(MAX_MESSAGES).ifEmpty { STARTERS }

        fun decodeList(s: String): List<BadgeMessage> =
            if (s.isBlank()) STARTERS
            else runCatching { cleanList(json.decodeFromString<List<BadgeMessage>>(s)) }.getOrDefault(STARTERS)

        fun encodeList(list: List<BadgeMessage>): String = json.encodeToString(list)

        fun activeIndex(stored: Int, size: Int): Int = stored.coerceIn(0, (size - 1).coerceAtLeast(0))

        /** The current message's index after moving the message at [from] to [to] (a swap of neighbours). */
        fun moveActive(active: Int, from: Int, to: Int): Int = when (active) {
            from -> to
            to -> from
            else -> active
        }

        /** The current index after deleting [deleted]: the next message takes over if the current one went. */
        fun afterDelete(active: Int, deleted: Int, sizeBefore: Int): Int = when {
            deleted < active -> active - 1
            deleted > active -> active
            deleted >= sizeBefore - 1 -> 0
            else -> deleted
        }
    }
}
