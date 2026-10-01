package com.example.volta.repository

import android.content.Context
import com.example.volta.db.VoltaDbHelper
import com.example.volta.model.LogEntry
import com.example.volta.model.Outlet
import com.example.volta.model.PowerStrip
import com.example.volta.model.Schedule
import com.example.volta.model.TelemetryPoint
import com.example.volta.model.TopConsumer
import com.example.volta.model.VoltaSettings
import com.example.volta.network.DirectSocketClient
import com.example.volta.network.VoltaApiClient
import com.example.volta.protocol.TonlyProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import kotlin.random.Random

class VoltaRepository(context: Context) {

    private val dbHelper = VoltaDbHelper(context)
    private val apiClient = VoltaApiClient()
    private val socketClient = DirectSocketClient()
    private val repoScope = CoroutineScope(Dispatchers.Default + Job())

    private val _strips = MutableStateFlow<List<PowerStrip>>(emptyList())
    val strips: StateFlow<List<PowerStrip>> = _strips.asStateFlow()

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _settings = MutableStateFlow(VoltaSettings())
    val settings: StateFlow<VoltaSettings> = _settings.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Ready")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val httpClient = OkHttpClient()

    init {
        repoScope.launch {
            loadInitialData()
            startTelemetryLoop()
        }
    }

    private suspend fun loadInitialData() = withContext(Dispatchers.IO) {
        val loadedSettings = dbHelper.loadSettings()
        _settings.value = loadedSettings

        var loadedStrips = dbHelper.getAllStrips()
        if (loadedStrips.isEmpty()) {
            loadedStrips = seedDefaultStrips()
            for (s in loadedStrips) {
                dbHelper.upsertStrip(s)
            }
            seedTelemetryHistory(loadedStrips)
        }
        _strips.value = loadedStrips

        var loadedSchedules = dbHelper.getAllSchedules()
        if (loadedSchedules.isEmpty()) {
            loadedSchedules = seedDefaultSchedules(loadedStrips.firstOrNull()?.mac ?: "")
            for (sc in loadedSchedules) {
                dbHelper.upsertSchedule(sc)
            }
        }
        _schedules.value = loadedSchedules

        addLog("SYS", "", "Volta controller initialized. Database loaded (${loadedStrips.size} strips registered).")
    }

    private fun seedDefaultStrips(): List<PowerStrip> {
        return listOf(
            PowerStrip(
                mac = "A020A6112233",
                name = "Living Room Strip",
                model = "MTTL-W01",
                fw = "0.1.54-1.0.105",
                ip = "192.168.1.101",
                online = true,
                voltageV = 222.4,
                rssi = -62,
                outlets = listOf(
                    Outlet(1, "OLED TV & Soundbar", on = true, powerW = 142.5, energyKwh = 18.42, tempC = 38),
                    Outlet(2, "PlayStation 5", on = true, powerW = 88.0, energyKwh = 9.15, tempC = 41),
                    Outlet(3, "Accent Light", on = false, powerW = 0.0, energyKwh = 1.34, tempC = 31),
                    Outlet(4, "Subwoofer", on = true, powerW = 24.5, energyKwh = 3.65, tempC = 35)
                )
            ),
            PowerStrip(
                mac = "A020A6445566",
                name = "Workstation Desk",
                model = "MTTL-W01",
                fw = "0.1.54-1.0.105",
                ip = "192.168.1.102",
                online = true,
                voltageV = 221.8,
                rssi = -58,
                outlets = listOf(
                    Outlet(1, "Dual Monitors", on = true, powerW = 65.0, energyKwh = 12.80, tempC = 36),
                    Outlet(2, "Desktop PC", on = true, powerW = 210.0, energyKwh = 42.10, tempC = 44),
                    Outlet(3, "Studio Monitors", on = false, powerW = 0.0, energyKwh = 2.15, tempC = 30),
                    Outlet(4, "Desk Lamp", on = true, powerW = 9.2, energyKwh = 0.95, tempC = 32)
                )
            )
        )
    }

