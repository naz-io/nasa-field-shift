package com.naz.fieldshift.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class ClimateCalculatorTest {

  /** A full synthetic year: frosty up to [lastSpringFrost] and from [firstFallFrost] on. */
  private fun year(
    year: Int,
    lastSpringFrost: MonthDay? = MonthDay.of(4, 30),
    firstFallFrost: MonthDay? = MonthDay.of(10, 10),
    precipPerDay: Double? = 1.0,
  ): List<DailyWeather> {
    val spring = lastSpringFrost?.atYear(year)
    val fall = firstFallFrost?.atYear(year)
    return generateSequence(LocalDate.of(year, 1, 1)) { it.plusDays(1) }
      .takeWhile { it.year == year }
      .map { date ->
        val frosty = (spring != null && date <= spring) || (fall != null && date >= fall)
        DailyWeather(date, tMaxC = 10.0, tMinC = if (frosty) -2.0 else 5.0, precipMm = precipPerDay)
      }
      .toList()
  }

  @Test
  fun `no complete year returns null`() {
    assertNull(ClimateCalculator.summarize(emptyList()))
    assertNull(ClimateCalculator.summarize(year(2021).take(100)))
  }

  @Test
  fun `precipitation is averaged over yearly totals`() {
    val days = year(2021, precipPerDay = 1.0) + year(2022, precipPerDay = 3.0)
    val summary = ClimateCalculator.summarize(days)!!
    assertEquals(730.0, summary.avgAnnualPrecipMm, 0.001) // (365 + 1095) / 2
    assertEquals(306.0, summary.avgGrowingSeasonPrecipMm, 0.001) // 153 days May–Sep * 2 mm
    assertEquals(2021, summary.firstYear)
    assertEquals(2022, summary.lastYear)
  }

  @Test
  fun `frost dates are averaged across years`() {
    val days =
      year(2021, MonthDay.of(4, 20), MonthDay.of(10, 5)) +
        year(2022, MonthDay.of(4, 30), MonthDay.of(10, 15))
    val summary = ClimateCalculator.summarize(days)!!
    assertEquals(MonthDay.of(4, 25), summary.avgLastSpringFrost)
    assertEquals(MonthDay.of(10, 10), summary.avgFirstFallFrost)
    assertEquals(167, summary.avgFrostFreeDays) // Apr 26 .. Oct 9
  }

  @Test
  fun `a temperature of exactly zero counts as frost`() {
    val days =
      year(2021, lastSpringFrost = null, firstFallFrost = null).map {
        if (it.date == LocalDate.of(2021, 5, 12)) it.copy(tMinC = 0.0) else it
      }
    assertEquals(MonthDay.of(5, 12), ClimateCalculator.summarize(days)!!.avgLastSpringFrost)
  }

  @Test
  fun `frost-free climate has no frost dates`() {
    val summary =
      ClimateCalculator.summarize(year(2021, lastSpringFrost = null, firstFallFrost = null))!!
    assertNull(summary.avgLastSpringFrost)
    assertNull(summary.avgFirstFallFrost)
    assertNull(summary.avgFrostFreeDays)
  }

  @Test
  fun `missing values are skipped and partial years ignored`() {
    val withGaps =
      year(2021).map { if (it.date.dayOfMonth == 1) it.copy(tMinC = null, precipMm = null) else it }
    val summary = ClimateCalculator.summarize(withGaps + year(2022).take(50))!!
    assertEquals(2021, summary.lastYear)
    assertEquals(353.0, summary.avgAnnualPrecipMm, 0.001) // 365 - 12 missing days
    assertEquals(MonthDay.of(4, 30), summary.avgLastSpringFrost)
  }
}
