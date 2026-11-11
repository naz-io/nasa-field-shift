package io.naz.fieldshift.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import io.naz.fieldshift.domain.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/** The device's current position. An interface so tests can fake it. */
interface LocationProvider {
  fun hasPermission(): Boolean

  /** Throws [LocationUnavailableException] if permission is missing or no position can be found. */
  suspend fun currentLocation(): GeoPoint
}

class LocationUnavailableException(message: String) : Exception(message)

/** Google Play services' Fused Location Provider: combines GPS, Wi-Fi and cell for a quick fix. */
class FusedLocationProvider(private val context: Context) : LocationProvider {
  private val client = LocationServices.getFusedLocationProviderClient(context)

  override fun hasPermission(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

  // Lint can't see that we check the permission ourselves before each call.
  @SuppressLint("MissingPermission")
  override suspend fun currentLocation(): GeoPoint {
    if (!hasPermission()) throw LocationUnavailableException("Location permission not granted.")

    // With "precise" permission, ask for GPS-quality accuracy. With "approximate" only, ask for
    // balanced accuracy (Wi-Fi/cell) – asking for more would just be downgraded anyway.
    val priority =
      if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) Priority.PRIORITY_HIGH_ACCURACY
      else Priority.PRIORITY_BALANCED_POWER_ACCURACY

    // Note: withTimeoutOrNull, not withTimeout. withTimeout throws TimeoutCancellationException,
    // which is a CancellationException – callers rightly re-throw those, so a timeout would end
    // silently instead of showing the farmer a message.
    val fresh =
      withTimeoutOrNull(TIMEOUT_MS.milliseconds) {
        val cancel = CancellationTokenSource()
        try {
          client.getCurrentLocation(priority, cancel.token).awaitOrNull()
        } finally {
          cancel.cancel() // stops the GPS request if we time out
        }
      }
    // Fall back to the last known position: good enough to find a field.
    val location = fresh ?: client.lastLocation.awaitOrNull()
    return location?.let { GeoPoint(it.latitude, it.longitude) }
      ?: throw LocationUnavailableException("Couldn't find your position – check that location is turned on.")
  }

  private fun granted(permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

  private companion object {
    const val TIMEOUT_MS = 15_000L
  }
}

/**
 * Bridges a Play services Task to a coroutine. A Task can succeed, fail *or be cancelled*;
 * addOnCompleteListener sees all three, so the coroutine can never hang waiting.
 * Any non-success becomes null, and the caller decides what that means.
 */
private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
  addOnCompleteListener { task -> if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null) }
}