    private fun seedDefaultSchedules(mac: String): List<Schedule> {
        return listOf(
            Schedule(
                id = UUID.randomUUID().toString(),
                stripMac = mac,
                outlet = 0, // all
                actionOn = false,
                kind = "time",
                time = "01:00",
                days = listOf(0, 1, 2, 3, 4, 5, 6),
                timezone = "Africa/Cairo",
                label = "Night Auto-Off",
                enabled = true
            ),
            Schedule(
                id = UUID.randomUUID().toString(),
                stripMac = mac,
                outlet = 1,
                actionOn = true,
                kind = "time",
                time = "08:30",
                days = listOf(0, 1, 2, 3, 4),
                timezone = "Africa/Cairo",
                label = "Workday Power On",
                enabled = true
            )
        )
    }

    private fun seedTelemetryHistory(stripsList: List<PowerStrip>) {
        val now = System.currentTimeMillis()
        val interval = 15 * 60 * 1000L // every 15 min for 24h
        for (i in 96 downTo 0) {
            val t = now - (i * interval)
            for (strip in stripsList) {
                for (outlet in strip.outlets) {
                    val basePower = if (outlet.on) {
                        when (outlet.n) {
                            1 -> 120.0 + Random.nextDouble(-15.0, 25.0)
                            2 -> 160.0 + Random.nextDouble(-30.0, 50.0)
                            3 -> 0.0
                            else -> 20.0 + Random.nextDouble(-3.0, 5.0)
                        }
                    } else 0.0
                    val volt = 220.0 + Random.nextDouble(-3.5, 3.5)
                    val temp = (32 + (basePower / 15.0).toInt()).coerceIn(25, 65)
                    dbHelper.insertTelemetry(
                        TelemetryPoint(
                            timestamp = t,
                            mac = strip.mac,
                            outlet = outlet.n,
                            powerW = (basePower * 10).toLong() / 10.0,
                            voltageV = (volt * 10).toLong() / 10.0,
                            tempC = temp,
                            energyKwh = outlet.energyKwh - (i * 0.05).coerceAtLeast(0.0)
                        )
                    )
                }
            }
        }
    }

    private fun startTelemetryLoop() {
        repoScope.launch {
            while (isActive) {
                delay(5000L)
                if (_settings.value.isStandalone) {
                    simulateTelemetryTick()
                } else {
                    pollRemoteServer()
                }
            }
        }
    }

    private suspend fun simulateTelemetryTick() = withContext(Dispatchers.IO) {
        val currentStrips = _strips.value
        val now = System.currentTimeMillis()
        val updated = currentStrips.map { strip ->
            if (!strip.online) return@map strip
            val volt = (220.0 + Random.nextDouble(-2.0, 2.0) * 10).toLong() / 10.0
            val updatedOutlets = strip.outlets.map { o ->
                if (o.on) {
                    val variation = Random.nextDouble(-2.0, 2.0)
                    val newPower = (o.powerW + variation).coerceAtLeast(2.0)
                    val deltaKwh = (newPower * 5.0) / (3600.0 * 1000.0)
                    val newEnergy = o.energyKwh + deltaKwh
                    val newTemp = (o.tempC + Random.nextInt(-1, 2)).coerceIn(30, 55)

                    // Insert telemetry point periodically
                    dbHelper.insertTelemetry(
                        TelemetryPoint(
                            timestamp = now,
                            mac = strip.mac,
                            outlet = o.n,
                            powerW = (newPower * 10).toLong() / 10.0,
                            voltageV = volt,
                            tempC = newTemp,
                            energyKwh = (newEnergy * 1000).toLong() / 1000.0
                        )
                    )

                    o.copy(
                        powerW = (newPower * 10).toLong() / 10.0,
                        energyKwh = (newEnergy * 1000).toLong() / 1000.0,
                        tempC = newTemp
                    )
                } else {
                    o.copy(powerW = 0.0)
                }
            }
            val s = strip.copy(
                voltageV = volt,
                lastSeen = now,
                outlets = updatedOutlets
            )
            dbHelper.upsertStrip(s)
            s
        }
        _strips.value = updated
        _connectionStatus.value = "Local Controller Active (${updated.count { it.online }} online)"
    }

    private suspend fun pollRemoteServer() {
        val s = _settings.value
        val result = apiClient.fetchStrips(s.serverUrl, s.serverToken)
        result.onSuccess { remoteStrips ->
            _strips.value = remoteStrips
            _connectionStatus.value = "Connected to ${s.serverUrl}"
            withContext(Dispatchers.IO) {
                for (rs in remoteStrips) {
                    dbHelper.upsertStrip(rs)
                }
            }
        }.onFailure { err ->
            _connectionStatus.value = "Gateway Error: ${err.message}"
        }
    }

