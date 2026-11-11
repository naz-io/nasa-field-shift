package io.naz.fieldshift.ui.picker

import io.naz.fieldshift.data.FieldRepository
import io.naz.fieldshift.data.location.LocationProvider
import io.naz.fieldshift.data.location.LocationUnavailableException
import io.naz.fieldshift.data.location.PlaceResult
import io.naz.fieldshift.data.location.PlaceSearch
import io.naz.fieldshift.domain.FieldLocation
import io.naz.fieldshift.domain.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val GUELPH = GeoPoint(43.55, -80.25)
private val SAVED = FieldLocation("Near Guelph, Ontario", GUELPH)

@OptIn(ExperimentalCoroutinesApi::class)
class FieldPickerViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private class FakeFields(initial: FieldLocation?) : FieldRepository {
    override val lastField = MutableStateFlow(initial)

    override suspend fun save(field: FieldLocation) {
      lastField.value = field
    }
  }

  private class FakeSearch(
    val results: List<PlaceResult> = emptyList(),
    val name: String? = "Near Somewhere, Ontario",
  ) : PlaceSearch {
    var nameLookups = 0

    override suspend fun search(query: String) = results

    override suspend fun nameFor(point: GeoPoint): String? {
      nameLookups++
      return name
    }
  }

  private class FakeLocation(var permission: Boolean = true, var result: () -> GeoPoint = { GUELPH }) :
    LocationProvider {
    override fun hasPermission() = permission

    override suspend fun currentLocation() = result()
  }

  private fun TestScope.viewModel(
    saved: FieldLocation? = null,
    search: FakeSearch = FakeSearch(),
    location: FakeLocation = FakeLocation(),
    fields: FakeFields = FakeFields(saved),
  ) = FieldPickerViewModel(fields, search, location).also { advanceUntilIdle() }

  @Test
  fun `first launch starts over southern Ontario`() = runTest(dispatcher) {
    val state = viewModel().uiState.value
    assertTrue(state.ready)
    assertEquals(DEFAULT_START, state.startPoint)
    assertEquals(DEFAULT_ZOOM, state.startZoom)
  }

  @Test
  fun `returning user starts on the saved field`() = runTest(dispatcher) {
    val state = viewModel(saved = SAVED).uiState.value
    assertEquals(GUELPH, state.startPoint)
    assertEquals(FIELD_ZOOM, state.startZoom)
    assertEquals("Near Guelph, Ontario", state.placeName)
  }

  @Test
  fun `place name is looked up once the map has rested for the debounce time`() = runTest(dispatcher) {
    val search = FakeSearch(name = "Near Elora, Ontario")
    val vm = viewModel(search = search)

    vm.onCameraIdle(GeoPoint(43.7, -80.4))
    vm.onCameraIdle(GeoPoint(43.68, -80.43)) // map moved again before the debounce ran out
    runCurrent()
    assertTrue(vm.uiState.value.isResolvingName)
    assertEquals(0, search.nameLookups)

    advanceTimeBy(NAME_LOOKUP_DEBOUNCE_MS + 1)
    assertEquals(1, search.nameLookups) // only the latest position was looked up
    assertEquals("Near Elora, Ontario", vm.uiState.value.placeName)
    assertEquals(GeoPoint(43.68, -80.43), vm.uiState.value.target)
  }

  @Test
  fun `a failed name lookup is retried once`() = runTest(dispatcher) {
    var calls = 0
    val flaky =
      object : PlaceSearch {
        override suspend fun search(query: String) = emptyList<PlaceResult>()

        override suspend fun nameFor(point: GeoPoint) = if (++calls == 1) null else "Near Elora, Ontario"
      }
    val vm = FieldPickerViewModel(FakeFields(null), flaky, FakeLocation()).also { advanceUntilIdle() }
    calls = 0 // ignore the lookup for the start position

    vm.onCameraIdle(GeoPoint(43.68, -80.43))
    advanceUntilIdle()
    assertEquals(2, calls)
    assertEquals("Near Elora, Ontario", vm.uiState.value.placeName)
  }

  @Test
  fun `a single search result moves the map there`() = runTest(dispatcher) {
    val vm = viewModel(search = FakeSearch(results = listOf(PlaceResult("Guelph, ON", GUELPH))))
    vm.onQueryChange("Guelph")
    vm.search()
    advanceUntilIdle()

    val request = vm.uiState.value.cameraRequest
    assertEquals(GUELPH, request?.point)
    assertEquals(PLACE_ZOOM, request?.zoom)
    assertTrue(vm.uiState.value.results.isEmpty())

    vm.cameraRequestHandled(request!!.id)
    assertNull(vm.uiState.value.cameraRequest)
  }

  @Test
  fun `several search results are listed for the farmer to choose`() = runTest(dispatcher) {
    val results = listOf(PlaceResult("London, ON", GeoPoint(42.98, -81.25)), PlaceResult("London, UK", GeoPoint(51.5, -0.12)))
    val vm = viewModel(search = FakeSearch(results = results))
    vm.onQueryChange("London")
    vm.search()
    advanceUntilIdle()
    assertEquals(results, vm.uiState.value.results)
    assertNull(vm.uiState.value.cameraRequest)

    vm.choose(results[0])
    assertEquals(GeoPoint(42.98, -81.25), vm.uiState.value.cameraRequest?.point)
  }

  @Test
  fun `no search results shows a message`() = runTest(dispatcher) {
    val vm = viewModel()
    vm.onQueryChange("Atlantis")
    vm.search()
    advanceUntilIdle()
    assertNotNull(vm.uiState.value.message)
    vm.messageShown()
    assertNull(vm.uiState.value.message)
  }

  @Test
  fun `use my location moves the map to the device position`() = runTest(dispatcher) {
    val vm = viewModel()
    vm.useMyLocation()
    advanceUntilIdle()
    assertEquals(GUELPH, vm.uiState.value.cameraRequest?.point)
    assertEquals(FIELD_ZOOM, vm.uiState.value.cameraRequest?.zoom)
    assertFalse(vm.uiState.value.isLocating)
  }

  @Test
  fun `location failure shows a message and keeps the map usable`() = runTest(dispatcher) {
    val vm = viewModel(location = FakeLocation(result = { throw LocationUnavailableException("Location is turned off.") }))
    vm.useMyLocation()
    advanceUntilIdle()
    assertNull(vm.uiState.value.cameraRequest)
    assertTrue(vm.uiState.value.message!!.contains("turned off"))
    assertFalse(vm.uiState.value.isLocating)
  }

  @Test
  fun `confirm saves the field with its place name`() = runTest(dispatcher) {
    val fields = FakeFields(null)
    val vm = viewModel(fields = fields, search = FakeSearch(name = "Near Guelph, Ontario"))
    vm.onCameraIdle(GUELPH)
    advanceUntilIdle()

    val chosen = vm.confirm()
    advanceUntilIdle()
    assertEquals(SAVED, chosen)
    assertEquals(SAVED, fields.lastField.value)
  }

  @Test
  fun `confirm falls back to coordinates when no name is known`() = runTest(dispatcher) {
    val vm = viewModel(search = FakeSearch(name = null))
    vm.onCameraIdle(GUELPH)
    advanceUntilIdle()
    assertEquals("43.5500°N, 80.2500°W", vm.confirm().name)
  }
}
