package io.naz.fieldshift.domain

/** A point on Earth. NASA POWER and the map both speak latitude/longitude. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
  /** "43.55°N, 80.25°W" – readable without knowing the sign convention. */
  fun display(): String =
    "%.4f°%s, %.4f°%s".format(
      kotlin.math.abs(latitude),
      if (latitude >= 0) "N" else "S",
      kotlin.math.abs(longitude),
      if (longitude >= 0) "E" else "W",
    )
}

/** The farmer's chosen field. */
data class FieldLocation(val name: String, val point: GeoPoint) {
  val latitude: Double get() = point.latitude
  val longitude: Double get() = point.longitude
}
