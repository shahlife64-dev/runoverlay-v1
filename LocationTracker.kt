package com.telemetryoverlay.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

data class RunStats(
    val distanceMeters: Double,
    val paceSecPerKm: Double, // instantaneous pace over the last GPS leg; 0 if not yet available
    val elevationMeters: Double
)

/**
 * Tracks cumulative distance and current elevation from GPS fixes and derives
 * a smoothed running pace. Distance accumulates as the sum of consecutive
 * fix-to-fix great-circle distances (Location#distanceTo), which is the same
 * approach most GPS running watches use.
 */
class LocationTracker(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var lastLocation: Location? = null
    private var totalDistanceMeters = 0.0
    private var lastElevation = 0.0
    private var recentPaceSecPerKm = 0.0

    // Rolling window used to smooth instantaneous pace so it doesn't jump
    // around between individual GPS fixes.
    private val recentSplits = ArrayDeque<Pair<Double, Long>>() // distance meters, timestamp

    var onUpdate: ((RunStats) -> Unit)? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            processLocation(location)
        }
    }

    @SuppressLint("MissingPermission") // caller guarantees permission before start()
    fun start() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, context.mainLooper)
    }

    fun stop() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    fun reset() {
        lastLocation = null
        totalDistanceMeters = 0.0
        recentSplits.clear()
        recentPaceSecPerKm = 0.0
    }

    private fun processLocation(location: Location) {
        if (location.hasAltitude()) {
            lastElevation = location.altitude
        }

        val previous = lastLocation
        if (previous != null) {
            val segmentMeters = previous.distanceTo(location).toDouble()
            // Ignore GPS jitter while standing still (typical drift is a few meters).
            if (segmentMeters > 1.0) {
                totalDistanceMeters += segmentMeters
                val now = System.currentTimeMillis()
                recentSplits.addLast(segmentMeters to now)
                while (recentSplits.isNotEmpty() && now - recentSplits.first().second > 30_000L) {
                    recentSplits.removeFirst()
                }
                recomputePace()
            }
        }
        lastLocation = location

        onUpdate?.invoke(RunStats(totalDistanceMeters, recentPaceSecPerKm, lastElevation))
    }

    private fun recomputePace() {
        if (recentSplits.size < 2) return
        val windowDistanceM = recentSplits.sumOf { it.first }
        val windowSeconds = (recentSplits.last().second - recentSplits.first().second) / 1000.0
        if (windowDistanceM > 0 && windowSeconds > 0) {
            val speedMetersPerSec = windowDistanceM / windowSeconds
            recentPaceSecPerKm = 1000.0 / speedMetersPerSec
        }
    }
}
