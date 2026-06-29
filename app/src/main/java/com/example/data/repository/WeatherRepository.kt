package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.WeatherApi
import com.example.data.local.FavoriteCity
import com.example.data.local.FavoriteCityDao
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class WeatherRepository(private val favoriteCityDao: FavoriteCityDao) {

    private val api: WeatherApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        api = retrofit.create(WeatherApi::class.java)
    }

    // API Key Configuration check
    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.OPENWEATHER_API_KEY
        return key.isNotBlank() && key != "MY_OPENWEATHER_API_KEY" && !key.startsWith("placeholder", ignoreCase = true)
    }

    private fun getApiKey(): String {
        return BuildConfig.OPENWEATHER_API_KEY
    }

    // Core Live Fetches
    suspend fun fetchCurrentWeather(city: String): CurrentWeatherResponse {
        return api.getCurrentWeatherByCity(city, getApiKey())
    }

    suspend fun fetchCurrentWeather(lat: Double, lon: Double): CurrentWeatherResponse {
        return api.getCurrentWeatherByCoordinates(lat, lon, getApiKey())
    }

    suspend fun fetchForecast(city: String): ForecastResponse {
        return api.getForecastByCity(city, getApiKey())
    }

    suspend fun fetchForecast(lat: Double, lon: Double): ForecastResponse {
        return api.getForecastByCoordinates(lat, lon, getApiKey())
    }

    suspend fun fetchAirPollution(lat: Double, lon: Double): AirPollutionResponse {
        return api.getAirPollution(lat, lon, getApiKey())
    }

    // Local DB Operations for Multi-City Speed Dial
    val favoriteCities: Flow<List<FavoriteCity>> = favoriteCityDao.getAllFavorites()

    suspend fun addFavoriteCity(name: String) {
        val normalizedName = name.trim().split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        favoriteCityDao.insertFavorite(FavoriteCity(name = normalizedName))
    }

    suspend fun removeFavoriteCity(name: String) {
        favoriteCityDao.deleteFavorite(FavoriteCity(name = name))
    }

    suspend fun isFavorite(name: String): Boolean {
        return favoriteCityDao.isFavorite(name)
    }
}
