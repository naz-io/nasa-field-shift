package io.naz.fieldshift

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Start screen: point at your field on the map. */
@Serializable data object FieldPicker : NavKey

/**
 * Climate summary for one field. The key carries the field itself, so the screen needs nothing
 * else – and because keys are @Serializable, Nav3 can restore the back stack after process death.
 */
@Serializable
data class Climate(val name: String, val latitude: Double, val longitude: Double) : NavKey
