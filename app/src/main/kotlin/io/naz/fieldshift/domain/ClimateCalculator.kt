package io.naz.fieldshift.domain

import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Pure Kotlin – no Android imports – so it runs in plain JVM unit tests in milliseconds.
 *
 * Data rules:
 *  - A year (or season) is used for a measurement only if at least [MIN_COVERAGE] of its days
 *    have a valid value *for that measurement*. Rain and temperature are checked separately.
 *  - Small gaps in a used year are filled with that year's daily mean, so a 2% gap doesn't
 *    shave 2% off the total.
 *  - Results need at least [MIN_YEARS] usable years; otherwise they report "not enough data".
 *
 * Frost definitions:
 *  - frost day: daily minimum air temperature at 2 m <= [frostThresholdC]
 *  - a "frost season" is a full year that starts mid-winter, so spring comes in its first half
 *    and fall in its second half:
 *      northern hemisphere: Jan 1 – Dec 31
 *      southern hemisphere: Jul 1 – Jun 30 (winter is Jun–Aug there)
 *  - last spring frost: latest frost day in the first half of the season
 *  - first fall frost: earliest frost day in the second half of the season
 */
object ClimateCalculator {
  const val DEFAULT_FROST_THRESHOLD_C = 0.0

  /** Minimum share of days with a valid value for a year/season to count. */
  const val MIN_COVERAGE = 0.95

  /** Minimum number of usable years/seasons before we report an average. */
  const val MIN_YEARS = 3

  fun summarize(
    days: List<DailyWeather>,
    latitude: Double,
    frostThresholdC: Double = DEFAULT_FROST_THRESHOLD_C,
  ): ClimateSummary {
    val hemisphere = Hemisphere.of(latitude)
    return ClimateSummary(
      precipitation = precipitation(days, hemisphere),
      frost = frost(days, hemisphere, frostThresholdC),
    )
  }

  private fun precipitation(days: List<DailyWeather>, hemisphere: Hemisphere): PrecipitationStats? {
    // Annual totals use calendar years – that's how people quote rainfall.
    val years =
      days.groupBy { it.date.year }.filter { (year, yearDays) ->
        coverage(yearDays, LocalDate.of(year, 1, 1).lengthOfYear()) { it.precipMm } >= MIN_COVERAGE
      }
    if (years.size < MIN_YEARS) return null

    val annualTotals =
      years.map { (year, yearDays) -> filledTotal(yearDays, LocalDate.of(year, 1, 1).lengthOfYear()) }
    val validDays = years.values.sumOf { yearDays -> yearDays.count { it.precipMm != null } }
    val calendarDays = years.keys.sumOf { LocalDate.of(it, 1, 1).lengthOfYear() }

    // Growing-season totals are grouped by frost season, so a southern Nov–Mar season stays whole.
    val growingTotals =
      days
        .filter { it.date.month in hemisphere.growingMonths }
        .groupBy { hemisphere.seasonOf(it.date) }
        .mapNotNull { (season, seasonDays) ->
          val expected = hemisphere.growingDays(season)
          if (coverage(seasonDays, expected) { it.precipMm } >= MIN_COVERAGE) filledTotal(seasonDays, expected)
          else null
        }

    return PrecipitationStats(
      avgAnnualMm = annualTotals.average(),
      yearsAnalysed = years.size,
      firstYear = years.keys.min(),
      lastYear = years.keys.max(),
      coverage = validDays.toDouble() / calendarDays,
      growingSeason = hemisphere.growingMonths,
      avgGrowingSeasonMm = growingTotals.takeIf { it.size >= MIN_YEARS }?.average(),
    )
  }

