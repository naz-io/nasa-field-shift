package io.naz.fieldshift

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** One key per screen. More get added as the app grows (priorities, comparison, map…). */
@Serializable data object Climate : NavKey
