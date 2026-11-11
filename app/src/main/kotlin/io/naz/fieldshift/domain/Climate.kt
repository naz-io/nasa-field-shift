package io.naz.fieldshift.domain

import java.time.LocalDate
import java.time.Month
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
  /**
   * Mean precipitation over the growing season ([growingSeasonFirstMonth]..[growingSeasonLastMonth],
   * May–Sep in the north, Nov–Mar in the south), mm. Null if no complete season was available.
   */
  val avgGrowingSeasonPrecipMm: Double?,
  val growingSeasonFirstMonth: Month,
  val growingSeasonLastMonth: Month,
  /** Null if no season had a spring frost (e.g. tropical locations). */
  val avgLastSpringFrost: MonthDay?,
  /** Null if no season had a fall frost. */
  val avgFirstFallFrost: MonthDay?,
  val avgFrostFreeDays: Int?,
)
