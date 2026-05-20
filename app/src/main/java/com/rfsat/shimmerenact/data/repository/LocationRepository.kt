package com.rfsat.shimmerenact.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.rfsat.shimmerenact.data.models.LocationPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationRepository(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _currentLocation = MutableStateFlow<LocationPoint?>(null)
    val currentLocation: StateFlow<LocationPoint?> = _currentLocation.asStateFlow()

    private val _locationTrace = MutableStateFlow<List<LocationPoint>>(emptyList())
    val locationTrace: StateFlow<List<LocationPoint>> = _locationTrace.asStateFlow()

    private var callback: LocationCallback? = null

    private val request = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1_000L
    ).apply {
        setMinUpdateIntervalMillis(500L)
        setMinUpdateDistanceMeters(0f)
    }.build()

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        if (callback != null) return
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val point = loc.toLocationPoint()
                _currentLocation.value = point
                val current = _locationTrace.value
                _locationTrace.value = if (current.size >= 5000) {
                    current.drop(1) + point
                } else {
                    current + point
                }
            }
        }
        callback = cb
        fusedClient.requestLocationUpdates(request, cb, Looper.getMainLooper())
        AppLog.i("GPS", "Location updates started")
    }

    fun stopUpdates() {
        callback?.let {
            fusedClient.removeLocationUpdates(it)
            callback = null
            AppLog.i("GPS", "Location updates stopped")
        }
    }

    fun latestPoint(): LocationPoint? = _currentLocation.value

    private fun Location.toLocationPoint() = LocationPoint(
        lat         = latitude,
        lon         = longitude,
        altM        = altitude,
        accuracyM   = accuracy,
        timestampMs = System.currentTimeMillis()
    )
}
