package com.example.volta.model

import java.util.Locale

data class Outlet(
    val n: Int,
    val name: String = "Outlet $n",
    val on: Boolean = false,
    val powerW: Double = 0.0,
    val currentA: Double = 0.0,
    val energyKwh: Double = 0.0,
    val tempC: Int = 25,
    val locked: Boolean = false,
    val busy: Boolean = false,
    val overloadOk: Boolean = true,
    val overheatOk: Boolean = true,
    val eventCode: String = "00",
    val eventDesc: String = "Normal / OK"
)

data class VoltaHome(
    val id: String,
    val userId: String = "",
    val name: String,
    val icon: String = "home",
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val deviceCount: Int = 0
)

data class PowerStrip(
    val mac: String,
    val name: String,
    val model: String = "MTTL-W01",
    val fw: String = "0.1.54-1.0.105",
    val ip: String = "192.168.1.150",
    val online: Boolean = true,
    val voltageV: Double = 220.0,
    val rssi: Int = -65,
    val lastSeen: Long = System.currentTimeMillis(),
    val outlets: List<Outlet> = listOf(
        Outlet(1, "Outlet 1"),
        Outlet(2, "Outlet 2"),
        Outlet(3, "Outlet 3"),
        Outlet(4, "Outlet 4")
    ),
    val pendingCommands: Int = 0,
    val serverTotalPowerW: Double = -1.0,
    val serverCurrentA: Double = -1.0,
    val voltageGuard: VoltageGuard? = null,
    val lastSeenAgoServer: String = "",
    val isConnectingCloud: Boolean = false,
    val homeId: String? = null
) {
    val guard: VoltageGuard
        get() = voltageGuard ?: VoltageGuard()

    val totalPowerW: Double
        get() = if (serverTotalPowerW >= 0.0) serverTotalPowerW else outlets.sumOf { it.powerW }

    val totalEnergyKwh: Double
        get() = outlets.sumOf { it.energyKwh }

    val currentA: Double
        get() = if (serverCurrentA >= 0.0) serverCurrentA else outlets.sumOf { it.currentA }

    val isAnyOn: Boolean
        get() = outlets.any { it.on }

    val displayName: String
        get() = name.ifBlank { "MTTL ${mac.takeLast(6).uppercase()}" }

    val lastSeenAgo: String
        get() = if (lastSeenAgoServer.isNotBlank()) lastSeenAgoServer else {
            val sec = ((System.currentTimeMillis() - lastSeen) / 1000).coerceAtLeast(0)
            when {
                sec < 60 -> "just now"
                sec < 3600 -> "${sec / 60}m ago"
                sec < 86400 -> "${sec / 3600}h ago"
                else -> "${sec / 86400}d ago"
            }
        }
}

data class Schedule(
    val id: String,
    val stripMac: String,
    val outlet: Int, // 0 for All, 1..4 for individual
    val actionOn: Boolean,
    val kind: String = "time", // "time", "rise", "set"
    val time: String = "23:00",
    val offsetMinutes: Int = 0,
    val days: List<Int> = listOf(0, 1, 2, 3, 4, 5, 6), // 0=Mon .. 6=Sun
    val timezone: String = "Africa/Cairo",
    val label: String = "",
    val enabled: Boolean = true
)

enum class TimerMode {
    COUNTDOWN, // Turn ON or OFF after duration
    CYCLIC     // Alternate: ON for onDuration, OFF for offDuration
}

