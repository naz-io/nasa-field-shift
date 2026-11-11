package io.naz.fieldshift.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.naz.fieldshift.domain.FieldLocation
import io.naz.fieldshift.domain.GeoPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** DataStore runs on the plain JVM, so this needs no emulator – just a temporary file. */
class FieldRepositoryTest {
  @get:Rule val tmp = TemporaryFolder()

  @Test
  fun `saves and reads back the last field`() = runTest {
    val store =
      PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("field.preferences_pb") }
    val repository = DataStoreFieldRepository(store)

    assertNull(repository.lastField.first())

    val field = FieldLocation("Near Guelph, Ontario", GeoPoint(43.55, -80.25))
    repository.save(field)
    assertEquals(field, repository.lastField.first())

    val moved = FieldLocation("Near Clare, South Australia", GeoPoint(-33.83, 138.6))
    repository.save(moved)
    assertEquals(moved, repository.lastField.first())
  }
}
