package io.naz.fieldshift.ui.picker

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import io.naz.fieldshift.R
import io.naz.fieldshift.appContainer
import io.naz.fieldshift.domain.FieldLocation
import io.naz.fieldshift.domain.GeoPoint
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
private fun fieldPickerViewModel(): FieldPickerViewModel {
  val container = LocalContext.current.appContainer
  return viewModel {
    FieldPickerViewModel(container.fieldRepository, container.placeSearch, container.locationProvider)
  }
}

@Composable
fun FieldPickerScreen(
  onFieldChosen: (FieldLocation) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: FieldPickerViewModel = fieldPickerViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }

  LaunchedEffect(state.message) {
    state.message?.let {
      snackbar.showSnackbar(it)
      viewModel.messageShown()
    }
  }

  // Ask for location only when the farmer taps the button. Either permission is enough:
  // on Android 12+ the farmer may grant only "approximate", which is fine for finding a field.
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
      if (grants.values.any { it }) viewModel.useMyLocation() else viewModel.onLocationPermissionDenied()
    }

  // The map is meant to draw edge to edge, under the status and navigation bars, so the Scaffold
  // gets no window insets (its padding is then just zero). The search bar and bottom card pad
  // themselves with statusBarsPadding()/navigationBarsPadding().
  Scaffold(
    modifier = modifier,
    snackbarHost = { SnackbarHost(snackbar) },
    contentWindowInsets = WindowInsets(0),
  ) { padding ->
    if (!state.ready) {
      Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      PickerContent(
        modifier = Modifier.padding(padding),
        state = state,
        onCameraIdle = viewModel::onCameraIdle,
        onCameraRequestHandled = viewModel::cameraRequestHandled,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onChoose = viewModel::choose,
        onMyLocation = {
          if (viewModel.hasLocationPermission()) viewModel.useMyLocation()
          else
            permissionLauncher.launch(
              arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        },
        onConfirm = { onFieldChosen(viewModel.confirm()) },
      )
    }
  }
}

@Composable
private fun PickerContent(
  state: FieldPickerUiState,
  modifier: Modifier = Modifier,
  onCameraIdle: (GeoPoint) -> Unit,
  onCameraRequestHandled: (Long) -> Unit,
  onQueryChange: (String) -> Unit,
  onSearch: () -> Unit,
  onChoose: (io.naz.fieldshift.data.location.PlaceResult) -> Unit,
  onMyLocation: () -> Unit,
  onConfirm: () -> Unit,
) {
  // rememberCameraPositionState is saveable, so the map keeps its position across rotation.
  val cameraState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(state.startPoint.toLatLng(), state.startZoom)
  }
  // Satellite is the default: farmers recognise their own fields from above.
  var satellite by rememberSaveable { mutableStateOf(true) }
  val scope = rememberCoroutineScope()

  // Report the pin position every time the map comes to rest.
  LaunchedEffect(cameraState) {
    snapshotFlow { cameraState.isMoving }
      .filter { moving -> !moving }
      .collect { onCameraIdle(cameraState.position.target.toGeoPoint()) }
  }

  // Move the map when the ViewModel asks (search result, "use my location").
  LaunchedEffect(state.cameraRequest) {
    val request = state.cameraRequest ?: return@LaunchedEffect
    cameraState.animateTo(request.point, request.zoom)
    onCameraRequestHandled(request.id)
  }

  Box(modifier.fillMaxSize()) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraState,
      properties = MapProperties(mapType = if (satellite) MapType.HYBRID else MapType.NORMAL),
      uiSettings =
        MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
      onMapClick = { latLng -> scope.launch { cameraState.animate(CameraUpdateFactory.newLatLng(latLng)) } },
    )

    // The pin is fixed in the centre; the map moves under it. Offset by half its height so the
    // pin's tip – not its middle – marks the exact point.
    Icon(
      painter = painterResource(R.drawable.ic_pin),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.align(Alignment.Center).size(48.dp).offset(y = (-24).dp),
    )

    Column(
      modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SearchBox(state, onQueryChange, onSearch)
      if (state.results.isNotEmpty()) SearchResults(state.results, onChoose)
      FilterChip(
        selected = satellite,
        onClick = { satellite = !satellite },
        label = { Text("Satellite") },
        colors =
          FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    }

    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      FloatingActionButton(onClick = onMyLocation) {
        if (state.isLocating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else Icon(painterResource(R.drawable.ic_my_location), contentDescription = "Use my location")
      }
      SelectionCard(state, onConfirm)
    }
  }
}

@Composable
private fun SearchBox(state: FieldPickerUiState, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
  val focusManager = LocalFocusManager.current
  Surface(shape = RoundedCornerShape(28.dp), shadowElevation = 4.dp) {
    TextField(
      value = state.query,
      onValueChange = onQueryChange,
      modifier = Modifier.fillMaxWidth(),
      placeholder = { Text("Search a town or road") },
      leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
      trailingIcon = {
        if (state.isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions =
        KeyboardActions(
          onSearch = {
            onSearch()
            focusManager.clearFocus()
          }
        ),
      colors =
        TextFieldDefaults.colors(
          focusedIndicatorColor = MaterialTheme.colorScheme.surface,
          unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
        ),
    )
  }
}

@Composable
private fun SearchResults(
  results: List<io.naz.fieldshift.data.location.PlaceResult>,
  onChoose: (io.naz.fieldshift.data.location.PlaceResult) -> Unit,
) {
  Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp) {
    Column {
      results.forEachIndexed { index, result ->
        if (index > 0) HorizontalDivider()
        ListItem(
          headlineContent = { Text(result.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
          modifier = Modifier.clickable { onChoose(result) },
        )
      }
    }
  }
}

@Composable
private fun SelectionCard(state: FieldPickerUiState, onConfirm: () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text =
          state.placeName
            ?: if (state.isResolvingName) "Finding place name…" else "Place name not available",
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        state.target.display(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        "Move the map so the pin sits on your field.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp)) {
        Text("Use this field")
      }
    }
  }
}

private suspend fun CameraPositionState.animateTo(point: GeoPoint, zoom: Float) =
  animate(CameraUpdateFactory.newLatLngZoom(point.toLatLng(), zoom), durationMs = 800)

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

private fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)
