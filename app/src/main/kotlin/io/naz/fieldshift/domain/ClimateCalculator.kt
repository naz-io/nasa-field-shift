package io.naz.fieldshift.domain

import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import kotlin.math.roundToInt

/**
 * Pure Kotlin – no Android imports – so it runs in plain JVM unit tests in milliseconds.
 *
 * Definitions (northern hemisphere):
 *  - frost day: daily minimum air temperature at 2 m <= [frostThresholdC]
 *  - last spring frost: latest frost day before July 1
 *  - first fall frost: earliest frost day on/after July 1
 * Each is found per year, then averaged across years as a day-of-year.
 */
object ClimateCalculator {
  const val DEFAULT_FROST_THRESHOLD_C = 0.0

  /** Years with fewer days than this (partial years) are ignored so they don't skew averages. */
  private const val MIN_DAYS_PER_YEAR = 360

  /** Any non-leap year; used only to turn an average day-of-year back into a month/day. */
  private const val REFERENCE_YEAR = 2023

  /** Returns null when there isn't at least one complete year of data. */
  fun summarize(
    days: List<DailyWeather>,
    frostThresholdC: Double = DEFAULT_FROST_THRESHOLD_C,
  ): ClimateSummary? {
    val years =
      days.groupBy { it.date.year }.filterValues { it.size >= MIN_DAYS_PER_YEAR }.toSortedMap()
    if (years.isEmpty()) return null

    val annualPrecip = years.values.map { year -> year.sumOf { it.precipMm ?: 0.0 } }
    val seasonPrecip =
      years.values.map { year ->
        year.filter { it.date.month in Month.MAY..Month.SEPTEMBER }.sumOf { it.precipMm ?: 0.0 }
      }

    val springFrosts = mutableListOf<Int>()
    val fallFrosts = mutableListOf<Int>()
    for ((year, yearDays) in years) {
      val midYear = LocalDate.of(year, Month.JULY, 1)
      val frostDates =
        yearDays.filter { it.tMinC != null && it.tMinC <= frostThresholdC }.map { it.date }
      frostDates.filter { it < midYear }.maxOrNull()?.let { springFrosts += it.dayOfYear }
      frostDates.filter { it >= midYear }.minOrNull()?.let { fallFrosts += it.dayOfYear }
    }
    val avgSpring = springFrosts.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val avgFall = fallFrosts.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    return ClimateSummary(
      firstYear = years.firstKey(),
      lastYear = years.lastKey(),
      avgAnnualPrecipMm = annualPrecip.average(),
      avgGrowingSeasonPrecipMm = seasonPrecip.average(),
      avgLastSpringFrost = avgSpring?.toMonthDay(),
      avgFirstFallFrost = avgFall?.toMonthDay(),
      // Days strictly between the two frosts.
      avgFrostFreeDays = if (avgSpring != null && avgFall != null) avgFall - avgSpring - 1 else null,
    )
  }

  private fun Int.toMonthDay(): MonthDay = MonthDay.from(LocalDate.ofYearDay(REFERENCE_YEAR, this))
}
