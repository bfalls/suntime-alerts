package com.bfalls.suntimealerts.alarm.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

interface LocationProvider {
    suspend fun currentCoordinate(): Coordinate?
}

class LocationService(private val application: Application) : LocationProvider {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    @SuppressLint("MissingPermission")
    override suspend fun currentCoordinate(): Coordinate? {
        // Fast check: if we don't have location permission, bail out immediately
        val fineGranted = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(
            "LocationService",
            "Permission check: fineGranted=$fineGranted coarseGranted=$coarseGranted"
        )

        if (!fineGranted && !coarseGranted) {
            Log.w("LocationService", "No location permission; returning null")
            return null
        }

        return try {
            Log.d("LocationService", "Requesting current location…")

            val cts = CancellationTokenSource()

            // Try a fresh fix first
            val current: Location? = try {
                client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).await()
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

            val effective: Location? = current ?: last

            if (effective == null) {
                Log.w("LocationService", "No location available (current or last).")
                null
            } else {
                val coord = Coordinate(effective.latitude, effective.longitude)
                Log.d(
                    "LocationService",
                    "Using coordinate: $coord from location: $effective (source=${if (current != null) "current" else "last"})"
                )
                coord
            }
        } catch (se: SecurityException) {
            Log.e("LocationService", "Location permission not granted (SecurityException)", se)
            null
        } catch (t: Throwable) {
            Log.e("LocationService", "Error retrieving location", t)
            null
        }
    }
}
