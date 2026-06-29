package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_cities")
data class FavoriteCity(
    @PrimaryKey val name: String,
    val addedAt: Long = System.currentTimeMillis()
)
