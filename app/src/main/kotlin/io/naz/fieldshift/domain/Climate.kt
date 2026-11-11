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

/**
 * Multi-year averages that the rotation scorer (milestone 3) will consume.
 *
 * Each part is computed only from years with enough valid measurements *for that part*, so
 * missing data can never masquerade as a real result (e.g. 0 mm of rain, or "no frost").
 */
data class ClimateSummary(
  /** Null when fewer than [ClimateCalculator.MIN_YEARS] years have enough rain data. */
  val precipitation: PrecipitationStats?,
  val frost: FrostStats,
)

data class PrecipitationStats(
  /** Mean yearly total precipitation (rain + melted snow), mm. */
  val avgAnnualMm: Double,
  val yearsAnalysed: Int,
  val firstYear: Int,
  val lastYear: Int,
  /** Share of days in the analysed years that had a valid measurement, 0.0–1.0. */
  val coverage: Double,
  /**
   * The hemisphere's default growing season (May–Sep north, Nov–Mar south) – not a locally
   * determined crop season.
   */
  val growingSeason: MonthRange,
  /** Mean growing-season total, mm. Null when too few seasons have enough data. */
  val avgGrowingSeasonMm: Double?,
)

/** Frost results, with "we don't know" kept separate from "there was no frost". */
sealed interface FrostStats {
  /** Fewer than [ClimateCalculator.MIN_YEARS] seasons had enough temperature data. */
  data class InsufficientData(val seasonsWithData: Int) : FrostStats

  /** Enough data, and not a single frost day in any analysed season. */
  data class NoFrost(val seasonsAnalysed: Int) : FrostStats

  data class Frost(
    /** Averaged over seasons that had a spring frost. */
    val avgLastSpringFrost: MonthDay?,
    /** Averaged over seasons that had a fall frost. */
    val avgFirstFallFrost: MonthDay?,
    /** Days strictly between the two frosts, averaged over seasons that had both. */
    val avgFrostFreeDays: Int?,
    val seasonsWithFrost: Int,
    val seasonsAnalysed: Int,
  ) : FrostStats
}
