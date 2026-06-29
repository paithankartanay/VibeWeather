package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FavoriteCity
import com.example.data.local.WeatherDatabase
import com.example.data.model.*
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(
        val currentWeather: CurrentWeatherResponse,
        val forecast: ForecastResponse,
        val airPollution: AirPollutionResponse,
        val isMock: Boolean
    ) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WeatherRepository

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Last search/fetch parameters to support accurate swipe-to-refresh
    private var lastFetchType: FetchType = FetchType.City("Amravati")

    sealed interface FetchType {
        data class City(val name: String) : FetchType
        data class Coordinates(val lat: Double, val lon: Double) : FetchType
    }

    val favoriteCities: StateFlow<List<FavoriteCity>>

    init {
        val database = WeatherDatabase.getDatabase(application)
        repository = WeatherRepository(database.favoriteCityDao())

        favoriteCities = repository.favoriteCities
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Add some default speed-dial favorites if the DB is empty
        viewModelScope.launch {
            repository.favoriteCities.first().let { currentFavs ->
                if (currentFavs.isEmpty()) {
                    repository.addFavoriteCity("Amravati")
                    repository.addFavoriteCity("Pune")
                    repository.addFavoriteCity("Mumbai")
                }
            }
            // Trigger initial load on startup
            loadDefaultWeather()
        }
    }

    private fun loadDefaultWeather() {
        // Default to Amravati
        fetchWeatherForCity("Amravati")
    }

    fun isApiKeyConfigured(): Boolean {
        return repository.isApiKeyConfigured()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun fetchWeatherForCity(cityName: String) {
        if (cityName.isBlank()) return
        lastFetchType = FetchType.City(cityName)
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                if (repository.isApiKeyConfigured()) {
                    val weather = repository.fetchCurrentWeather(cityName)
                    val forecast = repository.fetchForecast(cityName)
                    val aqi = repository.fetchAirPollution(weather.coord.lat, weather.coord.lon)
                    
                    _uiState.value = WeatherUiState.Success(
                        currentWeather = weather,
                        forecast = forecast,
                        airPollution = aqi,
                        isMock = false
                    )
                } else {
                    // Load deterministic mock weather data
                    _uiState.value = generateMockWeather(cityName)
                }
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Error fetching weather for city: $cityName", e)
                _uiState.value = WeatherUiState.Error(
                    e.localizedMessage ?: "Failed to connect to weather service. Please check your internet connection."
                )
            }
        }
    }

    fun fetchWeatherByCoordinates(lat: Double, lon: Double) {
        lastFetchType = FetchType.Coordinates(lat, lon)
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                if (repository.isApiKeyConfigured()) {
                    val weather = repository.fetchCurrentWeather(lat, lon)
                    val forecast = repository.fetchForecast(lat, lon)
                    val aqi = repository.fetchAirPollution(lat, lon)
                    
                    _uiState.value = WeatherUiState.Success(
                        currentWeather = weather,
                        forecast = forecast,
                        airPollution = aqi,
                        isMock = false
                    )
                } else {
                    // Generate mock city name based on coordinates or simply "My Location"
                    _uiState.value = generateMockWeather("My Location", lat, lon)
                }
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Error fetching weather by coords: $lat, $lon", e)
                _uiState.value = WeatherUiState.Error(
                    e.localizedMessage ?: "Failed to retrieve location weather."
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val currentType = lastFetchType
                if (repository.isApiKeyConfigured()) {
                    when (currentType) {
                        is FetchType.City -> {
                            val weather = repository.fetchCurrentWeather(currentType.name)
                            val forecast = repository.fetchForecast(currentType.name)
                            val aqi = repository.fetchAirPollution(weather.coord.lat, weather.coord.lon)
                            _uiState.value = WeatherUiState.Success(weather, forecast, aqi, false)
                        }
                        is FetchType.Coordinates -> {
                            val weather = repository.fetchCurrentWeather(currentType.lat, currentType.lon)
                            val forecast = repository.fetchForecast(currentType.lat, currentType.lon)
                            val aqi = repository.fetchAirPollution(currentType.lat, currentType.lon)
                            _uiState.value = WeatherUiState.Success(weather, forecast, aqi, false)
                        }
                    }
                } else {
                    // Simple refresh with deterministic delay
                    kotlinx.coroutines.delay(1000)
                    when (currentType) {
                        is FetchType.City -> {
                            _uiState.value = generateMockWeather(currentType.name)
                        }
                        is FetchType.Coordinates -> {
                            _uiState.value = generateMockWeather("My Location", currentType.lat, currentType.lon)
                        }
                    }
                }
            } catch (e: Exception) {
                // If refresh fails, keep current success if possible or show error
                if (_uiState.value !is WeatherUiState.Success) {
                    _uiState.value = WeatherUiState.Error(e.localizedMessage ?: "Refresh failed.")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun toggleFavorite(cityName: String) {
        viewModelScope.launch {
            val isFav = repository.isFavorite(cityName)
            if (isFav) {
                repository.removeFavoriteCity(cityName)
            } else {
                repository.addFavoriteCity(cityName)
            }
        }
    }

    fun isCityFavorite(cityName: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            callback(repository.isFavorite(cityName))
        }
    }

    // Helper to generate deterministic mock data based on the city name hash
    private fun generateMockWeather(
        cityName: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): WeatherUiState.Success {
        val seed = cityName.lowercase().trim().hashCode().toLong()
        val random = Random(seed)

        // Select coordinates based on seed if not provided
        val lat = latitude ?: (18.0 + random.nextDouble() * 12.0) // India range roughly
        val lon = longitude ?: (72.0 + random.nextDouble() * 12.0)

        // Base temp ranges from 15C to 38C based on seed
        val baseTemp = 18.0 + random.nextDouble() * 18.0
        val temp = Math.round(baseTemp * 10.0) / 10.0
        val feelsLike = Math.round((temp + (random.nextDouble() * 3.0 - 1.5)) * 10.0) / 10.0
        val humidity = 40 + random.nextInt(51) // 40% - 90%
        val pressure = 998 + random.nextInt(15)
        val windSpeed = Math.round((1.5 + random.nextDouble() * 8.0) * 10.0) / 10.0

        // Determine weather condition based on city name or seed
        val conditions = listOf("Clear", "Clouds", "Rain", "Haze", "Thunderstorm", "Drizzle")
        val conditionIndex = random.nextInt(conditions.size)
        val mainCondition = conditions[conditionIndex]

        val conditionDesc = when (mainCondition) {
            "Clear" -> "Clear Sky"
            "Clouds" -> "Scattered Clouds"
            "Rain" -> "Moderate Rain"
            "Haze" -> "Hazy Atmosphere"
            "Thunderstorm" -> "Thunderstorm with Rain"
            "Drizzle" -> "Light Drizzle"
            else -> "Overcast"
        }

        val icon = when (mainCondition) {
            "Clear" -> "01d"
            "Clouds" -> "03d"
            "Rain" -> "10d"
            "Haze" -> "50d"
            "Thunderstorm" -> "11d"
            "Drizzle" -> "09d"
            else -> "02d"
        }

        val weatherResponse = CurrentWeatherResponse(
            cityName = cityName.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } },
            coord = Coordinates(lat, lon),
            weather = listOf(WeatherCondition(mainCondition, conditionDesc, icon)),
            main = MainTempInfo(temp, feelsLike, humidity, pressure),
            wind = WindInfo(windSpeed),
            timestamp = System.currentTimeMillis() / 1000
        )

        // Generate 24-hour forecast in 3-hour chunks (8 steps)
        val forecastItems = ArrayList<ForecastItem>()
        val currentTime = System.currentTimeMillis()
        for (i in 0 until 8) {
            val forecastTimestamp = currentTime + (i * 3 * 3600 * 1000)
            // Temperature dips at night and rises in midday
            val hourOffset = (i * 3) % 24
            val tempVariation = when (hourOffset) {
                in 0..6 -> -4.0 - random.nextDouble() * 2.0  // late night/early morning cold
                in 7..11 -> -1.0 + random.nextDouble() * 2.0  // warming up
                in 12..17 -> 2.0 + random.nextDouble() * 4.0   // peak hot
                else -> 0.0 - random.nextDouble() * 2.0       // cooling down
            }
            val forecastTemp = Math.round((temp + tempVariation) * 10.0) / 10.0

            // Change weather condition slightly for forecast
            val forecastCondition = if (i % 3 == 0) {
                conditions[(conditionIndex + 1) % conditions.size]
            } else {
                mainCondition
            }
            
            val forecastDesc = when (forecastCondition) {
                "Clear" -> "Clear Sky"
                "Clouds" -> "Few Clouds"
                "Rain" -> "Showers"
                "Haze" -> "Mist"
                "Thunderstorm" -> "Stormy"
                else -> "Overcast"
            }

            forecastItems.add(
                ForecastItem(
                    timestamp = forecastTimestamp / 1000,
                    main = ForecastTempInfo(forecastTemp),
                    weather = listOf(WeatherCondition(forecastCondition, forecastDesc, icon)),
                    dtTxt = "Hour +${i * 3}"
                )
            )
        }
        val forecastResponse = ForecastResponse(forecastItems)

        // AQI (1 to 5)
        val aqi = 1 + random.nextInt(5)
        val airPollutionResponse = AirPollutionResponse(
            list = listOf(AirPollutionItem(main = AqiInfo(aqi)))
        )

        return WeatherUiState.Success(
            currentWeather = weatherResponse,
            forecast = forecastResponse,
            airPollution = airPollutionResponse,
            isMock = true
        )
    }
}
