package io.naz.fieldshift.domain

import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Pure Kotlin – no Android imports – so it runs in plain JVM unit tests in milliseconds.
 *
 * Definitions:
 *  - frost day: daily minimum air temperature at 2 m <= [frostThresholdC]
 *  - a "frost season" is a full year that starts mid-winter, so spring comes in its first half
 *    and fall in its second half:
 *      northern hemisphere: Jan 1 – Dec 31
 *      southern hemisphere: Jul 1 – Jun 30 (winter is Jun–Aug there)
 *  - last spring frost: latest frost day in the first half of the season
 *  - first fall frost: earliest frost day in the second half of the season
 * Each is found per season as "days since the season started", then averaged across seasons.
 */
object ClimateCalculator {
  const val DEFAULT_FROST_THRESHOLD_C = 0.0

  /** Years/seasons with fewer days than this (partial ones) are ignored so they don't skew averages. */
  private const val MIN_DAYS_PER_YEAR = 360

  /** Returns null when there isn't at least one complete calendar year of data. */
  fun summarize(
    days: List<DailyWeather>,
    latitude: Double,
    frostThresholdC: Double = DEFAULT_FROST_THRESHOLD_C,
  ): ClimateSummary? {
    val hemisphere = Hemisphere.of(latitude)

    // Precipitation per calendar year.
    val years = days.groupBy { it.date.year }.completeOnly()
    if (years.isEmpty()) return null
    val annualPrecip = years.values.map { year -> year.sumOf { it.precipMm ?: 0.0 } }

    // Everything seasonal is grouped by frost season instead.
    val seasons = days.groupBy { hemisphere.seasonOf(it.date) }.completeOnly()

    val seasonPrecip =
      seasons.values.map { season ->
        season.filter { it.date.month in hemisphere.growingMonths }.sumOf { it.precipMm ?: 0.0 }
      }

    val springFrosts = mutableListOf<Long>()
    val fallFrosts = mutableListOf<Long>()
    for ((season, seasonDays) in seasons) {
      val start = hemisphere.seasonStart(season)
      val half = ChronoUnit.DAYS.between(start, start.plusMonths(6))
      val frostIndexes =
        seasonDays
          .filter { it.tMinC != null && it.tMinC <= frostThresholdC }
          .map { ChronoUnit.DAYS.between(start, it.date) }
      frostIndexes.filter { it < half }.maxOrNull()?.let { springFrosts += it }
      frostIndexes.filter { it >= half }.minOrNull()?.let { fallFrosts += it }
    }
    val avgSpring = springFrosts.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val avgFall = fallFrosts.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    return ClimateSummary(
      firstYear = years.firstKey(),
      lastYear = years.lastKey(),
      avgAnnualPrecipMm = annualPrecip.average(),
      avgGrowingSeasonPrecipMm = seasonPrecip.takeIf { it.isNotEmpty() }?.average(),
      growingSeasonFirstMonth = hemisphere.growingMonths.start,
      growingSeasonLastMonth = hemisphere.growingMonths.endInclusive,
      avgLastSpringFrost = avgSpring?.let(hemisphere::toMonthDay),
      avgFirstFallFrost = avgFall?.let(hemisphere::toMonthDay),
      // Days strictly between the two frosts.
      avgFrostFreeDays = if (avgSpring != null && avgFall != null) avgFall - avgSpring - 1 else null,
    )
  }

  private fun Map<Int, List<DailyWeather>>.completeOnly() =
    filterValues { it.size >= MIN_DAYS_PER_YEAR }.toSortedMap()

  private enum class Hemisphere(private val seasonStartMonth: Month, val growingMonths: MonthRange) {
    NORTH(Month.JANUARY, MonthRange(Month.MAY, Month.SEPTEMBER)),
    SOUTH(Month.JULY, MonthRange(Month.NOVEMBER, Month.MARCH));

    /** Seasons are named after the calendar year they end in. */
    fun seasonOf(date: LocalDate): Int =
      if (this == SOUTH && date.month >= Month.JULY) date.year + 1 else date.year

    fun seasonStart(season: Int): LocalDate =
      LocalDate.of(if (this == SOUTH) season - 1 else season, seasonStartMonth, 1)

    /** Turns an average "days since season start" back into a date, using a non-leap season. */
    fun toMonthDay(dayIndex: Int): MonthDay =
      MonthDay.from(seasonStart(REFERENCE_SEASON).plusDays(dayIndex.toLong()))

    companion object {
      /** Its February (2023) is not a leap month, for both hemispheres. */
      private const val REFERENCE_SEASON = 2023

      fun of(latitude: Double) = if (latitude < 0) SOUTH else NORTH
    }
  }
}

/** Inclusive month range that may wrap around the new year (e.g. Nov–Mar). */
data class MonthRange(val start: Month, val endInclusive: Month) {
  operator fun contains(month: Month): Boolean =
    if (start <= endInclusive) month in start..endInclusive
    else month >= start || month <= endInclusive
}
