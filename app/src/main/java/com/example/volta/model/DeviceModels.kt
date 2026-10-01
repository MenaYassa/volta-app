package com.example.volta.model

data class Outlet(
    val n: Int,
    val name: String = "Outlet $n",
    val on: Boolean = false,
    val powerW: Double = 0.0,
    val energyKwh: Double = 0.0,
    val tempC: Int = 25,
    val locked: Boolean = false,
    val busy: Boolean = false
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
    val pendingCommands: Int = 0
) {
    val totalPowerW: Double
        get() = outlets.sumOf { it.powerW }

    val totalEnergyKwh: Double
        get() = outlets.sumOf { it.energyKwh }

    val currentA: Double
        get() = if (voltageV > 0) totalPowerW / voltageV else 0.0

    val isAnyOn: Boolean
        get() = outlets.any { it.on }

    val displayName: String
        get() = name.ifBlank { "MTTL ${mac.takeLast(6).uppercase()}" }
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
    val cost: Double
)

data class VoltaSettings(
    val serverUrl: String = "http://192.168.1.100:8080",
    val serverToken: String = "",
    val isStandalone: Boolean = true,
    val costPerKwh: Double = 1.2,
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
    val longitude: Double = 31.2357
)

data class LogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String, // "IN", "OUT", "SYS", "WARN", "ERR"
    val mac: String = "",
    val message: String
)
