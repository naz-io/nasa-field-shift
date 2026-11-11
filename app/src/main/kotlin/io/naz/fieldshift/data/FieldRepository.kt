package io.naz.fieldshift.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.naz.fieldshift.domain.FieldLocation
import io.naz.fieldshift.domain.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Remembers the last field the farmer picked, so the app reopens on it. */
interface FieldRepository {
  /** Emits null until a field has been saved. */
  val lastField: Flow<FieldLocation?>

  suspend fun save(field: FieldLocation)
}

/**
 * DataStore Preferences is a small, coroutine-based key-value store – the modern replacement for
 * SharedPreferences. Reads are a Flow, writes are suspend functions, and it never blocks the UI.
 */
class DataStoreFieldRepository(private val store: DataStore<Preferences>) : FieldRepository {

  override val lastField: Flow<FieldLocation?> =
    store.data.map { prefs ->
      val lat = prefs[LATITUDE] ?: return@map null
      val lon = prefs[LONGITUDE] ?: return@map null
      FieldLocation(name = prefs[NAME].orEmpty(), point = GeoPoint(lat, lon))
    }

  override suspend fun save(field: FieldLocation) {
    store.edit { prefs ->
      prefs[LATITUDE] = field.latitude
      prefs[LONGITUDE] = field.longitude
      prefs[NAME] = field.name
    }
  }

  private companion object {
    val LATITUDE = doublePreferencesKey("field_latitude")
    val LONGITUDE = doublePreferencesKey("field_longitude")
    val NAME = stringPreferencesKey("field_name")
  }
}
