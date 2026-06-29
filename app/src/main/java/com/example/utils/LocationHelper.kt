package com.example.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object LocationHelper {

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(
        context: Context,
        onSuccess: (latitude: Double, longitude: Double) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        // Try to get fresh location first
        val cancellationTokenSource = CancellationTokenSource()
        
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                onSuccess(location.latitude, location.longitude)
            } else {
                // Fallback to last known location
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                    if (lastLoc != null) {
                        onSuccess(lastLoc.latitude, lastLoc.longitude)
                    } else {
                        onFailure(Exception("Unable to retrieve location."))
                    }
                }.addOnFailureListener {
                    onFailure(it)
                }
            }
        }.addOnFailureListener {
            onFailure(it)
        }
    }
}
