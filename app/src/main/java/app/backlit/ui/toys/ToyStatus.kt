package app.backlit.ui.toys

import app.backlit.badge.BadgeMessage
import app.backlit.badge.BadgeText
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.pet.PetKind
import app.backlit.render.charge.ChargeStyles
import app.backlit.render.faces.Faces
import app.backlit.sand.Phase
import app.backlit.sand.TimerState
import app.backlit.ui.home.ToyId
import java.time.ZoneId

/** Values a status line needs from Android or a live engine; the page fills them in. */
data class StatusInputs(
    val battery: Int? = null,
    val micGranted: Boolean = true,
    val musicSupported: Boolean = true,
    val petBase: String? = null,
    val petMood: Int? = null,
    val drawingName: String? = null,
)

/** The one-line status under each toy's name on its page. */
object ToyStatus {
    fun line(id: ToyId, s: Settings, now: Long, zone: ZoneId, i: StatusInputs): String = when (id) {
        ToyId.CLOCK -> Faces.byId(s.faceId).label.uppercase()
        ToyId.MUSIC -> when {
            !i.musicSupported -> "PHONE (3) ONLY"
            !i.micGranted -> "NEEDS PERMISSION"
            else -> "LISTENING"
        }
        ToyId.CHARGE -> listOfNotNull(i.battery?.let { "$it %" }, ChargeStyles.byId(s.chargeStyle).label.uppercase()).joinToString(" · ")
        ToyId.CANVAS -> i.drawingName?.uppercase() ?: "NO DRAWING YET"
        ToyId.PET -> {
            val name = SettingsRepo.petNameFor(s, PetKind.byId(s.petKind)).uppercase()
            name + (i.petBase?.let { " IS $it" } ?: "") + (i.petMood?.let { " · MOOD $it" } ?: "")
        }
        ToyId.SAND -> {
            val st = TimerState.decode(s.sandTimer).tick(now)
            when (st.phase) {
                Phase.READY -> "READY · ${st.durationMs / TimerState.MIN} MIN"
                Phase.RUNNING -> "RUNNING · ${TimerState.clock(st.timeLeft(now))} LEFT"
                Phase.PAUSED -> "PAUSED · ${TimerState.clock(st.timeLeft(now))} LEFT"
                Phase.DONE -> "TIME'S UP"
            }
        }
        ToyId.BADGE -> {
            val list = BadgeMessage.decodeList(s.badgeMessages)
            val m = list[BadgeMessage.activeIndex(s.badgeActive, list.size)]
            BadgeText.scroll(m, s.badgeActiveSince.takeIf { it > 0 } ?: now, now, s.use24h, zone)
        }
    }
}
