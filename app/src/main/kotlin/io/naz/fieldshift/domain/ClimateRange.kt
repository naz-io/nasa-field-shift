package io.naz.fieldshift.domain

import java.time.LocalDate

/** An inclusive range of calendar years, e.g. 2016–2025. */
data class YearRange(val first: Int, val last: Int) {
  val count: Int get() = last - first + 1
}

/**
 * Which years of NASA POWER data to analyse. Pure function of "today", so it's trivially testable
 * and the app moves forward by itself instead of being stuck on a hard-coded range.
 */
object ClimateRange {
  /**
   * Ten years: steadier frost averages than five (official climate "normals" use 30), while the
   * download stays small – about 180 KB and under a second.
   */
  const val DEFAULT_YEARS = 10

  /**
   * NASA POWER's daily data arrives with a lag, so a year only counts as complete a few months
   * after it ends. Until then we keep using the previous ten years.
   */
  const val DATA_LAG_MONTHS = 3L

  fun lastCompleteYears(today: LocalDate, count: Int = DEFAULT_YEARS): YearRange {
    val last = today.minusMonths(DATA_LAG_MONTHS).year - 1
    return YearRange(first = last - count + 1, last = last)
  }
}
