package io.naz.fieldshift.data

import io.naz.fieldshift.data.remote.PowerApi
import io.naz.fieldshift.data.remote.PowerResponse
import io.naz.fieldshift.domain.DailyWeather
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The ViewModel depends on this interface, not on Retrofit. That lets tests swap in a fake,
 * and lets milestone 2 add a Room cache behind it without touching the UI.
 */
interface ClimateRepository {
  /** Daily weather for complete calendar years [startYear]..[endYear]. Throws on network errors. */
  suspend fun getDailyWeather(
    latitude: Double,
    longitude: Double,
    startYear: Int,
    endYear: Int,
  ): List<DailyWeather>
}

class PowerClimateRepository(private val api: PowerApi) : ClimateRepository {
  override suspend fun getDailyWeather(
    latitude: Double,
    longitude: Double,
    startYear: Int,
    endYear: Int,
  ): List<DailyWeather> =
    api
      .getDaily(
        latitude = latitude,
        longitude = longitude,
        start = "${startYear}0101",
        end = "${endYear}1231",
      )
      .toDailyWeather()
}

private val POWER_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE // yyyyMMdd

/** DTO -> domain: pivot three "date -> value" maps into one row per day; fill value -> null. */
internal fun PowerResponse.toDailyWeather(): List<DailyWeather> {
  val fill = header.fillValue
  fun series(name: String) = properties.parameter[name].orEmpty()
  val tMax = series(PowerApi.T2M_MAX)
  val tMin = series(PowerApi.T2M_MIN)
  val precip = series(PowerApi.PRECTOTCORR)

  fun Map<String, Double>.valueOn(day: String): Double? = this[day]?.takeUnless { it == fill }

  return (tMax.keys + tMin.keys + precip.keys).sorted().map { day ->
    DailyWeather(
      date = LocalDate.parse(day, POWER_DATE),
      tMaxC = tMax.valueOn(day),
      tMinC = tMin.valueOn(day),
      precipMm = precip.valueOn(day),
    )
  }
}
