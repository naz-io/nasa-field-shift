package io.naz.fieldshift.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import io.naz.fieldshift.domain.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class PlaceResult(val label: String, val point: GeoPoint)

/** Turns text into places ("Guelph") and places into names. An interface so tests can fake it. */
interface PlaceSearch {
  suspend fun search(query: String): List<PlaceResult>

  /** A short human name for a point, e.g. "Near Guelph, Ontario", or null if unknown. */
  suspend fun nameFor(point: GeoPoint): String?
}

/**
 * Uses Android's built-in Geocoder: free and needs no extra API key (on devices with Google Play
 * services it is backed by Google's geocoding). Errors and "no results" both become empty/null,
 * because a failed lookup should never break the map.
 */
class GeocoderPlaceSearch(context: Context) : PlaceSearch {
  private val geocoder = Geocoder(context, Locale.getDefault())

  override suspend fun search(query: String): List<PlaceResult> {
    if (query.isBlank() || !Geocoder.isPresent()) return emptyList()
    return fromName(query.trim()).map { PlaceResult(it.label(), GeoPoint(it.latitude, it.longitude)) }
  }

  override suspend fun nameFor(point: GeoPoint): String? {
    if (!Geocoder.isPresent()) return null
    val address = fromPoint(point).firstOrNull() ?: return null
    val place = address.locality ?: address.subAdminArea ?: return address.adminArea
    return listOfNotNull("Near $place", address.adminArea).joinToString(", ")
  }

  // Android 13 (API 33) added a callback API and deprecated the blocking one. We support both:
  // the callback version is wrapped in suspendCancellableCoroutine, the old one runs on Dispatchers.IO.

  private suspend fun fromName(query: String): List<Address> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      suspendCancellableCoroutine { cont ->
        geocoder.getFromLocationName(query, MAX_RESULTS, listener(cont))
      }
    } else {
      withContext(Dispatchers.IO) {
        runCatching {
          @Suppress("DEPRECATION")
          geocoder.getFromLocationName(query, MAX_RESULTS).orEmpty()
        }.getOrDefault(emptyList())
      }
    }

  private suspend fun fromPoint(point: GeoPoint): List<Address> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      suspendCancellableCoroutine { cont ->
        geocoder.getFromLocation(point.latitude, point.longitude, 1, listener(cont))
      }
    } else {
      withContext(Dispatchers.IO) {
        runCatching {
          @Suppress("DEPRECATION")
          geocoder.getFromLocation(point.latitude, point.longitude, 1).orEmpty()
        }.getOrDefault(emptyList())
      }
    }

  private fun listener(cont: kotlinx.coroutines.CancellableContinuation<List<Address>>) =
    object : Geocoder.GeocodeListener {
      override fun onGeocode(addresses: MutableList<Address>) {
        if (cont.isActive) cont.resume(addresses)
      }

      override fun onError(errorMessage: String?) {
        if (cont.isActive) cont.resume(emptyList())
      }
    }

  private fun Address.label(): String =
    (0..maxAddressLineIndex).firstNotNullOfOrNull { getAddressLine(it) }
      ?: listOfNotNull(featureName, adminArea, countryName).joinToString(", ")

  private companion object {
    const val MAX_RESULTS = 5
  }
}
