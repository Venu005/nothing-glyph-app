package app.backlit.ui.home

import app.backlit.anim.GlyphAnimation
import app.backlit.anim.Heartbeat
import app.backlit.audio.AudioFrame
import app.backlit.badge.BadgeArt
import app.backlit.badge.BadgeMessage
import app.backlit.badge.BadgeText
import app.backlit.data.Settings
import app.backlit.pet.Base
import app.backlit.pet.PetArt
import app.backlit.pet.PetKind
import app.backlit.pet.Pose
import app.backlit.render.FaceContext
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeStyles
import app.backlit.render.faces.Faces
import app.backlit.render.viz.MirrorBars
import app.backlit.sand.SandPreviewAnimation
import app.backlit.studio.CanvasHint
import java.time.Instant
import java.time.ZoneId
import kotlin.math.sin

/** The live dot previews on the Home cards, drawn with each toy's real art from the current settings. */
object ToyThumbs {
    private val sand by lazy { SandPreviewAnimation.parse(SandPreviewAnimation.RUNNING_ID)!! }

    fun frame(id: ToyId, s: Settings, size: Int, nowMs: Long, zone: ZoneId, canvas: GlyphAnimation? = null): PixelGrid = when (id) {
        ToyId.CLOCK -> {
            val t = Instant.ofEpochMilli(nowMs).atZone(zone)
            Faces.byId(s.faceId).render(FaceContext(t.hour, t.minute, t.second, size, Mode.ACTIVE, s.faceOptions))
        }
        ToyId.MUSIC -> MirrorBars(size).apply {
            val bands = FloatArray(8) { i -> (0.45 + 0.45 * sin(nowMs / 260.0 + i * 0.9)).toFloat() }
            update(AudioFrame(bands, 0.5f, 0.4f), 50)
        }.render()
        ToyId.CHARGE -> ChargeStyles.byId(s.chargeStyle).charging(size, 62, nowMs)
        ToyId.CANVAS -> canvas?.frame(size, nowMs) ?: CanvasHint.frame(size)
        ToyId.PET -> PetArt.frame(PetKind.byId(s.petKind), size, Pose(Base.CONTENT, null, 0, 0, 0, 0, 62), nowMs)
        ToyId.SAND -> sand.frame(size, nowMs)
        ToyId.BADGE -> {
            val list = BadgeMessage.decodeList(s.badgeMessages)
            val m = list[BadgeMessage.activeIndex(s.badgeActive, list.size)]
            val since = s.badgeActiveSince.takeIf { it > 0 } ?: nowMs
            BadgeArt.frame(size, m, BadgeText.scroll(m, since, nowMs, s.use24h, zone), nowMs, BadgeArt.FLASH_MS, BadgeText.spanText(m, s.use24h))
        }
    }

    fun studio(size: Int, nowMs: Long): PixelGrid = CanvasHint.frame(size)

    fun alerts(size: Int, nowMs: Long): PixelGrid = Heartbeat.frame(size, nowMs)
}
