package app.backlit.glyph

import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph

enum class DeviceProfile(val size: Int, val aodOnly: Boolean, val label: String) {
    PHONE_3(25, aodOnly = false, label = "PHONE (3)"),
    PHONE_4A_PRO(13, aodOnly = true, label = "PHONE (4A) PRO"),
    UNSUPPORTED(25, aodOnly = false, label = "PREVIEW");

    val sdkDevice: String?
        get() = when (this) {
            PHONE_3 -> Glyph.DEVICE_23112
            PHONE_4A_PRO -> Glyph.DEVICE_25111p
            UNSUPPORTED -> null
        }

    companion object {
        fun detect(): DeviceProfile = runCatching {
            when {
                Common.is23112() -> PHONE_3
                Common.is25111p() -> PHONE_4A_PRO
                else -> UNSUPPORTED
            }
        }.getOrDefault(UNSUPPORTED)
    }
}
