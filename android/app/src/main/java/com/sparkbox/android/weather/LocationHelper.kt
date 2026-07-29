package com.sparkbox.android.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Prefers Play Services when present; falls back to [LocationManager]
 * for ColorOS / China builds without GMS (e.g. OPPO Find X6 Pro 国行).
 */
class LocationHelper(private val context: Context) {
    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        if (hasPlayServices()) {
            fusedLocation()?.let { return it }
        }
        return platformLocation()
    }

    private fun hasPlayServices(): Boolean {
        return try {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (_: Throwable) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fusedLocation(): Location? =
        suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            try {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc -> cont.resume(loc) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (_: Exception) {
                cont.resume(null)
            }
        }

    @SuppressLint("MissingPermission")
    private suspend fun platformLocation(): Location? = withContext(Dispatchers.IO) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = sequenceOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            try {
                if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
            } catch (_: Exception) {
                null
            }
        }.maxByOrNull { it.time }

        if (last != null && System.currentTimeMillis() - last.time < 5 * 60 * 1000) {
            return@withContext last
        }

        // Fresh single update when possible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { cont ->
                val criteria = Criteria().apply {
                    accuracy = Criteria.ACCURACY_COARSE
                    powerRequirement = Criteria.POWER_LOW
                }
                val provider = lm.getBestProvider(criteria, true) ?: LocationManager.NETWORK_PROVIDER
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                        cont.resume(loc ?: last)
                    }
                } catch (_: Exception) {
                    cont.resume(last)
                }
            }
        } else {
            last
        }
    }
}
