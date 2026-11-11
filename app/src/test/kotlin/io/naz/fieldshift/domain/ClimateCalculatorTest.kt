package io.naz.fieldshift.domain

import io.naz.fieldshift.domain.FrostStats.Frost
import io.naz.fieldshift.domain.FrostStats.InsufficientData
import io.naz.fieldshift.domain.FrostStats.NoFrost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay

private const val NORTH = 43.7 // southern Ontario
private const val SOUTH = -34.6 // South Australia

class ClimateCalculatorTest {

  /** A full synthetic northern year: frosty up to [lastSpringFrost] and from [firstFallFrost] on. */
  private fun year(
    year: Int,
    lastSpringFrost: MonthDay? = MonthDay.of(4, 30),
    firstFallFrost: MonthDay? = MonthDay.of(10, 10),
    precipPerDay: Double? = 1.0,
  ): List<DailyWeather> {
    val spring = lastSpringFrost?.atYear(year)
    val fall = firstFallFrost?.atYear(year)
    return daysOf(year).map { date ->
      val frosty = (spring != null && date <= spring) || (fall != null && date >= fall)
      DailyWeather(date, tMaxC = 10.0, tMinC = if (frosty) -2.0 else 5.0, precipMm = precipPerDay)
    }
  }

  /** Several identical northern years. */
  private fun years(range: IntRange, precipPerDay: Double? = 1.0) =
    range.flatMap { year(it, precipPerDay = precipPerDay) }

  /** A full synthetic southern calendar year: winter frost from [firstFallFrost] to [lastSpringFrost]. */
  private fun southernYear(
    year: Int,
    firstFallFrost: MonthDay = MonthDay.of(5, 20),
    lastSpringFrost: MonthDay = MonthDay.of(9, 25),
  ): List<DailyWeather> =
    daysOf(year).map { date ->
      val frosty = date >= firstFallFrost.atYear(year) && date <= lastSpringFrost.atYear(year)
      DailyWeather(date, tMaxC = 15.0, tMinC = if (frosty) -1.0 else 8.0, precipMm = 1.0)
    }

