package io.naz.fieldshift.ui.climate

import io.naz.fieldshift.data.ClimateRepository
import io.naz.fieldshift.domain.DailyWeather
import io.naz.fieldshift.domain.FieldLocation
import io.naz.fieldshift.domain.FrostStats
import io.naz.fieldshift.domain.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 11 Nov 2026 – so the expected range is 2016–2025. */
private val CLOCK = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC)
private val FIELD = FieldLocation("Near Guelph, Ontario", GeoPoint(43.55, -80.25))

@OptIn(ExperimentalCoroutinesApi::class)
class ClimateViewModelTest {

  // viewModelScope uses Dispatchers.Main, which doesn't exist on the JVM – swap in a test one.
  @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

  @After fun tearDown() = Dispatchers.resetMain()

  private class FakeRepository(var result: () -> List<DailyWeather>) : ClimateRepository {
    var requestedYears: Pair<Int, Int>? = null

    override suspend fun getDailyWeather(
      latitude: Double,
      longitude: Double,
      startYear: Int,
      endYear: Int,
    ): List<DailyWeather> {
      requestedYears = startYear to endYear
      return result()
    }
  }

  // Three complete years: the minimum the calculator needs for an average.
  private val threeYears =
    generateSequence(LocalDate.of(2021, 1, 1)) { it.plusDays(1) }
      .takeWhile { it.year <= 2023 }
      .map { DailyWeather(it, 10.0, 5.0, 2.0) }
      .toList()

  @Test
  fun `starts loading then shows summary`() = runTest {
    val viewModel = ClimateViewModel(FakeRepository { threeYears }, FIELD, CLOCK)
    assertEquals(ClimateUiState.Loading, viewModel.uiState.value)

    advanceUntilIdle()

    val state = viewModel.uiState.value as ClimateUiState.Success
    assertEquals(730.0, state.summary.precipitation!!.avgAnnualMm, 0.001)
  }

  @Test
  fun `asks NASA for the ten most recent complete years`() = runTest {
    val repository = FakeRepository { threeYears }
    val viewModel = ClimateViewModel(repository, FIELD, CLOCK)
    advanceUntilIdle()
    assertEquals(2016 to 2025, repository.requestedYears)
    assertEquals(10, viewModel.years.count)
  }

  @Test
  fun `too little data is a successful load that says so, not an error`() = runTest {
    val viewModel = ClimateViewModel(FakeRepository { threeYears.take(400) }, FIELD, CLOCK)
    advanceUntilIdle()

    val summary = (viewModel.uiState.value as ClimateUiState.Success).summary
    assertEquals(null, summary.precipitation)
    assertEquals(FrostStats.InsufficientData(1), summary.frost)
  }

  @Test
  fun `network failure shows error and retry recovers`() = runTest {
    val repository = FakeRepository { throw IOException("offline") }
    val viewModel = ClimateViewModel(repository, FIELD, CLOCK)
    advanceUntilIdle()
    assertEquals(ClimateUiState.Error("offline"), viewModel.uiState.value)

    repository.result = { threeYears }
    viewModel.load()
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value is ClimateUiState.Success)
  }
}
