package io.naz.fieldshift.ui.climate

import io.naz.fieldshift.data.ClimateRepository
import io.naz.fieldshift.domain.DailyWeather
import io.naz.fieldshift.domain.FieldLocation
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
import java.time.LocalDate

private val FIELD = FieldLocation("Near Guelph, Ontario", GeoPoint(43.55, -80.25))

@OptIn(ExperimentalCoroutinesApi::class)
class ClimateViewModelTest {

  // viewModelScope uses Dispatchers.Main, which doesn't exist on the JVM – swap in a test one.
  @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

  @After fun tearDown() = Dispatchers.resetMain()

  private class FakeRepository(var result: () -> List<DailyWeather>) : ClimateRepository {
    override suspend fun getDailyWeather(
      latitude: Double,
      longitude: Double,
      startYear: Int,
      endYear: Int,
    ) = result()
  }

  private val oneYear =
    (0L until 365).map { DailyWeather(LocalDate.of(2021, 1, 1).plusDays(it), 10.0, 5.0, 2.0) }

  @Test
  fun `starts loading then shows summary`() = runTest {
    val viewModel = ClimateViewModel(FakeRepository { oneYear }, FIELD)
    assertEquals(ClimateUiState.Loading, viewModel.uiState.value)

    advanceUntilIdle()

    val state = viewModel.uiState.value as ClimateUiState.Success
    assertEquals(730.0, state.summary.avgAnnualPrecipMm, 0.001)
  }

  @Test
  fun `network failure shows error and retry recovers`() = runTest {
    val repository = FakeRepository { throw IOException("offline") }
    val viewModel = ClimateViewModel(repository, FIELD)
    advanceUntilIdle()
    assertEquals(ClimateUiState.Error("offline"), viewModel.uiState.value)

    repository.result = { oneYear }
    viewModel.load()
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value is ClimateUiState.Success)
  }
}
