package com.example.volta.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class DiscoveredStripAp(
    val ssid: String,
    val bssid: String = "",
    val rssi: Int = -65,
    val password: String,
    val macSuffix: String
)

data class WifiScanOutcome(
    val strips: List<DiscoveredStripAp>,
    val isWifiEnabled: Boolean = true,
    val isLocationEnabled: Boolean = true,
    val infoMessage: String? = null
)

class WifiProvisioningHelper(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        fun deriveStripPassword(ssid: String): String {
            val cleanSsid = ssid.trim().removeSurrounding("\"")
            val suffix = when {
                cleanSsid.startsWith("TONLY_TAP_", ignoreCase = true) -> cleanSsid.substringAfter("TONLY_TAP_")
                cleanSsid.startsWith("U+TAP_", ignoreCase = true) -> cleanSsid.substringAfter("U+TAP_")
                cleanSsid.startsWith("MTTL-W01-", ignoreCase = true) -> cleanSsid.substringAfter("MTTL-W01-")
                cleanSsid.contains("_") -> cleanSsid.substringAfterLast("_")
                else -> cleanSsid
            }
            return "LGU_$suffix"
        }

        fun deriveMacSuffix(ssid: String): String {
            val cleanSsid = ssid.trim().removeSurrounding("\"")
            return when {
                cleanSsid.startsWith("TONLY_TAP_", ignoreCase = true) -> cleanSsid.substringAfter("TONLY_TAP_")
                cleanSsid.startsWith("U+TAP_", ignoreCase = true) -> cleanSsid.substringAfter("U+TAP_")
                cleanSsid.startsWith("MTTL-W01-", ignoreCase = true) -> cleanSsid.substringAfter("MTTL-W01-")
                cleanSsid.contains("_") -> cleanSsid.substringAfterLast("_")
                else -> cleanSsid
            }
        }

        fun isTonlyStripSsid(ssid: String): Boolean {
            val s = ssid.trim().removeSurrounding("\"").uppercase()
            return s.startsWith("TONLY_TAP_") || s.startsWith("U+TAP_") || s.startsWith("MTTL")
        }
    }

    fun isWifiEnabled(): Boolean {
        return wifiManager?.isWifiEnabled ?: false
    }

    fun isLocationEnabled(): Boolean {
        return locationManager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: true
    }

    suspend fun scanForStrips(): WifiScanOutcome = withContext(Dispatchers.IO) {
        if (wifiManager == null) {
            return@withContext WifiScanOutcome(emptyList(), infoMessage = "Wi-Fi service unavailable on this device")
        }

        val wifiOn = isWifiEnabled()
        if (!wifiOn) {
            return@withContext WifiScanOutcome(
                strips = emptyList(),
                isWifiEnabled = false,
                infoMessage = "Wi-Fi is turned off. Please turn on Wi-Fi in Quick Settings."
            )
        }

        val locationOn = isLocationEnabled()
        if (!locationOn) {
            return@withContext WifiScanOutcome(
                strips = emptyList(),
                isLocationEnabled = false,
                infoMessage = "Location (GPS) is turned OFF. Android requires Location to be ON to scan nearby Wi-Fi SSIDs."
            )
        }

        val hasLocationPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPerm) {
            return@withContext WifiScanOutcome(
                strips = emptyList(),
                infoMessage = "Location permission required to scan Wi-Fi networks."
            )
        }

        // Check if there are already cached scan results matching TONLY
        val existingCached = parseScanResults()
        if (existingCached.isNotEmpty()) {
            return@withContext WifiScanOutcome(existingCached)
        }

        // Trigger active asynchronous scan with broadcast listener
        var scanSucceeded = false
        try {
            @Suppress("DEPRECATION")
            scanSucceeded = wifiManager.startScan()
        } catch (e: Exception) {
            Log.e("WifiProvisioning", "startScan threw exception: ${e.message}")
        }

        if (scanSucceeded) {
            // Await broadcast with timeout
            withTimeoutOrNull(3500L) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(c: Context?, intent: Intent?) {
                            try {
                                context.unregisterReceiver(this)
                            } catch (_: Exception) {}
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                    val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                    context.registerReceiver(receiver, filter)

                    continuation.invokeOnCancellation {
                        try {
                            context.unregisterReceiver(receiver)
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        val freshResults = parseScanResults()
        if (freshResults.isNotEmpty()) {
            return@withContext WifiScanOutcome(freshResults)
        }

        val msg = if (!scanSucceeded) {
            "Android scan throttled. Please enter the strip SSID or Suffix manually below."
        } else {
            "No TONLY_TAP networks detected in range. Ensure strip power button was held 5s until LED blinks rapidly, or enter SSID manually."
        }
        WifiScanOutcome(emptyList(), infoMessage = msg)
    }

    private fun parseScanResults(): List<DiscoveredStripAp> {
        val results = mutableListOf<DiscoveredStripAp>()
        try {
            val scanList = wifiManager?.scanResults ?: emptyList()
            for (res in scanList) {
                val ssid = res.SSID ?: continue
                if (isTonlyStripSsid(ssid)) {
                    val password = deriveStripPassword(ssid)
                    val macSuffix = deriveMacSuffix(ssid)
                    results.add(
                        DiscoveredStripAp(
                            ssid = ssid,
                            bssid = res.BSSID ?: "",
                            rssi = res.level,
                            password = password,
                            macSuffix = macSuffix
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("WifiProvisioning", "Failed to parse scanResults: ${e.message}")
        }
        return results.distinctBy { it.ssid }.sortedByDescending { it.rssi }
    }

    suspend fun connectToStripAp(ssid: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        if (connectivityManager == null) {
            return@withContext Result.failure(Exception("ConnectivityManager not available"))
        }

        disconnectFromStripAp()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val specifier = WifiNetworkSpecifier.Builder()
                        .setSsid(ssid)
                        .setWpa2Passphrase(password)
                        .build()

                    val request = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(specifier)
                        .build()

                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            super.onAvailable(network)
                            connectivityManager.bindProcessToNetwork(network)
                            if (continuation.isActive) {
                                continuation.resume(Result.success("Connected to $ssid (password $password)"))
                            }
                        }

                        override fun onUnavailable() {
                            super.onUnavailable()
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(Exception("Could not connect to $ssid. Ensure strip AP is active.")))
                            }
                        }

                        override fun onLost(network: Network) {
                            super.onLost(network)
                            connectivityManager.bindProcessToNetwork(null)
                        }
                    }

                    activeNetworkCallback = callback
                    connectivityManager.requestNetwork(request, callback, 20000)

                    continuation.invokeOnCancellation {
                        disconnectFromStripAp()
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(e))
                    }
                }
            }
        } else {
            // For Android < 10 or fallback
            Result.success("Manual mode: Please connect to Wi-Fi '$ssid' with password '$password'")
        }
    }

    fun disconnectFromStripAp() {
        try {
            activeNetworkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
                activeNetworkCallback = null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager?.bindProcessToNetwork(null)
            }
        } catch (_: Exception) {}
    }

    fun unbindProcessNetwork() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager?.bindProcessToNetwork(null)
            }
        } catch (_: Exception) {}
    }
}