data class OutletTimer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val stripMac: String,
    val outlet: Int, // 0 for Master (All), 1..4 for individual
    val label: String = "",
    val mode: TimerMode = TimerMode.COUNTDOWN,
    val targetActionOn: Boolean = false, // true = Turn ON, false = Turn OFF
    val durationSeconds: Long = 1800,
    val onDurationSeconds: Long = 900,
    val offDurationSeconds: Long = 2700,
    val startPhaseOn: Boolean = true,
    val repeatCount: Int = 1, // -1 means infinite/forever, 1 means once, >1 means N times
    val currentCycle: Int = 0,
    val isRunning: Boolean = true,
    val isPaused: Boolean = false,
    val currentPhaseOn: Boolean = false,
    val remainingSeconds: Long = 1800,
    val totalSecondsInPhase: Long = 1800,
    val startedAt: Long = System.currentTimeMillis(),
    val lastTickAt: Long = System.currentTimeMillis(),
    val phase: String = "on", // "on" or "off"
    val phaseStarted: Long = startedAt
) {
    val isForever: Boolean get() = repeatCount <= -1 || repeatCount == 0

    val repeatType: String
        get() = when {
            repeatCount == 1 -> "once"
            repeatCount > 1 -> "times"
            else -> "forever"
        }

    val progress: Float
        get() = if (totalSecondsInPhase > 0) {
            1f - (remainingSeconds.toFloat() / totalSecondsInPhase.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedRemaining: String
        get() {
            val totalSec = remainingSeconds.coerceAtLeast(0)
            val hours = totalSec / 3600
            val minutes = (totalSec % 3600) / 60
            val seconds = totalSec % 60
            return if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
}

data class TelemetryPoint(
    val id: Long = 0,
    val timestamp: Long,
    val mac: String,
    val outlet: Int,
    val powerW: Double,
    val voltageV: Double,
    val tempC: Int,
    val energyKwh: Double
)

data class TopConsumer(
    val mac: String,
    val stripName: String,
    val outlet: Int,
    val outletName: String,
    val kwh: Double,
    val cost: Double = 0.0,
    val pct: Double = 0.0,
    val avgPowerW: Double = 0.0,
    val peakPowerW: Double = 0.0
)

data class AnalyticsSummary(
    val energyKwh: Double,
    val wattHours: Double,
    val avgPowerW: Double,
    val peakPowerW: Double,
    val minVoltageV: Double? = null,
    val maxVoltageV: Double? = null,
    val avgVoltageV: Double? = null,
    val avgTempC: Double? = null,
    val samples: Long = 0,
    val stripsCount: Int = 1,
    val cost: Double = 0.0,
    val currency: String = "EGP",
    val costPerKwh: Double = 2.18
)

data class VoltaSettings(
    val serverUrl: String = "https://volta.03092017.xyz",
    val serverToken: String = "",
    val isStandalone: Boolean = false,
    val costPerKwh: Double = 2.18,
    val currency: String = "EGP",
    val ntfyTopic: String = "",
    val alertOfflineMin: Int = 10,
    val notifyOffline: Boolean = true,
    val notifySwitch: Boolean = false,
    val tempAlertC: Int = 60,
    val notifyTemp: Boolean = true,
    val voltageMin: Double = 200.0,
    val voltageMax: Double = 250.0,
    val notifyVoltage: Boolean = true,
    val latitude: Double = 30.0444,
    val longitude: Double = 31.2357,
    val city: String = "Cairo",
    val country: String = "Egypt",
    val timezone: String = "Africa/Cairo"
) {
    val useGatewayMode: Boolean get() = !isStandalone || (serverUrl.isNotBlank() && serverToken.isNotBlank())
    val remoteUrl: String get() = serverUrl
    val authToken: String get() = serverToken
}

data class VoltageGuard(
    val enabled: Boolean = true,
    val minV: Double = 195.0,
    val maxV: Double = 250.0,
    val safeDelayMin: Int = 3,
    val tripped: Boolean = false,
    val faultType: String? = null,
    val trippedAt: Long? = null,
    val tripVoltage: Double? = null,
    val savedOutlets: List<Int> = emptyList(),
    val safeSince: Long? = null,
    val tempEnabled: Boolean = true,
    val maxTempC: Double = 65.0,
    val tempTripped: Boolean = false,
    val tempFaultType: String? = null,
    val tripTempC: Double? = null
)

data class WhoAmIResponse(
    val authorized: Boolean = false,
    val isAdmin: Boolean = false,
    val name: String = "",
    val email: String = "",
    val picture: String = "",
    val strips: List<String> = emptyList(),
    val maxStrips: Int = 10,
    val expiresAt: Long? = null, // Unix seconds, null = Lifetime
    val status: String = "active", // "active", "canceled", or "expired"
    val subscriptionExpired: Boolean = false,
    val error: String? = null
)

data class GoogleAuthResponse(
    val ok: Boolean = false,
    val token: String = "",
    val email: String = "",
    val name: String = "",
    val picture: String = "",
    val isAdmin: Boolean = false,
    val strips: List<String> = emptyList(),
    val maxStrips: Int = 1,
    val expiresAt: Long? = null,
    val status: String = "active",
    val error: String? = null
)

object GoogleAuthConfig {
    const val WEB_CLIENT_ID = "345585205205-d2kn3jfdhahsq1ur4naclk97reojqptk.apps.googleusercontent.com"
    const val ANDROID_CLIENT_ID = "345585205205-g9gnm704fkh7is0n1r33v6n4tk0teuja.apps.googleusercontent.com"
    const val PROJECT_ID = "volta-510610"
    const val CLIENT_SECRET = ""
    const val REDIRECT_URI = "https://volta.03092017.xyz"
    const val PACKAGE_NAME = "com.aistudio.volta.kxwz"
    const val SHA1_FINGERPRINT = "90:8E:F0:0C:3E:8D:10:65:0E:2F:12:7E:EE:5D:22:08:88:3D:18:79"

    fun getGoogleOAuthUrl(nonce: String = "volta_${System.currentTimeMillis()}"): String {
        val cid = if (WEB_CLIENT_ID.isNotBlank()) WEB_CLIENT_ID else "345585205205-d2kn3jfdhahsq1ur4naclk97reojqptk.apps.googleusercontent.com"
        val encodedRedirect = java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$cid" +
                "&redirect_uri=$encodedRedirect" +
                "&response_type=id_token" +
                "&scope=openid%20email%20profile" +
                "&nonce=$nonce" +
                "&prompt=select_account"
    }
}

data class CitySearchResult(
    val name: String,
    val country: String = "",
    val countryCode: String = "",
    val admin1: String = "",
    val latitude: Double,
    val longitude: Double,
    val timezone: String = ""
)

data class SunInfo(
    val ok: Boolean = true,
    val city: String = "",
    val country: String = "",
    val timezone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val localTime: String = "",
    val localDate: String = "",
    val sunrise: String = "",
    val sunset: String = ""
)

data class LogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String, // "IN", "OUT", "SYS", "WARN", "ERR"
    val mac: String = "",
    val message: String
)
