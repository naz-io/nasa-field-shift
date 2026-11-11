package io.naz.fieldshift

import android.app.Application
import android.content.Context

/** Lives as long as the app process; a natural home for the dependency container. */
class FieldShiftApp : Application() {
  val container: AppContainer by lazy { AppContainer(this) }
}

/** Shortcut for composables: `LocalContext.current.appContainer`. */
val Context.appContainer: AppContainer
  get() = (applicationContext as FieldShiftApp).container
