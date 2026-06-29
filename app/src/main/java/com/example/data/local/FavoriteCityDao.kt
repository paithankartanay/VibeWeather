package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteCityDao {

    @Query("SELECT * FROM favorite_cities ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteCity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favoriteCity: FavoriteCity)

    @Delete
    suspend fun deleteFavorite(favoriteCity: FavoriteCity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_cities WHERE name = :cityName LIMIT 1)")
    suspend fun isFavorite(cityName: String): Boolean
}
