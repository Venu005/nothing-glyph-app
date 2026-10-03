package app.backlit.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.round

data class Coords(val lat: Double, val lon: Double)

/** Two decimals ≈ 1 km: plenty for sunrise, and we never store anything finer. */
fun roundCoord(v: Double): Double = round(v * 100.0) / 100.0

class LocationSource(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(): Coords? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val recent = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.time >= dayAgo }
            .maxByOrNull { it.time }
        val loc: Location? = recent ?: withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                val started = runCatching {
                    lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, signal, context.mainExecutor) { cont.resume(it) }
                }
                if (started.isFailure) cont.resume(null)
            }
        }
        return loc?.let { Coords(roundCoord(it.latitude), roundCoord(it.longitude)) }
    }
}
