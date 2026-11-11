package io.naz.fieldshift

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.naz.fieldshift.domain.FieldLocation
import io.naz.fieldshift.domain.GeoPoint
import io.naz.fieldshift.ui.climate.ClimateScreen
import io.naz.fieldshift.ui.picker.FieldPickerScreen

@Composable
fun MainNavigation() {
  // Navigation 3: the back stack is just a list of keys that we own.
  val backStack = rememberNavBackStack(FieldPicker)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // Decorators add per-entry behaviour. The ViewModel one gives every back-stack entry its own
    // ViewModelStore: each Climate(...) entry gets a fresh ClimateViewModel for *its* field, and
    // that ViewModel is cleared when the entry is popped.
    entryDecorators =
      listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider =
      entryProvider {
        entry<FieldPicker> {
          FieldPickerScreen(
            onFieldChosen = { field -> backStack.add(Climate(field.name, field.latitude, field.longitude)) }
          )
        }
        entry<Climate> { key ->
          ClimateScreen(
            location = FieldLocation(key.name, GeoPoint(key.latitude, key.longitude)),
            // The picker is still underneath us on the back stack, with its state intact.
            onChangeField = { backStack.removeLastOrNull() },
          )
        }
      },
  )
}
