package io.naz.fieldshift

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.naz.fieldshift.data.ClimateRepository
import io.naz.fieldshift.data.DataStoreFieldRepository
import io.naz.fieldshift.data.FieldRepository
import io.naz.fieldshift.data.PowerClimateRepository
import io.naz.fieldshift.data.location.FusedLocationProvider
import io.naz.fieldshift.data.location.GeocoderPlaceSearch
import io.naz.fieldshift.data.location.LocationProvider
import io.naz.fieldshift.data.location.PlaceSearch
import io.naz.fieldshift.data.remote.PowerApi

/** One DataStore per file per process – the delegate guarantees that. */
private val Context.fieldDataStore by preferencesDataStore(name = "field")

/**
 * Manual dependency injection: every app-wide object is created once, here.
 * ViewModels get what they need from this container. In milestone 2 each `val` below becomes a
 * Hilt @Provides/@Binds, and this class disappears.
 */
class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  val climateRepository: ClimateRepository by lazy { PowerClimateRepository(PowerApi.create()) }
  val fieldRepository: FieldRepository by lazy { DataStoreFieldRepository(appContext.fieldDataStore) }
  val placeSearch: PlaceSearch by lazy { GeocoderPlaceSearch(appContext) }
  val locationProvider: LocationProvider by lazy { FusedLocationProvider(appContext) }
}