  private fun daysOf(year: Int) =
    generateSequence(LocalDate.of(year, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == year }.toList()

  /** Removes rain and/or minimum temperature on the first [count] days of every year. */
  private fun List<DailyWeather>.withGaps(count: Int, rain: Boolean = false, temp: Boolean = false) = map {
    if (it.date.dayOfYear > count) it
    else it.copy(precipMm = if (rain) null else it.precipMm, tMinC = if (temp) null else it.tMinC)
  }

  // --- Not enough data -------------------------------------------------------------------------

  @Test
  fun `no data at all reports not enough data, never zero`() {
    val summary = ClimateCalculator.summarize(emptyList(), NORTH)
    assertNull(summary.precipitation)
    assertEquals(InsufficientData(0), summary.frost)
  }

  @Test
  fun `fewer than three usable years reports not enough data`() {
    val summary = ClimateCalculator.summarize(years(2021..2022), NORTH)
    assertNull(summary.precipitation)
    assertEquals(InsufficientData(2), summary.frost)
  }

  @Test
  fun `years with no rain measurements give not enough data instead of 0 mm`() {
    val summary = ClimateCalculator.summarize(years(2021..2023, precipPerDay = null), NORTH)
    assertNull(summary.precipitation)
    assertTrue(summary.frost is Frost) // temperature data is fine, so frost is still reported
  }

  @Test
  fun `years with no temperature measurements give insufficient frost data instead of no frost`() {
    val summary = ClimateCalculator.summarize(years(2021..2023).map { it.copy(tMinC = null) }, NORTH)
    assertEquals(InsufficientData(0), summary.frost)
    assertEquals(365.0, summary.precipitation!!.avgAnnualMm, 0.001) // rain is still fine
  }

  @Test
  fun `a year needs 95 percent coverage per measurement`() {
    // 18 missing days = 95.07% coverage (kept); 19 missing = 94.79% (dropped).
    val kept = ClimateCalculator.summarize(years(2021..2023).withGaps(18, rain = true), NORTH)
    assertEquals(3, kept.precipitation!!.yearsAnalysed)

    val dropped = ClimateCalculator.summarize(years(2021..2023).withGaps(19, rain = true), NORTH)
    assertNull(dropped.precipitation)
    assertTrue(dropped.frost is Frost) // only rain was missing
  }

  @Test
  fun `partial years are not used`() {
    val summary = ClimateCalculator.summarize(years(2021..2023) + year(2024).take(50), NORTH)
    assertEquals(2023, summary.precipitation!!.lastYear)
    assertEquals(3, (summary.frost as Frost).seasonsAnalysed)
  }

  // --- Precipitation ----------------------------------------------------------------------------

  @Test
  fun `precipitation is averaged over yearly totals`() {
    val days = year(2021, precipPerDay = 1.0) + year(2022, precipPerDay = 3.0) + year(2023, precipPerDay = 2.0)
    val precip = ClimateCalculator.summarize(days, NORTH).precipitation!!
    assertEquals(730.0, precip.avgAnnualMm, 0.001) // (365 + 1095 + 730) / 3
    assertEquals(306.0, precip.avgGrowingSeasonMm!!, 0.001) // 153 days May–Sep * 2 mm
    assertEquals(MonthRange(Month.MAY, Month.SEPTEMBER), precip.growingSeason)
    assertEquals(2021, precip.firstYear)
    assertEquals(2023, precip.lastYear)
    assertEquals(1.0, precip.coverage, 0.0001)
  }

  @Test
  fun `small gaps are filled with the daily mean instead of counted as dry`() {
    val precip = ClimateCalculator.summarize(years(2021..2023).withGaps(12, rain = true), NORTH).precipitation!!
    assertEquals(365.0, precip.avgAnnualMm, 0.001) // not 353
    assertEquals(353.0 / 365, precip.coverage, 0.0001)
  }

  // --- Frost ------------------------------------------------------------------------------------

  @Test
  fun `frost dates are averaged across years`() {
    val days =
      year(2021, MonthDay.of(4, 20), MonthDay.of(10, 5)) +
        year(2022, MonthDay.of(4, 30), MonthDay.of(10, 15)) +
        year(2023, MonthDay.of(4, 25), MonthDay.of(10, 10))
    val frost = ClimateCalculator.summarize(days, NORTH).frost as Frost
    assertEquals(MonthDay.of(4, 25), frost.avgLastSpringFrost)
    assertEquals(MonthDay.of(10, 10), frost.avgFirstFallFrost)
    assertEquals(167, frost.avgFrostFreeDays) // Apr 26 .. Oct 9
    assertEquals(3, frost.seasonsWithFrost)
    assertEquals(3, frost.seasonsAnalysed)
  }

  @Test
  fun `a temperature of exactly zero counts as frost`() {
    val days =
      (2021..2023).flatMap { y ->
        year(y, lastSpringFrost = null, firstFallFrost = null).map {
          if (it.date == LocalDate.of(y, 5, 12)) it.copy(tMinC = 0.0) else it
        }
      }
    assertEquals(MonthDay.of(5, 12), (ClimateCalculator.summarize(days, NORTH).frost as Frost).avgLastSpringFrost)
  }

  @Test
  fun `enough data and no frost at all is reported as no frost`() {
    val days = (2021..2023).flatMap { year(it, lastSpringFrost = null, firstFallFrost = null) }
    assertEquals(NoFrost(3), ClimateCalculator.summarize(days, NORTH).frost)
  }

  @Test
  fun `seasons without frost count in the total but not in the date averages`() {
    val days =
      year(2021, MonthDay.of(4, 20), MonthDay.of(10, 5)) +
        year(2022, lastSpringFrost = null, firstFallFrost = null) +
        year(2023, MonthDay.of(4, 30), MonthDay.of(10, 15))
    val frost = ClimateCalculator.summarize(days, NORTH).frost as Frost
    assertEquals(MonthDay.of(4, 25), frost.avgLastSpringFrost)
    assertEquals(MonthDay.of(10, 10), frost.avgFirstFallFrost)
    assertEquals(2, frost.seasonsWithFrost)
    assertEquals(3, frost.seasonsAnalysed)
  }

  @Test
  fun `frost-free length is averaged from each season's own length`() {
    // Only 2021 has both frosts (Apr 1 → Oct 31: 212 frost-free days). 2022 has only a spring
    // frost and 2023 only a fall frost. Subtracting the averaged dates (Apr 16 → Oct 16) would
    // claim ~182 days – a length no season actually had.
    val days =
      year(2021, MonthDay.of(4, 1), MonthDay.of(10, 31)) +
        year(2022, MonthDay.of(4, 30), firstFallFrost = null) +
        year(2023, lastSpringFrost = null, firstFallFrost = MonthDay.of(9, 30))
    val frost = ClimateCalculator.summarize(days, NORTH).frost as Frost
    assertEquals(212, frost.avgFrostFreeDays)
    assertEquals(3, frost.seasonsWithFrost)
  }

  @Test
  fun `leap years do not shift frost dates`() {
    // Apr 30 is day 121 in a leap year; counting days would turn it into May 1.
    val days = listOf(2016, 2020, 2024).flatMap { year(it, MonthDay.of(4, 30), MonthDay.of(10, 10)) }
    val frost = ClimateCalculator.summarize(days, NORTH).frost as Frost
    assertEquals(MonthDay.of(4, 30), frost.avgLastSpringFrost)
    assertEquals(MonthDay.of(10, 10), frost.avgFirstFallFrost)
    assertEquals(162, frost.avgFrostFreeDays) // May 1 .. Oct 9
  }

  @Test
  fun `a frost on February 29 is treated as February 28`() {
    val days =
      listOf(2016, 2020, 2024).flatMap { y ->
        year(y, lastSpringFrost = null, firstFallFrost = null).map {
          if (it.date == LocalDate.of(y, 2, 29)) it.copy(tMinC = -1.0) else it
        }
      }
    assertEquals(MonthDay.of(2, 28), (ClimateCalculator.summarize(days, NORTH).frost as Frost).avgLastSpringFrost)
  }

  // --- Southern hemisphere ----------------------------------------------------------------------

  @Test
  fun `southern hemisphere frost season runs July to June`() {
    // Calendar 2020–2023 contains three complete Jul–Jun seasons: 2020/21, 2021/22, 2022/23.
    val days = (2020..2023).flatMap { southernYear(it) }
    val frost = ClimateCalculator.summarize(days, SOUTH).frost as Frost

    assertEquals(MonthDay.of(9, 25), frost.avgLastSpringFrost)
    assertEquals(MonthDay.of(5, 20), frost.avgFirstFallFrost)
    assertEquals(236, frost.avgFrostFreeDays) // Sep 26 .. May 19, across New Year
    assertEquals(3, frost.seasonsAnalysed)
  }

  @Test
  fun `southern frost dates are averaged across seasons`() {
    val days =
      southernYear(2020, lastSpringFrost = MonthDay.of(9, 20)) +
        southernYear(2021, firstFallFrost = MonthDay.of(5, 10), lastSpringFrost = MonthDay.of(9, 30)) +
        southernYear(2022, firstFallFrost = MonthDay.of(5, 30), lastSpringFrost = MonthDay.of(9, 25)) +
        southernYear(2023, firstFallFrost = MonthDay.of(5, 20))
    val frost = ClimateCalculator.summarize(days, SOUTH).frost as Frost

    assertEquals(MonthDay.of(9, 25), frost.avgLastSpringFrost) // Sep 20, Sep 30, Sep 25
    assertEquals(MonthDay.of(5, 20), frost.avgFirstFallFrost) // May 10, May 30, May 20
  }

  @Test
  fun `southern seasons that contain a leap day keep their dates`() {
    // Seasons 2022/23, 2023/24 (contains Feb 29 2024) and 2024/25.
    val days = (2022..2025).flatMap { southernYear(it) }
    val frost = ClimateCalculator.summarize(days, SOUTH).frost as Frost
    assertEquals(MonthDay.of(9, 25), frost.avgLastSpringFrost)
    assertEquals(MonthDay.of(5, 20), frost.avgFirstFallFrost) // not May 21
    assertEquals(236, frost.avgFrostFreeDays) // (236 + 237 + 236) / 3, rounded
  }

  @Test
  fun `southern growing season is November to March`() {
    val days = (2020..2023).flatMap { southernYear(it) }
    val precip = ClimateCalculator.summarize(days, SOUTH).precipitation!!

    assertEquals(MonthRange(Month.NOVEMBER, Month.MARCH), precip.growingSeason)
    // Nov 30 + Dec 31 + Jan 31 + Feb 28 + Mar 31 days at 1 mm, in the three complete seasons.
    assertEquals(151.0, precip.avgGrowingSeasonMm!!, 0.001)
    // Annual precipitation still uses calendar years (2020 is a leap year).
    assertEquals((366.0 + 365 + 365 + 365) / 4, precip.avgAnnualMm, 0.001)
  }

  @Test
  fun `month range can wrap around the new year`() {
    val summer = MonthRange(Month.NOVEMBER, Month.MARCH)
    assertTrue(Month.DECEMBER in summer)
    assertTrue(Month.JANUARY in summer)
    assertFalse(Month.JULY in summer)
    assertTrue(Month.JUNE in MonthRange(Month.MAY, Month.SEPTEMBER))
  }
}
