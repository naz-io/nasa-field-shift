package io.naz.fieldshift.domain

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

  /** A full synthetic southern year: winter frost from [firstFallFrost] to [lastSpringFrost]. */
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

  @Test
  fun `no complete year returns null`() {
    assertNull(ClimateCalculator.summarize(emptyList(), NORTH))
    assertNull(ClimateCalculator.summarize(year(2021).take(100), NORTH))
  }

  @Test
  fun `precipitation is averaged over yearly totals`() {
    val days = year(2021, precipPerDay = 1.0) + year(2022, precipPerDay = 3.0)
    val summary = ClimateCalculator.summarize(days, NORTH)!!
    assertEquals(730.0, summary.avgAnnualPrecipMm, 0.001) // (365 + 1095) / 2
    assertEquals(306.0, summary.avgGrowingSeasonPrecipMm!!, 0.001) // 153 days May–Sep * 2 mm
    assertEquals(Month.MAY, summary.growingSeasonFirstMonth)
    assertEquals(Month.SEPTEMBER, summary.growingSeasonLastMonth)
    assertEquals(2021, summary.firstYear)
    assertEquals(2022, summary.lastYear)
  }

  @Test
  fun `frost dates are averaged across years`() {
    val days =
      year(2021, MonthDay.of(4, 20), MonthDay.of(10, 5)) +
        year(2022, MonthDay.of(4, 30), MonthDay.of(10, 15))
    val summary = ClimateCalculator.summarize(days, NORTH)!!
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
    assertEquals(MonthDay.of(5, 12), ClimateCalculator.summarize(days, NORTH)!!.avgLastSpringFrost)
  }

  @Test
  fun `frost-free climate has no frost dates`() {
    val summary =
      ClimateCalculator.summarize(year(2021, lastSpringFrost = null, firstFallFrost = null), NORTH)!!
    assertNull(summary.avgLastSpringFrost)
    assertNull(summary.avgFirstFallFrost)
    assertNull(summary.avgFrostFreeDays)
  }

  @Test
  fun `missing values are skipped and partial years ignored`() {
    val withGaps =
      year(2021).map { if (it.date.dayOfMonth == 1) it.copy(tMinC = null, precipMm = null) else it }
    val summary = ClimateCalculator.summarize(withGaps + year(2022).take(50), NORTH)!!
    assertEquals(2021, summary.lastYear)
    assertEquals(353.0, summary.avgAnnualPrecipMm, 0.001) // 365 - 12 missing days
    assertEquals(MonthDay.of(4, 30), summary.avgLastSpringFrost)
  }

  @Test
  fun `southern hemisphere frost season runs July to June`() {
    // Calendar 2020–2022 contains two complete Jul–Jun seasons: 2020/21 and 2021/22.
    val days = southernYear(2020) + southernYear(2021) + southernYear(2022)
    val summary = ClimateCalculator.summarize(days, SOUTH)!!

    assertEquals(MonthDay.of(9, 25), summary.avgLastSpringFrost)
    assertEquals(MonthDay.of(5, 20), summary.avgFirstFallFrost)
    assertEquals(236, summary.avgFrostFreeDays) // Sep 26 .. May 19, across New Year
  }

  @Test
  fun `southern frost dates are averaged across seasons`() {
    val days =
      southernYear(2020, lastSpringFrost = MonthDay.of(9, 20)) +
        southernYear(2021, firstFallFrost = MonthDay.of(5, 10), lastSpringFrost = MonthDay.of(9, 30)) +
        southernYear(2022, firstFallFrost = MonthDay.of(5, 30))
    val summary = ClimateCalculator.summarize(days, SOUTH)!!

    assertEquals(MonthDay.of(9, 25), summary.avgLastSpringFrost) // Sep 20 and Sep 30
    assertEquals(MonthDay.of(5, 20), summary.avgFirstFallFrost) // May 10 and May 30
  }

  @Test
  fun `southern growing season is November to March and partial seasons are ignored`() {
    val days = southernYear(2020) + southernYear(2021) + southernYear(2022)
    val summary = ClimateCalculator.summarize(days, SOUTH)!!

    assertEquals(Month.NOVEMBER, summary.growingSeasonFirstMonth)
    assertEquals(Month.MARCH, summary.growingSeasonLastMonth)
    // Nov 30 + Dec 31 + Jan 31 + Feb 28 + Mar 31 days at 1 mm, in both complete seasons.
    assertEquals(151.0, summary.avgGrowingSeasonPrecipMm!!, 0.001)
    // Annual precipitation still uses calendar years (2020 is a leap year).
    assertEquals((366.0 + 365 + 365) / 3, summary.avgAnnualPrecipMm, 0.001)
    assertEquals(2020, summary.firstYear)
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
