package app.backlit.alerts

import kotlinx.serialization.Serializable

@Serializable
data class ContactRule(val name: String, val animationId: String)

@Serializable
data class DeviceRule(val address: String, val name: String, val animationId: String)

const val KIND_IMPORT = "import"
const val KIND_DRAWING = "drawing"

/**
 * An animation stored on disk as Glyph Museum JSON. sourceV is the Museum "v" (1 = 25×25, 4 = 13×13).
 * kind = "drawing" for Studio drawings (editable; fps is their speed), "import" for Glyph Museum imports.
 */
@Serializable
data class AnimIndexEntry(val id: String, val name: String, val sourceV: Int, val kind: String = KIND_IMPORT, val fps: Int = 0)

enum class AlertKind { CALL, DEVICE, MISSED }

data class ActiveAlert(val animationId: String, val kind: AlertKind, val startedAt: Long, val endsAt: Long)

data class AlertConfig(
    val contacts: List<ContactRule> = emptyList(),
    val devices: List<DeviceRule> = emptyList(),
    val imports: List<AnimIndexEntry> = emptyList(),
)
