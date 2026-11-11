package io.naz.fieldshift.ui.climate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.naz.fieldshift.data.ClimateRepository
import io.naz.fieldshift.domain.ClimateCalculator
import io.naz.fieldshift.domain.ClimateSummary
import io.naz.fieldshift.domain.FieldLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The five most recent complete years. Moves forward when we add a refresh policy (M2). */
const val START_YEAR = 2020
const val END_YEAR = 2024

/** A sealed interface makes `when` exhaustive: the screen must handle every state. */
sealed interface ClimateUiState {
  data object Loading : ClimateUiState

  data class Success(val summary: ClimateSummary) : ClimateUiState

  data class Error(val message: String) : ClimateUiState
}

class ClimateViewModel(
  private val repository: ClimateRepository,
  val location: FieldLocation,
) : ViewModel() {

  // Private mutable flow, public read-only flow: only the ViewModel can change state.
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
            repository.getDailyWeather(location.latitude, location.longitude, START_YEAR, END_YEAR)
          ClimateUiState.Success(ClimateCalculator.summarize(days, location.latitude))
        } catch (e: CancellationException) {
          throw e // never swallow cancellation – it's how coroutines stop
        } catch (e: Exception) {
          ClimateUiState.Error(e.message ?: "Couldn't load climate data.")
        }
    }
  }
}
