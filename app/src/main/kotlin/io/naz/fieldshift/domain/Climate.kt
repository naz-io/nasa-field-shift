package io.naz.fieldshift.domain

import java.time.LocalDate
import java.time.MonthDay

/** One day of weather for a location. A null value means the source had no data that day. */
data class DailyWeather(
  val date: LocalDate,
  val tMaxC: Double?,
  val tMinC: Double?,
  val precipMm: Double?,
)

/** Multi-year averages that the rotation scorer (milestone 3) will consume. */
data class ClimateSummary(
  val firstYear: Int,
  val lastYear: Int,
  /** Mean yearly total precipitation (rain + melted snow), mm. */
  val avgAnnualPrecipMm: Double,
  /** Mean May–September total precipitation, mm. */
  val avgGrowingSeasonPrecipMm: Double,
  /** Null if no year had a spring frost (e.g. tropical locations). */
  val avgLastSpringFrost: MonthDay?,
  /** Null if no year had a fall frost. */
  val avgFirstFallFrost: MonthDay?,
  val avgFrostFreeDays: Int?,
)
