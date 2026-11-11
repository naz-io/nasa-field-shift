package com.naz.fieldshift.data

import com.naz.fieldshift.data.remote.PowerResponse
import com.naz.fieldshift.domain.DailyWeather
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PowerResponseMappingTest {
  // Trimmed copy of a real response, with one value replaced by the fill value.
  private val sample =
    """
    {
      "type": "Feature",
      "geometry": {"type": "Point", "coordinates": [-79.4, 43.7, 98.64]},
      "properties": {
        "parameter": {
          "T2M_MAX": {"20200101": 1.83, "20200102": 6.33},
          "T2M_MIN": {"20200101": -0.35, "20200102": 2.33},
          "PRECTOTCORR": {"20200101": 1.27, "20200102": -999.0}
        }
      },
      "header": {"title": "NASA/POWER", "fill_value": -999.0, "start": "20200101"},
      "messages": []
    }
    """

  @Test
  fun `parses json and maps to daily weather with fill value as null`() {
    val response = Json { ignoreUnknownKeys = true }.decodeFromString<PowerResponse>(sample)

    assertEquals(
      listOf(
        DailyWeather(LocalDate.of(2020, 1, 1), 1.83, -0.35, 1.27),
        DailyWeather(LocalDate.of(2020, 1, 2), 6.33, 2.33, null),
      ),
      response.toDailyWeather(),
    )
  }
}
