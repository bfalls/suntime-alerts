package com.bfalls.suntimealerts.alarm.data

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.util.Log
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await


class LocationService(application: Application) {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    @SuppressLint("MissingPermission")
    suspend fun currentCoordinate(): Coordinate? {
        return try {
            Log.d("LocationService", "Requesting current location…")
            // Prefer a fresh location fix when possible
            val cts = CancellationTokenSource()

            val current: Location? = try {
                client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                )
                    .await()
            } catch (t: Throwable) {
                Log.w("LocationService", "getCurrentLocation failed: $t")
                null
            }

            Log.d("LocationService", "getCurrentLocation result = $current")

            val last: Location? = if (current == null) {
                val lastLoc = client.lastLocation.await()
                Log.d("LocationService", "lastLocation result = $lastLoc")
                lastLoc
            } else {
                null
            }

            // Fall back to last known location if no fresh fix is available
            val effective: Location? = current ?: last

            if (effective == null) {
                Log.w("LocationService", "No location available (current or last).")
                null
            } else {
                val coord = Coordinate(effective.latitude, effective.longitude)
                Log.d("LocationService", "Using coordinate: $coord from location: $effective (source=${if (current != null) "current" else "last"})")
                coord
            }
        } catch (se: SecurityException) {
            Log.e("LocationService", "Location permission not granted", se)
            null
        } catch (t: Throwable) {
            Log.e("LocationService", "Error retrieving location", t)
            null
        }
    }
}