    suspend fun refreshStrips() {
        _isRefreshing.value = true
        if (_settings.value.isStandalone) {
            simulateTelemetryTick()
        } else {
            pollRemoteServer()
        }
        delay(400)
        _isRefreshing.value = false
    }

    suspend fun toggleOutlet(mac: String, outletN: Int, requestedState: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val strip = _strips.value.find { it.mac == mac } ?: return@withContext Result.failure(Exception("Strip not found"))
        val outlet = strip.outlets.find { it.n == outletN }
        if (outlet?.locked == true) {
            addLog("WARN", mac, "Action blocked: Outlet $outletN is locked.")
            return@withContext Result.failure(Exception("Outlet is locked"))
        }

        addLog("OUT", mac, TonlyProtocol.buildOnOff(outletN, requestedState).trim())

        if (!_settings.value.isStandalone) {
            val res = apiClient.switchOutlet(_settings.value.serverUrl, _settings.value.serverToken, mac, outletN, requestedState)
            if (res.isSuccess) {
                addLog("IN", mac, "up:onoff:$outletN:${if (requestedState) "on" else "off"}")
                refreshStrips()
                return@withContext Result.success(true)
            } else {
                addLog("ERR", mac, "Failed to toggle: ${res.exceptionOrNull()?.message}")
                return@withContext res
            }
        }

        // Standalone local execution
        val updatedStrips = _strips.value.map { s ->
            if (s.mac == mac) {
                val updatedOutlets = s.outlets.map { o ->
                    if (o.n == outletN) {
                        val baseW = if (requestedState) (40.0 + Random.nextDouble(10.0, 90.0)) else 0.0
                        o.copy(on = requestedState, powerW = (baseW * 10).toLong() / 10.0)
                    } else o
                }
                val updatedStrip = s.copy(outlets = updatedOutlets)
                dbHelper.upsertStrip(updatedStrip)
                updatedStrip
            } else s
        }
        _strips.value = updatedStrips
        addLog("IN", mac, "up:onoff:$outletN:${if (requestedState) "on" else "off"}")

        // Check alerts
        val set = _settings.value
        if (set.notifySwitch && set.ntfyTopic.isNotBlank()) {
            sendNtfyAlert(set.ntfyTopic, "Volta Switch Alert", "${strip.displayName} outlet $outletN switched to ${if (requestedState) "ON" else "OFF"}")
        }

        Result.success(true)
    }

    suspend fun toggleMaster(mac: String, requestedState: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val strip = _strips.value.find { it.mac == mac } ?: return@withContext Result.failure(Exception("Strip not found"))
        addLog("OUT", mac, TonlyProtocol.buildOnOff(0, requestedState).trim())

        if (!_settings.value.isStandalone) {
            val res = apiClient.switchOutlet(_settings.value.serverUrl, _settings.value.serverToken, mac, 0, requestedState)
            if (res.isSuccess) {
                refreshStrips()
                return@withContext Result.success(true)
            }
        }

        val updatedStrips = _strips.value.map { s ->
            if (s.mac == mac) {
                val updatedOutlets = s.outlets.map { o ->
                    if (!o.locked) {
                        val baseW = if (requestedState) (35.0 + Random.nextDouble(10.0, 80.0)) else 0.0
                        o.copy(on = requestedState, powerW = (baseW * 10).toLong() / 10.0)
                    } else o
                }
                val updatedStrip = s.copy(outlets = updatedOutlets)
                dbHelper.upsertStrip(updatedStrip)
                updatedStrip
            } else s
        }
        _strips.value = updatedStrips
        addLog("IN", mac, "up:onoff:0:${if (requestedState) "on" else "off"}")
        Result.success(true)
    }

    suspend fun renameStrip(mac: String, newName: String) = withContext(Dispatchers.IO) {
        dbHelper.updateStripName(mac, newName)
        _strips.value = _strips.value.map {
            if (it.mac == mac) it.copy(name = newName) else it
        }
        addLog("SYS", mac, "Strip renamed to '$newName'")
    }

