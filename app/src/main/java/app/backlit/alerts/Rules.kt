package app.backlit.alerts

import kotlinx.serialization.Serializable

@Serializable
data class ContactRule(val name: String, val animationId: String)

@Serializable
data class DeviceRule(val address: String, val name: String, val animationId: String)

/** An imported animation stored on disk; sourceV is the Glyph Museum "v" (1 = Phone (3), 4 = (4a) Pro). */
@Serializable
data class AnimIndexEntry(val id: String, val name: String, val sourceV: Int)

enum class AlertKind { CALL, DEVICE }

data class ActiveAlert(val animationId: String, val kind: AlertKind, val startedAt: Long, val endsAt: Long)

data class AlertConfig(
    val contacts: List<ContactRule> = emptyList(),
    val devices: List<DeviceRule> = emptyList(),
    val imports: List<AnimIndexEntry> = emptyList(),
)
