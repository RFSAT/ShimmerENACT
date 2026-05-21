package com.rfsat.shimmerenact.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provides the device's GPS/network location as a [StateFlow].
 *
 * Call [startUpdates] when a recording session begins and [stopUpdates] when it ends.
 * The last known location is always available via [location]; it is `null` only if the
 * permission is denied or no fix has been obtained yet.
 *
 * Location updates are requested at 1 Hz (fastest 4 Hz) with HIGH_ACCURACY priority,
 * which uses GPS + network fusion via the Fused Location Provider.
 */
class LocationRepository(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private var active = false

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1_000L          // desired interval ms
    )
        .setMinUpdateIntervalMillis(250L)   // fastest interval ms
        .setMinUpdateDistanceMeters(0f)     // update even if stationary
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                _location.value = loc
                AppLog.d("LOC", "%.6f, %.6f  acc=%.1fm  alt=%.1fm".format(
                    loc.latitude, loc.longitude, loc.accuracy, loc.altitude))
            }
        }
    }

    /** Returns true if ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION is granted. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Start receiving location updates. Safe to call multiple times — ignores if already active.
     * Seeds [location] immediately from the last known location so that the very first
     * data row already has coordinates.
     */
    fun startUpdates() {
        if (active) return
        if (!hasPermission()) {
            AppLog.w("LOC", "startUpdates: location permission not granted")
            return
        }
        try {
            // Seed from last known position immediately (no wait for first fix)
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && _location.value == null) {
                    _location.value = loc
                    AppLog.d("LOC", "Seeded from last known: %.6f, %.6f".format(
                        loc.latitude, loc.longitude))
                }
            }
            fusedClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
            active = true
            AppLog.ok("LOC", "Location updates started")
        } catch (e: SecurityException) {
            AppLog.e("LOC", "startUpdates SecurityException: ${e.message}")
        } catch (e: Exception) {
            AppLog.e("LOC", "startUpdates failed: ${e.message}")
        }
    }

    /** Stop receiving updates. Call when the recording session ends. */
    fun stopUpdates() {
        if (!active) return
        try {
            fusedClient.removeLocationUpdates(callback)
            active = false
            AppLog.ok("LOC", "Location updates stopped")
        } catch (e: Exception) {
            AppLog.e("LOC", "stopUpdates failed: ${e.message}")
        }
    }

    /** Release all resources (call from ViewModel.onCleared). */
    fun cleanup() = stopUpdates()
}
