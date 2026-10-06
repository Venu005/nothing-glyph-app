package app.backlit.ui.toys

import app.backlit.badge.BadgeMessage
import app.backlit.badge.BadgePreviewAnimation
import app.backlit.badge.BadgeText
import app.backlit.charge.ChargePreviewAnimation
import app.backlit.charge.Moment
import app.backlit.data.Settings
import app.backlit.pet.Base
import app.backlit.pet.PetKind
import app.backlit.pet.PetPreviewAnimation
import app.backlit.render.charge.ChargeStyles
import app.backlit.sand.SandPreviewAnimation
import app.backlit.ui.home.ToyId
import java.time.ZoneId

/** The bottom-bar action on a toy page. */
sealed interface ToyAction {
    /** Play [previewId] on the Glyph for [ms] (null id: the page plays it itself, e.g. the Canvas drawing). */
    data class ShowOnGlyph(val previewId: String?, val ms: Long) : ToyAction
    data object OpenGlyphToys : ToyAction
    data object TurnOn : ToyAction
    data object NewDrawing : ToyAction

    companion object {
        /** null = no bar (unsupported phones, where nothing can play and Glyph Toys doesn't exist). */
        fun of(id: ToyId, setUp: Boolean, supported: Boolean, hasDrawing: Boolean): ToyAction? = when {
            !supported -> if (id == ToyId.CANVAS && !hasDrawing) NewDrawing else null
            !setUp -> TurnOn
            id == ToyId.CLOCK || id == ToyId.MUSIC -> OpenGlyphToys
            id == ToyId.CANVAS && !hasDrawing -> NewDrawing
            else -> ShowOnGlyph(null, 0)
        }

        fun previewFor(id: ToyId, s: Settings, now: Long, zone: ZoneId): ShowOnGlyph = when (id) {
            ToyId.CHARGE -> ShowOnGlyph(ChargePreviewAnimation.idFor(ChargeStyles.byId(s.chargeStyle).id, Moment.PLUG_IN), ChargePreviewAnimation.durationMs(Moment.PLUG_IN))
            ToyId.PET -> ShowOnGlyph(PetPreviewAnimation.idFor(PetKind.byId(s.petKind), Base.HAPPY), 3000L)
            ToyId.SAND -> ShowOnGlyph(SandPreviewAnimation.RUNNING_ID, 4000L)
            ToyId.BADGE -> {
                val list = BadgeMessage.decodeList(s.badgeMessages)
                val m = list[BadgeMessage.activeIndex(s.badgeActive, list.size)]
                val text = BadgeText.scroll(m, s.badgeActiveSince.takeIf { it > 0 } ?: now, now, s.use24h, zone)
                ShowOnGlyph(BadgePreviewAnimation.idFor(m.icon, text), 6000L)
            }
            else -> ShowOnGlyph(null, 4000L)
        }
    }
}