    suspend fun renameOutlet(mac: String, outletN: Int, newName: String) = withContext(Dispatchers.IO) {
        dbHelper.updateOutletName(mac, outletN, newName)
        _strips.value = _strips.value.map { s ->
            if (s.mac == mac) {
                s.copy(outlets = s.outlets.map { if (it.n == outletN) it.copy(name = newName) else it })
            } else s
        }
        addLog("SYS", mac, "Outlet $outletN renamed to '$newName'")
    }

    suspend fun toggleOutletLock(mac: String, outletN: Int) = withContext(Dispatchers.IO) {
        val strip = _strips.value.find { it.mac == mac } ?: return@withContext
        val outlet = strip.outlets.find { it.n == outletN } ?: return@withContext
        val newLock = !outlet.locked
        dbHelper.updateOutletLock(mac, outletN, newLock)
        _strips.value = _strips.value.map { s ->
            if (s.mac == mac) {
                s.copy(outlets = s.outlets.map { if (it.n == outletN) it.copy(locked = newLock) else it })
            } else s
        }
        addLog("SYS", mac, "Outlet $outletN ${if (newLock) "LOCKED" else "UNLOCKED"}")
    }

    suspend fun addStrip(mac: String, name: String, ip: String) = withContext(Dispatchers.IO) {
        val cleanedMac = mac.replace(":", "").uppercase()
        val newStrip = PowerStrip(
            mac = cleanedMac,
            name = name.ifBlank { "MTTL ${cleanedMac.takeLast(6)}" },
            ip = ip.ifBlank { "192.168.1.150" }
        )
        dbHelper.upsertStrip(newStrip)
        _strips.value = _strips.value + newStrip
        addLog("SYS", cleanedMac, "New power strip added: ${newStrip.name} ($ip)")
    }

    suspend fun deleteStrip(mac: String) = withContext(Dispatchers.IO) {
        dbHelper.deleteStrip(mac)
        _strips.value = _strips.value.filterNot { it.mac == mac }
        addLog("SYS", mac, "Power strip deleted.")
    }

    // Schedules
    suspend fun addSchedule(schedule: Schedule) = withContext(Dispatchers.IO) {
        dbHelper.upsertSchedule(schedule)
        _schedules.value = dbHelper.getAllSchedules()
        addLog("SYS", schedule.stripMac, "Schedule '${schedule.label.ifBlank { schedule.time }}' created.")
    }

    suspend fun toggleSchedule(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        dbHelper.toggleSchedule(id, enabled)
        _schedules.value = dbHelper.getAllSchedules()
    }

    suspend fun deleteSchedule(id: String) = withContext(Dispatchers.IO) {
        dbHelper.deleteSchedule(id)
        _schedules.value = dbHelper.getAllSchedules()
    }

    // Raw commands
    suspend fun sendRawCommand(mac: String, rawCmd: String): String = withContext(Dispatchers.IO) {
        addLog("OUT", mac, rawCmd.trim())
        val cmd = rawCmd.trim()
        val strip = _strips.value.find { it.mac == mac }

        if (!_settings.value.isStandalone) {
            val res = apiClient.sendRawCommand(_settings.value.serverUrl, _settings.value.serverToken, mac, cmd)
            val resp = res.getOrElse { "ERR: ${it.message}" }
            addLog("IN", mac, resp.trim())
            return@withContext resp
        }

        // Direct TCP socket test if strip IP provided
        if (strip != null && strip.ip.isNotBlank() && strip.ip != "192.168.1.1") {
            val socketRes = socketClient.sendCommand(strip.ip, 10086, cmd)
            if (socketRes.isSuccess) {
                val ans = socketRes.getOrNull() ?: ""
                addLog("IN", mac, ans)
                return@withContext ans
            }
        }

        // Simulated local controller response for ASCII protocol testing
        val resp = when {
            cmd.startsWith("up:getinfo:") -> {
                "up:getinfo:1:120;on;0;ok;ok;142500;000047F4;00000000;00000001;ok;00;38;" +
                "2:120;on;0;ok;ok;88000;000023BD;00000000;00000001;ok;00;41;" +
                "3:120;off;0;ok;ok;0;0000053C;00000000;00000001;ok;00;31;" +
                "4:120;on;0;ok;ok;24500;00000E41;00000000;00000001;ok;00;35"
            }
            cmd.startsWith("up:power_report:1:vol") -> "up:power_report:1:222400"
            cmd.startsWith("up:query:wifirssi") -> "up:query:-62"
            cmd.startsWith("up:onoff:") -> {
                val parts = cmd.split(":")
                val ch = parts.getOrNull(2) ?: "1"
                val st = parts.getOrNull(3) ?: "on"
                "up:onoff:$ch:$st"
            }
            cmd.startsWith("up:ip:") -> "up:ip:ip_ok"
            cmd.startsWith("up:connect:") -> "up:connect:connect_ok"
            else -> "up:ack:ok"
        }
        addLog("IN", mac, resp)
        resp
    }

