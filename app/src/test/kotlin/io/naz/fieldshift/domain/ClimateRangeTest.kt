package io.naz.fieldshift.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ClimateRangeTest {
  private fun rangeOn(y: Int, m: Int, d: Int) = ClimateRange.lastCompleteYears(LocalDate.of(y, m, d))

  @Test
  fun `uses the ten most recent complete years`() {
    assertEquals(YearRange(2016, 2025), rangeOn(2026, 11, 11))
    assertEquals(10, rangeOn(2026, 11, 11).count)
  }

  @Test
  fun `a year is only used once NASA has had three months to finish it`() {
    assertEquals(YearRange(2016, 2025), rangeOn(2027, 1, 15)) // 2026 just ended – not yet
    assertEquals(YearRange(2016, 2025), rangeOn(2027, 3, 31))
    assertEquals(YearRange(2017, 2026), rangeOn(2027, 4, 1))
  }

  @Test
  fun `works on a leap day and with a custom length`() {
    assertEquals(YearRange(2017, 2026), rangeOn(2028, 2, 29))
    assertEquals(YearRange(2021, 2025), ClimateRange.lastCompleteYears(LocalDate.of(2026, 11, 11), count = 5))
  }
}
