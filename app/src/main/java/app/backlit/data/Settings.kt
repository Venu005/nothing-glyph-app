package app.backlit.data

import app.backlit.render.FaceOptions

enum class LocationMode { FIXED, APPROXIMATE, CITY }

data class Settings(
    val faceId: String = "analog",
    val secondHand: Boolean = true,
    val brightness: Int = 80,
    val use24h: Boolean = true,
    val locationMode: LocationMode = LocationMode.FIXED,
    val lat: Double? = null,
    val lon: Double? = null,
    val placeName: String? = null,
    val locationUpdatedAt: Long = 0L,
    val toyEverBound: Boolean = false,
) {
    val faceOptions: FaceOptions get() = FaceOptions(secondHand = secondHand, use24h = use24h)
}
