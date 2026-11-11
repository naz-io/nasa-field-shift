package com.naz.fieldshift

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.naz.fieldshift.ui.climate.ClimateScreen

@Composable
fun MainNavigation() {
  // Navigation 3: the back stack is just a list of keys that we own.
  val backStack = rememberNavBackStack(Climate)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider { entry<Climate> { ClimateScreen() } },
  )
}
