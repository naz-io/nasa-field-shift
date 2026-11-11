package io.naz.fieldshift.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.naz.fieldshift.data.FieldRepository
import io.naz.fieldshift.data.location.LocationProvider
import io.naz.fieldshift.data.location.PlaceResult
import io.naz.fieldshift.data.location.PlaceSearch
import io.naz.fieldshift.domain.FieldLocation
import io.naz.fieldshift.domain.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** First-launch view: southern Ontario farmland, zoomed out so the farmer can find their area. */
val DEFAULT_START = GeoPoint(43.6, -80.6)
const val DEFAULT_ZOOM = 8f
const val FIELD_ZOOM = 15f
const val PLACE_ZOOM = 13f

/** Wait this long after the map stops before looking up a name, so we don't spam the geocoder. */
const val NAME_LOOKUP_DEBOUNCE_MS = 400L
const val NAME_LOOKUP_RETRY_MS = 1_500L

/**
 * A request for the screen to move the map. The [id] makes two requests to the same point distinct,
 * and lets the screen report back which request it has handled.
 */
data class CameraRequest(val id: Long, val point: GeoPoint, val zoom: Float)

data class FieldPickerUiState(
  /** False until we've read the saved field, so the map opens in the right place. */
  val ready: Boolean = false,
  val startPoint: GeoPoint = DEFAULT_START,
  val startZoom: Float = DEFAULT_ZOOM,
  /** Where the pin currently points (the centre of the map). */
  val target: GeoPoint = DEFAULT_START,
  val placeName: String? = null,
  val isResolvingName: Boolean = false,
  val query: String = "",
  val results: List<PlaceResult> = emptyList(),
  val isSearching: Boolean = false,
  val isLocating: Boolean = false,
  val cameraRequest: CameraRequest? = null,
  /** One message at a time for the snackbar; cleared with [FieldPickerViewModel.messageShown]. */
  val message: String? = null,
)

class FieldPickerViewModel(
  private val fieldRepository: FieldRepository,
  private val placeSearch: PlaceSearch,
  private val locationProvider: LocationProvider,
) : ViewModel() {

  private val _uiState = MutableStateFlow(FieldPickerUiState())
  val uiState: StateFlow<FieldPickerUiState> = _uiState.asStateFlow()

  private var nameJob: Job? = null
  private var nextRequestId = 0L

  init {
    viewModelScope.launch {
      val saved = fieldRepository.lastField.first()
      _uiState.update {
        if (saved == null) it.copy(ready = true)
        else
          it.copy(
            ready = true,
            startPoint = saved.point,
            startZoom = FIELD_ZOOM,
            target = saved.point,
            placeName = saved.name.ifBlank { null },
          )
      }
    }
  }

  /** The map has stopped moving with its centre at [point]. */
  fun onCameraIdle(point: GeoPoint) {
    if (point == _uiState.value.target && _uiState.value.placeName != null) return
    _uiState.update { it.copy(target = point, placeName = null, isResolvingName = true) }
    // Cancel the previous lookup: only the latest map position matters.
    nameJob?.cancel()
    nameJob =
      viewModelScope.launch {
        delay(NAME_LOOKUP_DEBOUNCE_MS)
        // The geocoder occasionally fails once (e.g. just after app start), so retry one time.
        val name =
          placeSearch.nameFor(point)
            ?: run {
              delay(NAME_LOOKUP_RETRY_MS)
              placeSearch.nameFor(point)
            }
        _uiState.update { it.copy(placeName = name, isResolvingName = false) }
      }
  }

  fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

  fun search() {
    val query = _uiState.value.query.trim()
    if (query.isEmpty()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isSearching = true, results = emptyList()) }
      val results = placeSearch.search(query)
      _uiState.update {
        it.copy(
          isSearching = false,
          results = if (results.size > 1) results else emptyList(),
          message = if (results.isEmpty()) "No places found for “$query”." else null,
        )
      }
      // One clear match: go straight there.
      results.singleOrNull()?.let(::choose)
    }
  }

  fun choose(result: PlaceResult) {
    _uiState.update { it.copy(results = emptyList(), query = result.label) }
    moveCamera(result.point, PLACE_ZOOM)
  }

  fun dismissResults() = _uiState.update { it.copy(results = emptyList()) }

  fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

  fun useMyLocation() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLocating = true) }
      try {
        moveCamera(locationProvider.currentLocation(), FIELD_ZOOM)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update { it.copy(message = "Couldn't get your location. ${e.message.orEmpty()}".trim()) }
      } finally {
        _uiState.update { it.copy(isLocating = false) }
      }
    }
  }

  fun onLocationPermissionDenied() =
    _uiState.update {
      it.copy(message = "No problem – search for a place or move the map to your field instead.")
    }

  fun cameraRequestHandled(id: Long) =
    _uiState.update { if (it.cameraRequest?.id == id) it.copy(cameraRequest = null) else it }

  fun messageShown() = _uiState.update { it.copy(message = null) }

  /** Saves the field (so the app reopens on it) and returns it for navigation. */
  fun confirm(): FieldLocation {
    val state = _uiState.value
    val field = FieldLocation(name = state.placeName ?: state.target.display(), point = state.target)
    viewModelScope.launch { fieldRepository.save(field) }
    return field
  }

  private fun moveCamera(point: GeoPoint, zoom: Float) =
    _uiState.update { it.copy(cameraRequest = CameraRequest(++nextRequestId, point, zoom)) }
}
