package com.example.data.api

import com.example.data.model.AirPollutionResponse
import com.example.data.model.CurrentWeatherResponse
import com.example.data.model.ForecastResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {

    @GET("data/2.5/weather")
    suspend fun getCurrentWeatherByCity(
        @Query("q") cityName: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): CurrentWeatherResponse

    @GET("data/2.5/weather")
    suspend fun getCurrentWeatherByCoordinates(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): CurrentWeatherResponse

    @GET("data/2.5/forecast")
    suspend fun getForecastByCoordinates(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): ForecastResponse

    @GET("data/2.5/forecast")
    suspend fun getForecastByCity(
        @Query("q") cityName: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): ForecastResponse

    @GET("data/2.5/air_pollution")
    suspend fun getAirPollution(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String
    ): AirPollutionResponse
}
