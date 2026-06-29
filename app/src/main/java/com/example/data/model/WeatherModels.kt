package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CurrentWeatherResponse(
    @Json(name = "name") val cityName: String,
    @Json(name = "coord") val coord: Coordinates,
    @Json(name = "weather") val weather: List<WeatherCondition>,
    @Json(name = "main") val main: MainTempInfo,
    @Json(name = "wind") val wind: WindInfo,
    @Json(name = "dt") val timestamp: Long
)

@JsonClass(generateAdapter = true)
data class Coordinates(
    @Json(name = "lat") val lat: Double,
    @Json(name = "lon") val lon: Double
)

@JsonClass(generateAdapter = true)
data class WeatherCondition(
    @Json(name = "main") val main: String,
    @Json(name = "description") val description: String,
    @Json(name = "icon") val icon: String
)

@JsonClass(generateAdapter = true)
data class MainTempInfo(
    @Json(name = "temp") val temp: Double,
    @Json(name = "feels_like") val feelsLike: Double,
    @Json(name = "humidity") val humidity: Int,
    @Json(name = "pressure") val pressure: Int
)

@JsonClass(generateAdapter = true)
data class WindInfo(
    @Json(name = "speed") val speed: Double
)

@JsonClass(generateAdapter = true)
data class ForecastResponse(
    @Json(name = "list") val list: List<ForecastItem>
)

@JsonClass(generateAdapter = true)
data class ForecastItem(
    @Json(name = "dt") val timestamp: Long,
    @Json(name = "main") val main: ForecastTempInfo,
    @Json(name = "weather") val weather: List<WeatherCondition>,
    @Json(name = "dt_txt") val dtTxt: String
)

@JsonClass(generateAdapter = true)
data class ForecastTempInfo(
    @Json(name = "temp") val temp: Double
)

@JsonClass(generateAdapter = true)
data class AirPollutionResponse(
    @Json(name = "list") val list: List<AirPollutionItem>
)

@JsonClass(generateAdapter = true)
data class AirPollutionItem(
    @Json(name = "main") val main: AqiInfo
)

@JsonClass(generateAdapter = true)
data class AqiInfo(
    @Json(name = "aqi") val aqi: Int // 1 = Good, 2 = Fair, 3 = Moderate, 4 = Poor, 5 = Very Poor
)
