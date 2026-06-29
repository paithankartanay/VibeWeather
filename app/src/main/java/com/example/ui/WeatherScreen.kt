package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CurrentWeatherResponse
import com.example.data.model.ForecastItem
import com.example.data.model.WeatherCondition
import com.example.ui.viewmodel.WeatherUiState
import com.example.ui.viewmodel.WeatherViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import com.example.utils.LocationHelper
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val favoriteCities by viewModel.favoriteCities.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var activeSearchQuery by remember { mutableStateOf("") }
    var locationError by remember { mutableStateOf<String?>(null) }
    var isAqiInfoExpanded by remember { mutableStateOf(false) }

    // Custom Pull to Refresh offset state
    var pullOffsetY by remember { mutableStateOf(0f) }
    val pullThreshold = 200f

    LaunchedEffect(searchQuery) {
        activeSearchQuery = searchQuery
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000)) // Pitch Black background
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (pullOffsetY > pullThreshold) {
                            viewModel.refresh()
                        }
                        pullOffsetY = 0f
                    },
                    onDragCancel = {
                        pullOffsetY = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        // Only pull down if offset is positive, decay resistance as it goes down
                        if (dragAmount > 0 || pullOffsetY > 0) {
                            pullOffsetY = (pullOffsetY + dragAmount / 2).coerceIn(0f, 350f)
                        }
                    }
                )
            }
    ) {
        // App Core Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .offset(y = (pullOffsetY / 2).dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // API Key Warning Banner if in demo mode
            if (!viewModel.isApiKeyConfigured()) {
                DemoBanner()
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Search Bar & GPS Trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0A0A)) // Matte Dark Black
                    .border(BorderStroke(0.5.dp, Color(0x33FFFFFF)), RoundedCornerShape(16.dp)) // Glassmorphism thin border
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search icon",
                    tint = Color(0x88FFFFFF),
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                )

                TextField(
                    value = activeSearchQuery,
                    onValueChange = { activeSearchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search city (e.g. Pune, Mumbai)...",
                            color = Color(0x66FFFFFF),
                            fontSize = 14.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("city_search_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (activeSearchQuery.isNotBlank()) {
                                viewModel.fetchWeatherForCity(activeSearchQuery)
                                keyboardController?.hide()
                            }
                        }
                    )
                )

                if (activeSearchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { activeSearchQuery = "" },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = Color(0xAAFFFFFF)
                        )
                    }
                }

                // GPS Auto Location Button
                IconButton(
                    onClick = {
                        locationError = null
                        LocationHelper.getCurrentLocation(
                            context = context,
                            onSuccess = { lat, lon ->
                                viewModel.fetchWeatherByCoordinates(lat, lon)
                            },
                            onFailure = { err ->
                                Log.e("WeatherScreen", "Location error: ", err)
                                locationError = "GPS request failed. Ensure location services are enabled."
                                // Fallback to prompt user for system permission
                                onRequestLocationPermission()
                            }
                        )
                    },
                    modifier = Modifier
                        .testTag("gps_location_button")
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Auto-Location GPS",
                        tint = Color.White
                    )
                }
            }

            // Location Error Message banner
            if (locationError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F0B0B))
                        .border(BorderStroke(0.5.dp, Color(0xFFE57373)), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = Color(0xFFE57373)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = locationError ?: "",
                        color = Color(0xFFEF9A9A),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { locationError = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFFEF9A9A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-City Speed Dial row
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "FAVORITE CITIES",
                    color = Color(0x66FFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    items(favoriteCities) { city ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF0F0F0F))
                                .border(BorderStroke(0.5.dp, Color(0x22FFFFFF)), RoundedCornerShape(20.dp))
                                .combinedClickable(
                                    onClick = {
                                        viewModel.updateSearchQuery(city.name)
                                        viewModel.fetchWeatherForCity(city.name)
                                        keyboardController?.hide()
                                    },
                                    onLongClick = {
                                        viewModel.toggleFavorite(city.name)
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("favorite_city_tag_${city.name}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = city.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "Bookmarked",
                                    tint = Color(0x88FFFFFF),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // UI State Content Selector
            when (val state = uiState) {
                is WeatherUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                is WeatherUiState.Success -> {
                    SuccessWeatherView(
                        state = state,
                        viewModel = viewModel,
                        isAqiExpanded = isAqiInfoExpanded,
                        onToggleAqiExpanded = { isAqiInfoExpanded = !isAqiInfoExpanded }
                    )
                }

                is WeatherUiState.Error -> {
                    ErrorStateView(
                        errorMessage = state.message,
                        onRetry = {
                            if (activeSearchQuery.isNotBlank()) {
                                viewModel.fetchWeatherForCity(activeSearchQuery)
                            } else {
                                viewModel.refresh()
                            }
                        }
                    )
                }
            }
        }

        // Custom Pull To Refresh Minimalist Spinner
        if (pullOffsetY > 10f || isRefreshing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (pullOffsetY / 3).dp + 10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F0F0F))
                    .border(BorderStroke(0.5.dp, Color(0x44FFFFFF)), CircleShape)
                    .padding(8.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Pull to refresh",
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .drawBehind {
                                // Rotate slightly based on pull depth
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun DemoBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F0F0F))
            .border(BorderStroke(0.5.dp, Color(0x33FFFFFF)), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Demo Info",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Demo Mode Weather",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "API Key not set. Showing high-fidelity deterministic weather data. Insert your OpenWeatherMap API key into AI Studio Secrets.",
                color = Color(0xAAFFFFFF),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun SuccessWeatherView(
    state: WeatherUiState.Success,
    viewModel: WeatherViewModel,
    isAqiExpanded: Boolean,
    onToggleAqiExpanded: () -> Unit
) {
    val weather = state.currentWeather
    val forecast = state.forecast
    val aqiResponse = state.airPollution
    
    val currentTemp = weather.main.temp.toInt()
    val condition = weather.weather.firstOrNull()?.main ?: "Clear"
    val description = weather.weather.firstOrNull()?.description ?: "Clear Sky"
    val humidity = weather.main.humidity
    val windSpeed = weather.wind.speed
    val pressure = weather.main.pressure
    val feelsLike = weather.main.feelsLike.toInt()

    var isFavorite by remember(weather.cityName) { mutableStateOf(false) }
    
    LaunchedEffect(weather.cityName) {
        viewModel.isCityFavorite(weather.cityName) {
            isFavorite = it
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Location City Title & Favorites Add Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location pin",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = weather.cityName.uppercase(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                Text(
                    text = "LAST UPDATED: ${formatTimestamp(weather.timestamp)}",
                    color = Color(0x55FFFFFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(start = 22.dp, top = 2.dp)
                )
            }

            IconButton(
                onClick = {
                    viewModel.toggleFavorite(weather.cityName)
                    isFavorite = !isFavorite
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF0F0F0F))
                    .border(BorderStroke(0.5.dp, Color(0x22FFFFFF)), CircleShape)
                    .testTag("bookmark_city_button")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Toggle favorite city",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large Temperature & Custom Drawn Technical Line-Art Weather Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF070707))
                .border(BorderStroke(0.5.dp, Color(0x22FFFFFF)), RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "$currentTemp°",
                    color = Color.White,
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.testTag("current_temperature_text")
                )
                Text(
                    text = description.uppercase(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "FEELS LIKE $feelsLike°",
                    color = Color(0x66FFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Beautiful geometric vector weather illustration
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF000000))
                    .border(BorderStroke(0.5.dp, Color(0x11FFFFFF)), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                WeatherLineArtIcon(condition = condition, modifier = Modifier.size(64.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Air Quality Index (AQI) - Clean Matte Display
        val aqiValue = aqiResponse.list.firstOrNull()?.main?.aqi ?: 1
        val aqiStatus = getAqiStatus(aqiValue)
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF070707))
                .border(BorderStroke(0.5.dp, Color(0x22FFFFFF)), RoundedCornerShape(16.dp))
                .clickable { onToggleAqiExpanded() }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = "Air Quality",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AIR QUALITY INDEX",
                            color = Color(0x66FFFFFF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "LEVEL $aqiValue - ${aqiStatus.uppercase()}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // AQI high-contrast dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(getAqiColor(aqiValue))
                            )
                        }
                    }
                }
                
                Icon(
                    imageVector = if (isAqiExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand AQI info",
                    tint = Color.White
                )
            }

            AnimatedVisibility(visible = isAqiExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Divider(color = Color(0x11FFFFFF), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The Air Quality Index (AQI) scale goes from 1 to 5:\n" +
                                "• 1 (Good) - Air quality is satisfactory.\n" +
                                "• 2 (Fair) - Air quality is acceptable.\n" +
                                "• 3 (Moderate) - Moderate pollution may exist.\n" +
                                "• 4 (Poor) - Sensitive groups may experience health effects.\n" +
                                "• 5 (Very Poor) - Health alert! Everyone may experience effects.",
                        color = Color(0xAAFFFFFF),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 24-Hour Forecast Scroll Timeline
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF070707))
                .border(BorderStroke(0.5.dp, Color(0x22FFFFFF)), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "HOURLY TIMELINE (24H)",
                color = Color(0x66FFFFFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Horizontal timeline
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // We show 8 steps (representing 3 hours each = 24 hours)
                items(forecast.list.take(8)) { item ->
                    ForecastTimelineItem(item)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Auxiliary Weather Details Grid (Wind, Humidity, Pressure, Coordinates)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
                .also {  }
        ) {
            AuxiliaryWeatherCard(
                icon = Icons.Outlined.Air,
                title = "WIND SPEED",
                value = "$windSpeed m/s",
                modifier = Modifier.weight(1f)
            )
            AuxiliaryWeatherCard(
                icon = Icons.Outlined.WaterDrop,
                title = "HUMIDITY",
                value = "$humidity%",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AuxiliaryWeatherCard(
                icon = Icons.Outlined.Compress,
                title = "PRESSURE",
                value = "$pressure hPa",
                modifier = Modifier.weight(1f)
            )
            AuxiliaryWeatherCard(
                icon = Icons.Outlined.Map,
                title = "COORDINATES",
                value = "${Math.round(weather.coord.lat * 100.0) / 100.0}°N, ${Math.round(weather.coord.lon * 100.0) / 100.0}°E",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun ForecastTimelineItem(item: ForecastItem) {
    val temp = item.main.temp.toInt()
    val timeString = formatForecastTime(item.timestamp)
    val condition = item.weather.firstOrNull()?.main ?: "Clear"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF020202))
            .border(BorderStroke(0.5.dp, Color(0x11FFFFFF)), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Text(
            text = timeString,
            color = Color(0x88FFFFFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        WeatherLineArtIcon(
            condition = condition,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$temp°",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AuxiliaryWeatherCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070707))
            .border(BorderStroke(0.5.dp, Color(0x22FFFFFF)), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F0F0F))
                .border(BorderStroke(0.5.dp, Color(0x11FFFFFF)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Column {
            Text(
                text = title,
                color = Color(0x55FFFFFF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ErrorStateView(errorMessage: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F0505))
            .border(BorderStroke(0.5.dp, Color(0x33FF5555)), RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = "Error connection",
            tint = Color(0xFFFF5555),
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "CONNECTION ERROR",
            color = Color(0xFFFF5555),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = errorMessage,
            color = Color(0xAAFFFFFF),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(18.dp))
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text(
                text = "RETRY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

// Sleek vector weather icons drawn cleanly via custom Compose Canvas for the ultimate custom tech look!
@Composable
fun WeatherLineArtIcon(condition: String, modifier: Modifier = Modifier) {
    val strokeColor = Color.White
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerX = w / 2
        val centerY = h / 2
        
        when (condition) {
            "Clear" -> {
                // Draw geometric Sun
                val sunRadius = w * 0.25f
                drawCircle(
                    color = strokeColor,
                    radius = sunRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = w * 0.06f)
                )
                // Draw 8 rays
                val rayLength = w * 0.12f
                val startDist = sunRadius + w * 0.08f
                for (i in 0 until 8) {
                    val angle = (i * 45) * Math.PI / 180.0
                    val startX = (centerX + Math.cos(angle) * startDist).toFloat()
                    val startY = (centerY + Math.sin(angle) * startDist).toFloat()
                    val endX = (centerX + Math.cos(angle) * (startDist + rayLength)).toFloat()
                    val endY = (centerY + Math.sin(angle) * (startDist + rayLength)).toFloat()
                    drawLine(
                        color = strokeColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = w * 0.06f,
                        cap = StrokeCap.Round
                    )
                }
            }
            "Clouds" -> {
                // Draw geometric Cloud
                val path = Path().apply {
                    val baseLine = h * 0.7f
                    moveTo(w * 0.25f, baseLine)
                    // Bottom line
                    lineTo(w * 0.75f, baseLine)
                    // Right curve
                    cubicTo(w * 0.9f, baseLine, w * 0.9f, h * 0.45f, w * 0.75f, h * 0.45f)
                    // Center big peak
                    cubicTo(w * 0.65f, h * 0.22f, w * 0.4f, h * 0.22f, w * 0.45f, h * 0.45f)
                    // Left curve
                    cubicTo(w * 0.35f, h * 0.4f, w * 0.15f, h * 0.45f, w * 0.25f, baseLine)
                    close()
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
                )
            }
            "Rain", "Drizzle" -> {
                // Draw Cloud + Rain slant lines
                val path = Path().apply {
                    val baseLine = h * 0.6f
                    moveTo(w * 0.25f, baseLine)
                    lineTo(w * 0.75f, baseLine)
                    cubicTo(w * 0.9f, baseLine, w * 0.9f, h * 0.35f, w * 0.75f, h * 0.35f)
                    cubicTo(w * 0.65f, h * 0.12f, w * 0.4f, h * 0.12f, w * 0.45f, h * 0.35f)
                    cubicTo(w * 0.35f, h * 0.3f, w * 0.15f, h * 0.35f, w * 0.25f, baseLine)
                    close()
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
                )
                // Draw rain streaks
                val strokeW = w * 0.04f
                val length = h * 0.15f
                val lines = listOf(
                    Offset(w * 0.35f, h * 0.7f) to Offset(w * 0.3f, h * 0.85f),
                    Offset(w * 0.5f, h * 0.7f) to Offset(w * 0.45f, h * 0.85f),
                    Offset(w * 0.65f, h * 0.7f) to Offset(w * 0.6f, h * 0.85f)
                )
                for (line in lines) {
                    drawLine(
                        color = strokeColor,
                        start = line.first,
                        end = line.second,
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }
            "Thunderstorm" -> {
                // Draw Cloud + Lightning Bolt
                val path = Path().apply {
                    val baseLine = h * 0.6f
                    moveTo(w * 0.25f, baseLine)
                    lineTo(w * 0.75f, baseLine)
                    cubicTo(w * 0.9f, baseLine, w * 0.9f, h * 0.35f, w * 0.75f, h * 0.35f)
                    cubicTo(w * 0.65f, h * 0.12f, w * 0.4f, h * 0.12f, w * 0.45f, h * 0.35f)
                    cubicTo(w * 0.35f, h * 0.3f, w * 0.15f, h * 0.35f, w * 0.25f, baseLine)
                    close()
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
                )
                // Draw Lightning bolt
                val boltPath = Path().apply {
                    moveTo(w * 0.52f, h * 0.65f)
                    lineTo(w * 0.42f, h * 0.78f)
                    lineTo(w * 0.54f, h * 0.78f)
                    lineTo(w * 0.45f, h * 0.92f)
                }
                drawPath(
                    path = boltPath,
                    color = strokeColor,
                    style = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
                )
            }
            "Haze" -> {
                // Draw parallel horizontal foggy lines
                val strokeW = w * 0.06f
                val lines = listOf(
                    Offset(w * 0.25f, h * 0.35f) to Offset(w * 0.75f, h * 0.35f),
                    Offset(w * 0.15f, h * 0.48f) to Offset(w * 0.85f, h * 0.48f),
                    Offset(w * 0.3f,  h * 0.61f) to Offset(w * 0.70f, h * 0.61f),
                    Offset(w * 0.2f,  h * 0.74f) to Offset(w * 0.80f, h * 0.74f)
                )
                for (line in lines) {
                    drawLine(
                        color = strokeColor,
                        start = line.first,
                        end = line.second,
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }
            else -> {
                // Overcast/Clouds Fallback - generic icon
                val path = Path().apply {
                    val baseLine = h * 0.65f
                    moveTo(w * 0.25f, baseLine)
                    lineTo(w * 0.75f, baseLine)
                    cubicTo(w * 0.9f, baseLine, w * 0.9f, h * 0.4f, w * 0.75f, h * 0.4f)
                    cubicTo(w * 0.65f, h * 0.18f, w * 0.4f, h * 0.18f, w * 0.45f, h * 0.4f)
                    cubicTo(w * 0.35f, h * 0.35f, w * 0.15f, h * 0.4f, w * 0.25f, baseLine)
                    close()
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

// Utility formatting functions
fun formatTimestamp(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm, MMM dd, yyyy", Locale.getDefault())
        val netDate = Date(timestamp * 1000L)
        sdf.format(netDate).uppercase()
    } catch (e: Exception) {
        "N/A"
    }
}

fun formatForecastTime(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val netDate = Date(timestamp * 1000L)
        sdf.format(netDate)
    } catch (e: Exception) {
        "N/A"
    }
}

fun getAqiStatus(aqi: Int): String {
    return when (aqi) {
        1 -> "Good"
        2 -> "Fair"
        3 -> "Moderate"
        4 -> "Poor"
        5 -> "Very Poor"
        else -> "Unknown"
    }
}

fun getAqiColor(aqi: Int): Color {
    return when (aqi) {
        1 -> Color(0xFFFFFFFF) // Crisp Silver/White
        2 -> Color(0xFFD4D4D4)
        3 -> Color(0xFF8C8C8C)
        4 -> Color(0xFF525252)
        5 -> Color(0xFF1C1C1C)
        else -> Color.White
    }
}