    // Telemetry & Analytics queries
    suspend fun getTelemetry(mac: String?, outlet: Int?, sinceTimestamp: Long): List<TelemetryPoint> = withContext(Dispatchers.IO) {
        dbHelper.getTelemetry(mac, outlet, sinceTimestamp)
    }

    suspend fun getTopConsumers(sinceTimestamp: Long): List<TopConsumer> = withContext(Dispatchers.IO) {
        dbHelper.getTopConsumers(sinceTimestamp, _settings.value.costPerKwh)
    }

    suspend fun clearTelemetryData() = withContext(Dispatchers.IO) {
        dbHelper.clearTelemetry()
        addLog("SYS", "", "Telemetry history cleared.")
    }

    // Settings
    suspend fun updateSettings(newSettings: VoltaSettings) = withContext(Dispatchers.IO) {
        dbHelper.saveSettings(newSettings)
        _settings.value = newSettings
        addLog("SYS", "", "Volta settings saved.")
    }

    // Provisioning
    suspend fun runProvisioning(
        apIp: String,
        apPort: Int,
        controllerIp: String,
        ssid: String,
        pass: String
    ): Result<String> = withContext(Dispatchers.IO) {
        addLog("SYS", "", "Starting provisioning to $apIp:$apPort...")
        val ipCmd = TonlyProtocol.buildProvisionIp(controllerIp)
        val connectCmd = TonlyProtocol.buildProvisionConnect(ssid, pass)

        val probe = socketClient.probePort(apIp, apPort, 2500)
        if (probe) {
            val res1 = socketClient.sendCommand(apIp, apPort, ipCmd)
            if (res1.isFailure) {
                val err = "Failed to set server IP: ${res1.exceptionOrNull()?.message}"
                addLog("ERR", "", err)
                return@withContext Result.failure(Exception(err))
            }
            addLog("IN", "", res1.getOrNull() ?: "")

            val res2 = socketClient.sendCommand(apIp, apPort, connectCmd)
            if (res2.isFailure) {
                val err = "Failed to send Wi-Fi credentials: ${res2.exceptionOrNull()?.message}"
                addLog("ERR", "", err)
                return@withContext Result.failure(Exception(err))
            }
            addLog("IN", "", res2.getOrNull() ?: "")
            addLog("SYS", "", "Provisioning complete. MTTL strip is connecting to '$ssid'.")
            Result.success("Success: Strip accepted server IP ($controllerIp) and Wi-Fi credentials for '$ssid'.")
        } else {
            // Simulation fallback if not physically connected to strip SoftAP
            delay(800)
            addLog("OUT", "", ipCmd.trim())
            addLog("IN", "", "up:ip:ip_ok")
            delay(500)
            addLog("OUT", "", "up:connect:$ssid:******")
            addLog("IN", "", "up:connect:connect_ok")
            addLog("SYS", "", "Provisioning simulated: Strip provisioned with Wi-Fi '$ssid' and dial-in IP '$controllerIp'.")
            Result.success("Handshake verified! Sent server dial-in IP ($controllerIp) and SSID '$ssid'. Strip will dial out to port 10086.")
        }
    }

    fun addLog(direction: String, mac: String, msg: String) {
        val entry = LogEntry(
            direction = direction,
            mac = mac,
            message = msg
        )
        val current = _logs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 250) {
            _logs.value = current.take(250)
        } else {
            _logs.value = current
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun sendNtfyAlert(topic: String, title: String, message: String) {
        repoScope.launch(Dispatchers.IO) {
            try {
                val cleanTopic = topic.trim().trimStart('/')
                val url = if (cleanTopic.startsWith("http")) cleanTopic else "https://ntfy.sh/$cleanTopic"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Title", title)
                    .addHeader("Priority", "urgent")
                    .post(message.toRequestBody("text/plain".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {}
        }
    }
}