  private fun frost(days: List<DailyWeather>, hemisphere: Hemisphere, thresholdC: Double): FrostStats {
    val seasons =
      days.groupBy { hemisphere.seasonOf(it.date) }.filter { (season, seasonDays) ->
        coverage(seasonDays, hemisphere.seasonLength(season)) { it.tMinC } >= MIN_COVERAGE
      }
    if (seasons.size < MIN_YEARS) return FrostStats.InsufficientData(seasons.size)

    val springFrosts = mutableListOf<Int>() // as day index in the reference season
    val fallFrosts = mutableListOf<Int>()
    val frostFreeLengths = mutableListOf<Long>()
    var seasonsWithFrost = 0
    for ((season, seasonDays) in seasons) {
      val midSeason = hemisphere.seasonStart(season).plusMonths(6)
      val frostDates = seasonDays.filter { it.tMinC != null && it.tMinC <= thresholdC }.map { it.date }
      if (frostDates.isEmpty()) continue // counted in seasonsAnalysed, kept out of the date averages
      seasonsWithFrost++

      val spring = frostDates.filter { it < midSeason }.maxOrNull()
      val fall = frostDates.filter { it >= midSeason }.minOrNull()
      spring?.let { springFrosts += hemisphere.referenceIndex(it) }
      fall?.let { fallFrosts += hemisphere.referenceIndex(it) }
      // The frost-free length is measured inside each season, from its own real dates (leap days
      // included), and only for seasons that had both frosts. Subtracting two separately averaged
      // dates would mix seasons and give a length no season actually had.
      if (spring != null && fall != null) frostFreeLengths += ChronoUnit.DAYS.between(spring, fall) - 1
    }
    if (seasonsWithFrost == 0) return FrostStats.NoFrost(seasons.size)

    return FrostStats.Frost(
      avgLastSpringFrost = springFrosts.averageIndex()?.let(hemisphere::fromReferenceIndex),
      avgFirstFallFrost = fallFrosts.averageIndex()?.let(hemisphere::fromReferenceIndex),
      avgFrostFreeDays = frostFreeLengths.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
      seasonsWithFrost = seasonsWithFrost,
      seasonsAnalysed = seasons.size,
    )
  }

  private fun List<Int>.averageIndex(): Int? = takeIf { it.isNotEmpty() }?.average()?.roundToInt()

  private fun coverage(days: List<DailyWeather>, expectedDays: Int, value: (DailyWeather) -> Double?) =
    days.count { value(it) != null }.toDouble() / expectedDays

  /** Total precipitation with small gaps filled by the mean of the valid days. */
  private fun filledTotal(days: List<DailyWeather>, expectedDays: Int): Double =
    days.mapNotNull { it.precipMm }.average() * expectedDays

  private enum class Hemisphere(private val seasonStartMonth: Month, val growingMonths: MonthRange) {
    NORTH(Month.JANUARY, MonthRange(Month.MAY, Month.SEPTEMBER)),
    SOUTH(Month.JULY, MonthRange(Month.NOVEMBER, Month.MARCH));

    /** Seasons are named after the calendar year they end in. */
    fun seasonOf(date: LocalDate): Int =
      if (this == SOUTH && date.month >= Month.JULY) date.year + 1 else date.year

    fun seasonStart(season: Int): LocalDate =
      LocalDate.of(if (this == SOUTH) season - 1 else season, seasonStartMonth, 1)

    fun seasonLength(season: Int): Int =
      ChronoUnit.DAYS.between(seasonStart(season), seasonStart(season + 1)).toInt()

    /** Number of calendar days in this season's growing months. */
    fun growingDays(season: Int): Int =
      generateSequence(seasonStart(season)) { it.plusDays(1) }
        .takeWhile { it < seasonStart(season + 1) }
        .count { it.month in growingMonths }

    /**
     * Position of a date's month and day within the reference season. Averaging these instead of
     * "days since season start" keeps leap years from shifting dates: Apr 30 is Apr 30 in every
     * year. Feb 29 is treated as Feb 28.
     */
    fun referenceIndex(date: LocalDate): Int {
      val monthDay = MonthDay.from(date).let { if (it == LEAP_DAY) MonthDay.of(2, 28) else it }
      val start = seasonStart(REFERENCE_SEASON)
      val year = if (this == SOUTH && monthDay.month >= Month.JULY) start.year else REFERENCE_SEASON
      return ChronoUnit.DAYS.between(start, monthDay.atYear(year)).toInt()
    }

    fun fromReferenceIndex(index: Int): MonthDay =
      MonthDay.from(seasonStart(REFERENCE_SEASON).plusDays(index.toLong()))

    companion object {
      /** Its February (2023) is not a leap month, for both hemispheres. */
      private const val REFERENCE_SEASON = 2023
      private val LEAP_DAY = MonthDay.of(2, 29)

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
