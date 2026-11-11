package com.naz.fieldshift.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * NASA POWER daily point API.
 * Docs: https://power.larc.nasa.gov/docs/services/api/temporal/daily/
 *
 * Retrofit generates the implementation of this interface at runtime. Because the function is
 * `suspend`, Retrofit runs the call off the main thread for us and throws on HTTP/network errors.
 */
interface PowerApi {
  @GET("api/temporal/daily/point")
  suspend fun getDaily(
    @Query("latitude") latitude: Double,
    @Query("longitude") longitude: Double,
    /** yyyyMMdd */
    @Query("start") start: String,
    /** yyyyMMdd */
    @Query("end") end: String,
    @Query("parameters") parameters: String = "$T2M_MAX,$T2M_MIN,$PRECTOTCORR",
    @Query("community") community: String = "AG",
    @Query("format") format: String = "JSON",
  ): PowerResponse

  companion object {
    const val T2M_MAX = "T2M_MAX"
    const val T2M_MIN = "T2M_MIN"
    const val PRECTOTCORR = "PRECTOTCORR"

    fun create(): PowerApi {
      // The response has many fields we don't model (geometry, times, …) – ignore them
      // instead of crashing when NASA adds a new one.
      val json = Json { ignoreUnknownKeys = true }
      val client =
        OkHttpClient.Builder()
          // A 5-year daily request can take a while on POWER's side; default is 10 s.
          .readTimeout(30, TimeUnit.SECONDS)
          .build()
      return Retrofit.Builder()
        .baseUrl("https://power.larc.nasa.gov/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PowerApi::class.java)
    }
  }
}

/**
 * DTOs ("data transfer objects") mirror the JSON shape exactly. They stay in the data layer;
 * the rest of the app uses the cleaner domain model [com.naz.fieldshift.domain.DailyWeather].
 */
@Serializable
data class PowerResponse(
  val properties: PowerProperties,
  val header: PowerHeader = PowerHeader(),
)

@Serializable
data class PowerProperties(
  /** parameter name (e.g. "T2M_MIN") -> date "yyyyMMdd" -> value */
  val parameter: Map<String, Map<String, Double>>,
)

@Serializable
data class PowerHeader(
  /** POWER uses this number (normally -999) to mean "no data for this day". */
  @kotlinx.serialization.SerialName("fill_value") val fillValue: Double = -999.0,
)
