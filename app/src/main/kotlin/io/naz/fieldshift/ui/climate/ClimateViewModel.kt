package io.naz.fieldshift.ui.climate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.naz.fieldshift.data.ClimateRepository
import io.naz.fieldshift.domain.ClimateCalculator
import io.naz.fieldshift.domain.ClimateRange
import io.naz.fieldshift.domain.ClimateSummary
import io.naz.fieldshift.domain.FieldLocation
import io.naz.fieldshift.domain.YearRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.LocalDate


const val OFFLINE_MESSAGE = "Can't reach NASA right now. Check your internet connection and try again."
const val UNAVAILABLE_MESSAGE = "NASA's climate service didn't answer as expected. Please try again later."

/** A sealed interface makes `when` exhaustive: the screen must handle every state. */
sealed interface ClimateUiState {
  data object Loading : ClimateUiState

  data class Success(val summary: ClimateSummary) : ClimateUiState

  data class Error(val message: String) : ClimateUiState
}

class ClimateViewModel(
  private val repository: ClimateRepository,
  val location: FieldLocation,
  /** Injected so tests can fix "today"; the app passes the system clock. */
  clock: Clock,
) : ViewModel() {

  // Private mutable flow, public read-only flow: only the ViewModel can change state.
  /** The years we ask NASA POWER for, e.g. 2016–2025. */
  val years: YearRange = ClimateRange.lastCompleteYears(LocalDate.now(clock))

  private val _uiState = MutableStateFlow<ClimateUiState>(ClimateUiState.Loading)
  val uiState: StateFlow<ClimateUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  /** Also called by the Retry button. */
  fun load() {
    _uiState.value = ClimateUiState.Loading
    // viewModelScope survives rotation and is cancelled when the screen is gone for good.
    viewModelScope.launch {
      _uiState.value =
        try {
          val days =
            repository.getDailyWeather(location.latitude, location.longitude, years.first, years.last)
          ClimateUiState.Success(ClimateCalculator.summarize(days, location.latitude))
        } catch (e: CancellationException) {
          throw e // never swallow cancellation – it's how coroutines stop
        } catch (e: IOException) {
          // No connection, DNS failure, timeout: the farmer can fix these, so say how.
          ClimateUiState.Error(OFFLINE_MESSAGE)
        } catch (e: Exception) {
          // Anything else (e.g. NASA returned something unexpected) isn't fixable by the farmer.
          ClimateUiState.Error(UNAVAILABLE_MESSAGE)
        }
    }
  }
}
