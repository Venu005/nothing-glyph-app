package app.backlit.ui.toys

import app.backlit.data.Settings

/** Which custom Charge animation a sheet edits. */
enum class ChargeAnimSlot(val title: String) {
    PLUG_IN("PLUG-IN ANIMATION"), DONE("DONE ANIMATION");

    fun current(s: Settings): String = if (this == PLUG_IN) s.chargePlugInAnim else s.chargeDoneAnim

    companion object {
        /** The settings update for choosing [id] in [slot]. The slot is fixed now: the update itself runs later, after the sheet closed. */
        fun update(slot: ChargeAnimSlot, id: String): (Settings) -> Settings =
            if (slot == PLUG_IN) { s -> s.copy(chargePlugInAnim = id) } else { s -> s.copy(chargeDoneAnim = id) }
    }
}
