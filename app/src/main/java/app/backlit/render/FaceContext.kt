package app.backlit.render

enum class Mode { ACTIVE, AOD }

data class FaceOptions(
    val secondHand: Boolean = true,
    val use24h: Boolean = true,
)

data class FaceContext(
    val hour: Int,
    val minute: Int,
    val second: Int,
    val size: Int,
    val mode: Mode,
    val options: FaceOptions = FaceOptions(),
    val dayLight: DayLight = DayLight.FIXED,
) {
    val minuteOfDay: Double get() = hour * 60.0 + minute
    val isLarge: Boolean get() = size >= 25
}
