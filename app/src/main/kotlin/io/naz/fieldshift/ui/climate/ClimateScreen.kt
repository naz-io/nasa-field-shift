package io.naz.fieldshift.ui.climate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.naz.fieldshift.R
import io.naz.fieldshift.appContainer
import io.naz.fieldshift.domain.ClimateSummary
import io.naz.fieldshift.domain.FieldLocation
import io.naz.fieldshift.domain.FrostStats
import io.naz.fieldshift.domain.MonthRange
import io.naz.fieldshift.domain.PrecipitationStats
import io.naz.fieldshift.domain.GeoPoint
import io.naz.fieldshift.ui.theme.FieldShiftTheme
import java.time.Month
import java.time.MonthDay
import java.time.format.TextStyle
import java.util.Locale
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
private fun climateViewModel(location: FieldLocation): ClimateViewModel {
  val container = LocalContext.current.appContainer
  // Thanks to Nav3's ViewModel decorator, this store belongs to one Climate back-stack entry,
  // so each field gets its own ViewModel.
  return viewModel { ClimateViewModel(container.climateRepository, location) }
}

/** "Stateful" entry point: owns the ViewModel and collects its state. */
@Composable
fun ClimateScreen(
  location: FieldLocation,
  onChangeField: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ClimateViewModel = climateViewModel(location),
) {
  // Lifecycle-aware: stops collecting when the app is in the background.
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  ClimateScreen(
    location = viewModel.location,
    uiState = uiState,
    onRetry = viewModel::load,
    onChangeField = onChangeField,
    modifier = modifier,
  )
}

/** "Stateless" version: just data in, events out – easy to preview and test. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClimateScreen(
  location: FieldLocation,
  uiState: ClimateUiState,
  onRetry: () -> Unit,
  onChangeField: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = { TextButton(onClick = onChangeField) { Text("Change field") } },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.padding(innerPadding)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column {
        Text(location.name, style = MaterialTheme.typography.headlineSmall)
        Text(
          location.point.display(),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      when (uiState) {
        ClimateUiState.Loading ->
          Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        is ClimateUiState.Error -> {
          Text(uiState.message, color = MaterialTheme.colorScheme.error)
          Button(onClick = onRetry) { Text("Retry") }
        }
        is ClimateUiState.Success -> SummaryContent(uiState.summary)
      }
    }
  }
}

@Composable
private fun SummaryContent(summary: ClimateSummary) {
  val precip = summary.precipitation
  StatCard(
    title = "Precipitation",
    rows =
      if (precip == null) listOf("Not enough data" to "—")
      else
        listOf(
          "Average per year" to "${precip.avgAnnualMm.roundToInt()} mm",
          "Growing season (${precip.growingSeason.start.short()}–${precip.growingSeason.endInclusive.short()})" to
            (precip.avgGrowingSeasonMm?.let { "${it.roundToInt()} mm" } ?: "—"),
        ),
  )
  StatCard(
    title = "Frost",
    rows =
      when (val frost = summary.frost) {
        is FrostStats.InsufficientData -> listOf("Not enough data" to "—")
        is FrostStats.NoFrost -> listOf("No frost recorded" to "${frost.seasonsAnalysed} seasons")
        is FrostStats.Frost ->
          listOf(
            "Average last spring frost" to frost.avgLastSpringFrost.display(),
            "Average first fall frost" to frost.avgFirstFallFrost.display(),
            "Frost-free season" to (frost.avgFrostFreeDays?.let { "$it days" } ?: "—"),
          )
      },
  )
  Text(
    "Averages from NASA POWER (MERRA-2). " +
      "Precipitation includes snow. Values are for a grid cell about 50 km wide, " +
      "so your field may be frostier or drier – check local station records.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun StatCard(title: String, rows: List<Pair<String, String>>) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      rows.forEach { (label, value) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(label, style = MaterialTheme.typography.bodyLarge)
          Text(value, style = MaterialTheme.typography.bodyLarge)
        }
      }
    }
  }
}

private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d")

private fun MonthDay?.display(): String = this?.format(MONTH_DAY) ?: "—"

private fun Month.short(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

@Preview(showBackground = true)
@Composable
private fun ClimateScreenPreview() {
  FieldShiftTheme {
    ClimateScreen(
      location = FieldLocation("Near Guelph, Ontario", GeoPoint(43.55, -80.25)),
      uiState =
        ClimateUiState.Success(
          ClimateSummary(
            precipitation =
              PrecipitationStats(
                avgAnnualMm = 850.0,
                yearsAnalysed = 10,
                firstYear = 2016,
                lastYear = 2025,
                coverage = 0.999,
                growingSeason = MonthRange(Month.MAY, Month.SEPTEMBER),
                avgGrowingSeasonMm = 390.0,
              ),
            frost =
              FrostStats.Frost(
                avgLastSpringFrost = MonthDay.of(4, 28),
                avgFirstFallFrost = MonthDay.of(10, 20),
                avgFrostFreeDays = 174,
                seasonsWithFrost = 10,
                seasonsAnalysed = 10,
              ),
          )
        ),
      onRetry = {},
      onChangeField = {},
    )
  }
}
