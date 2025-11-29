package com.bfalls.suntimealerts.alarm.data

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await


class LocationService(application: Application) {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)

    @SuppressLint("MissingPermission")
    suspend fun currentCoordinate(): Coordinate? {
        val location: Location = client.lastLocation.await() ?: return null
        return Coordinate(location.latitude, location.longitude)
    }
}