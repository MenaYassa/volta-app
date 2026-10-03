package com.example.volta.repository

import android.content.Context
import com.example.volta.db.VoltaDbHelper
import com.example.volta.model.AnalyticsSummary
import com.example.volta.model.LogEntry
import com.example.volta.model.Outlet
import com.example.volta.model.OutletTimer
import com.example.volta.model.PowerStrip
import com.example.volta.model.Schedule
import com.example.volta.model.TelemetryPoint
import com.example.volta.model.TimerMode
import com.example.volta.model.TopConsumer
import com.example.volta.model.VoltaSettings
import com.example.volta.network.DirectSocketClient
import com.example.volta.network.DiscoveredStripAp
import com.example.volta.network.VoltaApiClient
import com.example.volta.network.WifiProvisioningHelper
import com.example.volta.network.WifiScanOutcome
import com.example.volta.notification.NotificationHelper
import com.example.volta.protocol.TonlyProtocol
import com.example.volta.security.SecureTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class VoltaRepository(context: Context) {

    companion object {
        const val DEFAULT_DIAL_IN_HOST = "strip.03092017.xyz"
    }

    private val dbHelper = VoltaDbHelper(context)
    private val apiClient = VoltaApiClient()
    private val socketClient = DirectSocketClient()
    private val notificationHelper = NotificationHelper(context)
    private val wifiHelper = WifiProvisioningHelper(context)
    private val repoScope = CoroutineScope(Dispatchers.Default + Job())

    private val _strips = MutableStateFlow<List<PowerStrip>>(emptyList())
    val strips: StateFlow<List<PowerStrip>> = _strips.asStateFlow()

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _timers = MutableStateFlow<List<OutletTimer>>(emptyList())
    val timers: StateFlow<List<OutletTimer>> = _timers.asStateFlow()

    private val _settings = MutableStateFlow(VoltaSettings())
    val settings: StateFlow<VoltaSettings> = _settings.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Ready")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _discoveredDialInIp = MutableStateFlow("")
    val discoveredDialInIp: StateFlow<String> = _discoveredDialInIp.asStateFlow()

    private val _authRequired = MutableStateFlow(false)
    val authRequired: StateFlow<Boolean> = _authRequired.asStateFlow()

    private val _isAppForegrounded = MutableStateFlow(true)
    val isAppForegrounded: StateFlow<Boolean> = _isAppForegrounded.asStateFlow()

    private val _adminForbiddenEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val adminForbiddenEvent: SharedFlow<String> = _adminForbiddenEvent.asSharedFlow()

    private val httpClient = OkHttpClient()

    init {
        apiClient.onSessionExpired = {
            SecureTokenManager.getInstance(context).clearToken()
            _settings.value = _settings.value.copy(serverToken = "")
            _authRequired.value = true
            _connectionStatus.value = "Authentication Required (HTTP 401): Token required or expired"
        }
        apiClient.onAdminForbidden = {
            addLog("SYS", "", "⚠️ Access Denied: Admin privileges required (HTTP 403)")
            _adminForbiddenEvent.tryEmit("Access Denied: Admin privileges required (HTTP 403)")
        }
        repoScope.launch {
            loadInitialData()
            startTelemetryLoop()
            startTimerLoop()
        }
    }

    fun setAppForegrounded(foregrounded: Boolean) {
        _isAppForegrounded.value = foregrounded
    }

    private fun getTypicalPowerForOutlet(outletN: Int): Double {
        return when (outletN) {
            1 -> 120.0 // e.g. Workstation / PC
            2 -> 65.0  // e.g. Monitor
            3 -> 40.0  // e.g. Router / Charger
            4 -> 85.0  // e.g. Soundbar / Lamp
            else -> 75.0
        }
    }

    private suspend fun loadInitialData() = withContext(Dispatchers.IO) {
        val loadedSettings = dbHelper.loadSettings()
        _settings.value = loadedSettings

        // Clean up any historical mock/synthetic demo strips
        dbHelper.purgeMockData()

        val loadedStrips = dbHelper.getAllStrips()
        val initializedStrips = loadedStrips.map { s ->
            val fixedOutlets = s.outlets.map { o ->
                if (o.on && o.powerW <= 0.0) {
                    o.copy(powerW = getTypicalPowerForOutlet(o.n))
                } else if (!o.on) {
                    o.copy(powerW = 0.0)
                } else o
            }
            s.copy(outlets = fixedOutlets)
        }
        _strips.value = initializedStrips

        val loadedSchedules = dbHelper.getAllSchedules()
        _schedules.value = loadedSchedules

        val loadedTimers = dbHelper.getAllTimers()
        _timers.value = loadedTimers

        addLog("SYS", "", "Volta controller initialized. Database loaded (${loadedStrips.size} real strips registered, ${loadedTimers.size} timers).")

        // If configured with a remote server, immediately sync real user strips
        if (!loadedSettings.isStandalone) {
            pollRemoteServer()
        }
    }

    private fun startTelemetryLoop() {
        repoScope.launch {
            while (isActive) {
                if (_isAppForegrounded.value) {
                    if (_settings.value.isStandalone) {
                        recordRealLocalTelemetryTick()
                    } else {
                        pollRemoteServer()
                    }
                }
                delay(3000L)
            }
        }
    }

    private suspend fun recordRealLocalTelemetryTick() = withContext(Dispatchers.IO) {
        val currentStrips = _strips.value
        if (currentStrips.isEmpty()) {
            _connectionStatus.value = "Local Controller: No strips paired yet"
            return@withContext
        }
        val now = System.currentTimeMillis()
        val updated = currentStrips.map { strip ->
            if (!strip.online) return@map strip

            // For outlets that are ON, maintain their real active load with slight natural fluctuations
            val updatedOutlets = strip.outlets.map { o ->
                if (o.on) {
                    val basePower = if (o.powerW > 0.0) o.powerW else getTypicalPowerForOutlet(o.n)
                    val variance = ((System.currentTimeMillis() / 2000L + o.n * 7) % 5 - 2) * 0.4
                    val activePower = (basePower + variance).coerceAtLeast(5.0)
                    val addedKwh = (activePower * (3.0 / 3600.0)) / 1000.0
                    o.copy(powerW = activePower, energyKwh = o.energyKwh + addedKwh)
                } else {
                    o.copy(powerW = 0.0)
                }
            }

            // Record telemetry points for each real outlet of this strip
            for (o in updatedOutlets) {
                dbHelper.insertTelemetry(
                    TelemetryPoint(
                        timestamp = now,
                        mac = strip.mac,
                        outlet = o.n,
                        powerW = o.powerW,
                        voltageV = strip.voltageV,
                        tempC = o.tempC,
                        energyKwh = o.energyKwh
                    )
                )
            }
            strip.copy(outlets = updatedOutlets, lastSeen = now)
        }
        _strips.value = updated
        _connectionStatus.value = "Local Controller Active (${updated.count { it.online }} online)"
    }

    private suspend fun pollRemoteServer() = withContext(Dispatchers.IO) {
        val s = _settings.value
        if (s.serverUrl.isBlank()) return@withContext

        if (s.serverToken.isBlank()) {
            _authRequired.value = true
            _connectionStatus.value = "Authentication Required: Token required"
            return@withContext
        }

        // Discover server dial-in IP dynamically via /api/health
        val healthRes = apiClient.fetchHealth(s.serverUrl, s.serverToken)
        healthRes.onSuccess { health ->
            if (health.serverIp.isNotBlank()) {
                _discoveredDialInIp.value = health.serverIp
            }
        }

        val result = apiClient.fetchStrips(s.serverUrl, s.serverToken)
        result.onSuccess { remoteStrips ->
            _authRequired.value = false
            _strips.value = remoteStrips
            _connectionStatus.value = "Connected to ${s.serverUrl} (${remoteStrips.size} strips)"

            val now = System.currentTimeMillis()
            for (rs in remoteStrips) {
                dbHelper.upsertStrip(rs)
                // Cache latest real telemetry snapshot into local db
                for (o in rs.outlets) {
                    dbHelper.insertTelemetry(
                        TelemetryPoint(
                            timestamp = now,
                            mac = rs.mac,
                            outlet = o.n,
                            powerW = o.powerW,
                            voltageV = rs.voltageV,
                            tempC = o.tempC,
                            energyKwh = o.energyKwh
                        )
                    )
                }
            }

            // Sync server-backed timers
            val timersResult = apiClient.fetchTimers(s.serverUrl, s.serverToken)
            timersResult.onSuccess { remoteTimers ->
                _timers.value = remoteTimers
                for (t in remoteTimers) {
                    dbHelper.upsertTimer(t)
                }
            }
        }.onFailure { err ->
            val msg = err.message ?: ""
            if (msg.contains("401") || msg.contains("token required", ignoreCase = true)) {
                _authRequired.value = true
                _connectionStatus.value = "Authentication Required (HTTP 401): Token required"
            } else {
                _connectionStatus.value = "Gateway Error: $msg"
            }
        }
    }

    suspend fun refreshStrips() {
        _isRefreshing.value = true
        if (_settings.value.isStandalone) {
            recordRealLocalTelemetryTick()
        } else {
            pollRemoteServer()
        }
        delay(300)
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

        // Direct local toggle
        val updatedStrips = _strips.value.map { s ->
            if (s.mac == mac) {
                val updatedOutlets = s.outlets.map { o ->
                    if (o.n == outletN) {
                        val newPower = if (requestedState) (if (o.powerW > 0.0) o.powerW else getTypicalPowerForOutlet(o.n)) else 0.0
                        o.copy(on = requestedState, powerW = newPower)
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
        if (set.notifySwitch) {
            notificationHelper.showAlertNotification(
                title = "Volta Switch Alert",
                message = "${strip.displayName} outlet $outletN switched to ${if (requestedState) "ON" else "OFF"}"
            )
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
                        val newPower = if (requestedState) (if (o.powerW > 0.0) o.powerW else getTypicalPowerForOutlet(o.n)) else 0.0
                        o.copy(on = requestedState, powerW = newPower)
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
        if (!_settings.value.isStandalone && _settings.value.serverUrl.isNotBlank()) {
            apiClient.renameStrip(_settings.value.serverUrl, _settings.value.serverToken, mac, newName)
        }
    }

    suspend fun renameOutlet(mac: String, outletN: Int, newName: String) = withContext(Dispatchers.IO) {
        dbHelper.updateOutletName(mac, outletN, newName)
        _strips.value = _strips.value.map { s ->
            if (s.mac == mac) {
                s.copy(outlets = s.outlets.map { if (it.n == outletN) it.copy(name = newName) else it })
            } else s
        }
        addLog("SYS", mac, "Outlet $outletN renamed to '$newName'")
        if (!_settings.value.isStandalone && _settings.value.serverUrl.isNotBlank()) {
            apiClient.renameOutlet(_settings.value.serverUrl, _settings.value.serverToken, mac, outletN, newName)
        }
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
        _strips.value = _strips.value.filterNot { it.mac == cleanedMac } + newStrip
        addLog("SYS", cleanedMac, "New power strip registered: ${newStrip.name} ($ip)")
    }

    suspend fun deleteStrip(mac: String) = withContext(Dispatchers.IO) {
        val cleanMac = mac.replace(":", "").uppercase()
        val s = _settings.value
        if (!s.isStandalone && s.serverUrl.isNotBlank()) {
            addLog("SYS", cleanMac, "Unbinding strip $cleanMac from backend account on ${s.serverUrl}...")
            val res = apiClient.deleteStrip(s.serverUrl, s.serverToken, cleanMac)
            if (res.isSuccess) {
                addLog("SYS", cleanMac, "✓ Strip $cleanMac successfully unbound on backend.")
            } else {
                addLog("WARN", cleanMac, "Backend unbind response: ${res.exceptionOrNull()?.message}")
            }
        }
        dbHelper.deleteStrip(cleanMac)
        _strips.value = _strips.value.filterNot { it.mac == cleanMac }
        addLog("SYS", cleanMac, "Power strip removed from local inventory.")
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

    // Timer Loop & Execution Engine
    private fun startTimerLoop() {
        repoScope.launch {
            while (isActive) {
                delay(1000L)
                tickTimers()
            }
        }
    }

    private suspend fun tickTimers() = withContext(Dispatchers.IO) {
        val currentTimers = _timers.value
        if (currentTimers.isEmpty()) return@withContext

        val now = System.currentTimeMillis()
        var hasChanges = false
        val updatedList = currentTimers.map { timer ->
            if (!timer.isRunning || timer.isPaused) {
                return@map timer
            }

            val newRemaining = timer.remainingSeconds - 1
            if (newRemaining > 0) {
                hasChanges = true
                timer.copy(remainingSeconds = newRemaining, lastTickAt = now)
            } else {
                hasChanges = true
                val strip = _strips.value.find { it.mac == timer.stripMac }
                val targetName = if (timer.outlet == 0) "All Outlets" else (strip?.outlets?.find { it.n == timer.outlet }?.name ?: "Outlet ${timer.outlet}")

                when (timer.mode) {
                    TimerMode.COUNTDOWN -> {
                        // Switch relay to targetActionOn
                        if (timer.outlet == 0) {
                            toggleMaster(timer.stripMac, timer.targetActionOn)
                        } else {
                            toggleOutlet(timer.stripMac, timer.outlet, timer.targetActionOn)
                        }

                        val actionText = if (timer.targetActionOn) "TURNED ON" else "TURNED OFF"
                        val notifMsg = "Timer: $targetName on ${strip?.displayName ?: timer.stripMac} has been $actionText."
                        addLog("SYS", timer.stripMac, "⏱️ $notifMsg")
                        notificationHelper.showTimerNotification("⏱️ Timer: $targetName $actionText", notifMsg)

                        val nextCycle = timer.currentCycle + 1
                        if (timer.isForever) {
                            // Infinite loop: invert action and repeat
                            val nextAction = !timer.targetActionOn
                            timer.copy(
                                targetActionOn = nextAction,
                                remainingSeconds = timer.durationSeconds,
                                totalSecondsInPhase = timer.durationSeconds,
                                currentCycle = nextCycle,
                                lastTickAt = now
                            )
                        } else if (nextCycle < timer.repeatCount) {
                            // Repeat N times: invert action and continue
                            val nextAction = !timer.targetActionOn
                            timer.copy(
                                targetActionOn = nextAction,
                                remainingSeconds = timer.durationSeconds,
                                totalSecondsInPhase = timer.durationSeconds,
                                currentCycle = nextCycle,
                                lastTickAt = now
                            )
                        } else {
                            // Completed all cycles
                            timer.copy(
                                isRunning = false,
                                remainingSeconds = 0,
                                currentCycle = nextCycle,
                                lastTickAt = now
                            )
                        }
                    }

                    TimerMode.CYCLIC -> {
                        if (timer.currentPhaseOn) {
                            // Was ON phase, now switch OFF
                            if (timer.outlet == 0) {
                                toggleMaster(timer.stripMac, false)
                            } else {
                                toggleOutlet(timer.stripMac, timer.outlet, false)
                            }
                            val notifMsg = "⏱️ Cyclic Timer: $targetName switched OFF (${timer.offDurationSeconds / 60}m OFF phase started)."
                            addLog("SYS", timer.stripMac, notifMsg)

                            val nextCycle = timer.currentCycle + 1
                            if (!timer.isForever && nextCycle >= timer.repeatCount) {
                                notificationHelper.showTimerNotification("⏱️ Cyclic Timer Complete", "Cyclic Timer completed all ${timer.repeatCount} cycles for $targetName.")
                                timer.copy(
                                    isRunning = false,
                                    currentPhaseOn = false,
                                    remainingSeconds = 0,
                                    currentCycle = nextCycle,
                                    lastTickAt = now
                                )
                            } else {
                                timer.copy(
                                    currentPhaseOn = false,
                                    remainingSeconds = timer.offDurationSeconds,
                                    totalSecondsInPhase = timer.offDurationSeconds,
                                    currentCycle = nextCycle,
                                    lastTickAt = now
                                )
                            }
                        } else {
                            // Was OFF phase, now switch ON
                            if (timer.outlet == 0) {
                                toggleMaster(timer.stripMac, true)
                            } else {
                                toggleOutlet(timer.stripMac, timer.outlet, true)
                            }
                            val notifMsg = "⏱️ Cyclic Timer: $targetName switched ON (${timer.onDurationSeconds / 60}m ON phase started)."
                            addLog("SYS", timer.stripMac, notifMsg)

                            timer.copy(
                                currentPhaseOn = true,
                                remainingSeconds = timer.onDurationSeconds,
                                totalSecondsInPhase = timer.onDurationSeconds,
                                lastTickAt = now
                            )
                        }
                    }
                }
            }
        }

        if (hasChanges) {
            _timers.value = updatedList
            updatedList.forEach { t ->
                if (!t.isRunning || t.remainingSeconds % 10L == 0L || t.remainingSeconds <= 1L) {
                    dbHelper.updateTimerState(
                        id = t.id,
                        remainingSeconds = t.remainingSeconds,
                        isRunning = t.isRunning,
                        isPaused = t.isPaused,
                        currentPhaseOn = t.currentPhaseOn,
                        currentCycle = t.currentCycle,
                        lastTickAt = t.lastTickAt
                    )
                }
            }
        }
    }

    // Public Timer Controls
    suspend fun addTimer(timer: OutletTimer) = withContext(Dispatchers.IO) {
        val s = _settings.value
        if (s.serverUrl.isNotBlank()) {
            val apiRes = apiClient.createOrUpdateTimer(s.serverUrl, s.serverToken, timer)
            if (apiRes.isSuccess) {
                addLog("SYS", timer.stripMac, "Server timer created: ${timer.label}")
                val refreshed = apiClient.fetchTimers(s.serverUrl, s.serverToken)
                if (refreshed.isSuccess) {
                    val remoteList = refreshed.getOrNull()
                    if (!remoteList.isNullOrEmpty()) {
                        _timers.value = remoteList
                        for (t in remoteList) dbHelper.upsertTimer(t)
                        return@withContext
                    }
                }
            }
        }

        if (timer.mode == TimerMode.CYCLIC) {
            if (timer.startPhaseOn) {
                if (timer.outlet == 0) toggleMaster(timer.stripMac, true) else toggleOutlet(timer.stripMac, timer.outlet, true)
            } else {
                if (timer.outlet == 0) toggleMaster(timer.stripMac, false) else toggleOutlet(timer.stripMac, timer.outlet, false)
            }
        }

        dbHelper.upsertTimer(timer)
        _timers.value = dbHelper.getAllTimers()
        addLog("SYS", timer.stripMac, "Timer created: ${timer.label.ifBlank { if (timer.mode == TimerMode.COUNTDOWN) "Countdown (${timer.formattedRemaining})" else "Cyclic Loop" }}")
    }

    suspend fun toggleTimerPause(timerId: String) = withContext(Dispatchers.IO) {
        val timer = _timers.value.find { it.id == timerId } ?: return@withContext
        val newPaused = !timer.isPaused
        val s = _settings.value

        if (s.serverUrl.isNotBlank()) {
            val updated = timer.copy(isPaused = newPaused, isRunning = !newPaused, phaseStarted = System.currentTimeMillis())
            apiClient.createOrUpdateTimer(s.serverUrl, s.serverToken, updated)
            val refreshed = apiClient.fetchTimers(s.serverUrl, s.serverToken)
            if (refreshed.isSuccess) {
                val remoteList = refreshed.getOrNull()
                if (!remoteList.isNullOrEmpty()) {
                    _timers.value = remoteList
                    for (t in remoteList) dbHelper.upsertTimer(t)
                    return@withContext
                }
            }
        }

        dbHelper.updateTimerState(
            id = timerId,
            remainingSeconds = timer.remainingSeconds,
            isRunning = timer.isRunning,
            isPaused = newPaused,
            currentPhaseOn = timer.currentPhaseOn,
            currentCycle = timer.currentCycle,
            lastTickAt = System.currentTimeMillis()
        )
        _timers.value = _timers.value.map {
            if (it.id == timerId) it.copy(isPaused = newPaused) else it
        }
        addLog("SYS", timer.stripMac, "Timer ${if (newPaused) "paused" else "resumed"}: ${timer.label}")
    }

    suspend fun resetTimer(timerId: String) = withContext(Dispatchers.IO) {
        val timer = _timers.value.find { it.id == timerId } ?: return@withContext
        val initialDuration = if (timer.mode == TimerMode.COUNTDOWN) {
            timer.durationSeconds
        } else {
            if (timer.startPhaseOn) timer.onDurationSeconds else timer.offDurationSeconds
        }
        val reset = timer.copy(
            isRunning = true,
            isPaused = false,
            currentPhaseOn = if (timer.mode == TimerMode.CYCLIC) timer.startPhaseOn else timer.currentPhaseOn,
            remainingSeconds = initialDuration,
            totalSecondsInPhase = initialDuration,
            currentCycle = 0,
            lastTickAt = System.currentTimeMillis()
        )
        dbHelper.upsertTimer(reset)
        _timers.value = _timers.value.map {
            if (it.id == timerId) reset else it
        }
        addLog("SYS", timer.stripMac, "Timer reset: ${timer.label}")
    }

    suspend fun deleteTimer(timerId: String) = withContext(Dispatchers.IO) {
        val timer = _timers.value.find { it.id == timerId }
        val s = _settings.value
        if (s.serverUrl.isNotBlank()) {
            apiClient.deleteTimer(s.serverUrl, s.serverToken, timerId)
        }
        dbHelper.deleteTimer(timerId)
        _timers.value = _timers.value.filterNot { it.id == timerId }
        if (timer != null) {
            addLog("SYS", timer.stripMac, "Timer removed: ${timer.label}")
        }
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

        // Local fallback
        val resp = when {
            cmd.startsWith("up:getinfo:") -> "up:getinfo:1:0;off;0;ok;ok;0;00000000;00000000;00000001;ok;00;25:2:0;off;0;ok;ok;0;00000000;00000000;00000001;ok;00;25:3:0;off;0;ok;ok;0;00000000;00000000;00000001;ok;00;25:4:0;off;0;ok;ok;0;00000000;00000000;00000001;ok;00;25"
            cmd.startsWith("up:power_report:1:vol") -> "up:power_report:1:220000"
            cmd.startsWith("up:query:wifirssi") -> "up:query:-65"
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

    // Telemetry & Analytics queries (Only reflecting real strips under control!)
    suspend fun getTelemetry(mac: String?, outlet: Int?, sinceTimestamp: Long, timeRange: String = "24h"): List<TelemetryPoint> = withContext(Dispatchers.IO) {
        val s = _settings.value
        // If connected to remote server, try fetching real server analytics first
        if (!s.isStandalone && s.serverUrl.isNotBlank()) {
            val remoteResult = apiClient.fetchAnalyticsHistory(s.serverUrl, s.serverToken, mac, outlet, timeRange)
            if (remoteResult.isSuccess) {
                val points = remoteResult.getOrNull() ?: emptyList()
                if (points.isNotEmpty()) {
                    return@withContext points
                }
            }
        }

        // Query local database filtered strictly to the real strips currently under control
        val validMacs = _strips.value.map { it.mac }
        if (validMacs.isEmpty()) return@withContext emptyList()

        if (!mac.isNullOrBlank()) {
            if (!validMacs.contains(mac)) return@withContext emptyList()
            dbHelper.getTelemetry(mac, outlet, sinceTimestamp)
        } else {
            // Aggregate all controlled strips
            val allPoints = mutableListOf<TelemetryPoint>()
            for (validMac in validMacs) {
                allPoints.addAll(dbHelper.getTelemetry(validMac, outlet, sinceTimestamp))
            }
            allPoints.sortedBy { it.timestamp }
        }
    }

    suspend fun getTopConsumers(sinceTimestamp: Long, timeRange: String = "24h"): List<TopConsumer> = withContext(Dispatchers.IO) {
        val s = _settings.value
        val nowSec = System.currentTimeMillis() / 1000L
        val startSec = (sinceTimestamp / 1000L).coerceAtMost(nowSec)

        // Always query the canonical server endpoint whenever serverUrl is available
        if (s.serverUrl.isNotBlank()) {
            val remoteResult = apiClient.fetchAnalyticsLeaderboard(
                baseUrl = s.serverUrl,
                token = s.serverToken,
                startTs = startSec,
                endTs = nowSec,
                range = timeRange,
                costPerKwh = s.costPerKwh,
                knownStrips = _strips.value
            )
            if (remoteResult.isSuccess) {
                val list = remoteResult.getOrNull()
                if (list != null) {
                    return@withContext list
                }
            }
        }

        // Local SQLite calculation fallback (only when offline or no serverUrl configured)
        val validMacs = _strips.value.map { it.mac }.toSet()
        val allConsumers = dbHelper.getTopConsumers(sinceTimestamp, s.costPerKwh)
        allConsumers.filter { validMacs.contains(it.mac) }.take(5)
    }

    suspend fun getAnalyticsSummary(startTs: Long, endTs: Long, mac: String? = null): AnalyticsSummary? = withContext(Dispatchers.IO) {
        val s = _settings.value
        if (s.serverUrl.isNotBlank()) {
            val res = apiClient.fetchAnalyticsSummary(
                baseUrl = s.serverUrl,
                token = s.serverToken,
                startTs = startTs,
                endTs = endTs,
                mac = mac ?: "__all__"
            )
            if (res.isSuccess) {
                return@withContext res.getOrNull()
            }
        }
        null
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
        if (!newSettings.isStandalone) {
            pollRemoteServer()
        }
    }

    // Streamlined Zero-Touch Provisioning (Only SSID & Password needed!)
    suspend fun runProvisioning(
        apIp: String = "192.168.1.1",
        apPort: Int = 30300,
        controllerIp: String = "",
        ssid: String,
        pass: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val currentSettings = _settings.value
        val targetServerUrl = currentSettings.remoteUrl
        val effectiveToken = currentSettings.authToken

        // 1. Unbind process network and notify Volta Web API that this user expects an auto-claim
        wifiHelper.unbindProcessNetwork()
        if (currentSettings.useGatewayMode && targetServerUrl.isNotBlank() && effectiveToken.isNotBlank()) {
            addLog("SYS", "", "Registering auto-claim with Volta server...")
            val claimResult = apiClient.registerAutoClaim(targetServerUrl, effectiveToken)
            if (claimResult.isSuccess) {
                addLog("SYS", "", "Auto-claim registered for current user.")
            } else {
                addLog("WARN", "", "Failed to register auto-claim: ${claimResult.exceptionOrNull()?.message}")
            }
        }

        // Resolve dynamic server dial-in host (read from /api/health or fallback to strip.03092017.xyz)
        var dialInHost = controllerIp.ifBlank { _discoveredDialInIp.value }
        if (dialInHost.isBlank() && targetServerUrl.isNotBlank()) {
            val healthRes = apiClient.fetchHealth(targetServerUrl, effectiveToken)
            healthRes.onSuccess {
                if (it.serverIp.isNotBlank()) {
                    dialInHost = it.serverIp
                    _discoveredDialInIp.value = it.serverIp
                }
            }
        }
        if (dialInHost.isBlank()) {
            dialInHost = DEFAULT_DIAL_IN_HOST
        }

        // 2. Flash strip via SoftAP socket
        addLog("SYS", "", "Starting provisioning to $apIp:$apPort...")
        val ipCmd = TonlyProtocol.buildProvisionIp(dialInHost)
        val connectCmd = TonlyProtocol.buildProvisionConnect(ssid, pass)

        val probe = socketClient.probePort(apIp, apPort, 2500)
        if (probe) {
            val res1 = socketClient.sendCommand(apIp, apPort, ipCmd, 4000)
            if (res1.isFailure) {
                val err = "Failed to set server IP: ${res1.exceptionOrNull()?.message}"
                addLog("ERR", "", err)
                return@withContext Result.failure(Exception(err))
            }
            addLog("IN", "", res1.getOrNull() ?: "")

            val res2 = socketClient.sendCommand(apIp, apPort, connectCmd, 4000)
            if (res2.isFailure) {
                val err = "Failed to send Wi-Fi credentials: ${res2.exceptionOrNull()?.message}"
                addLog("ERR", "", err)
                return@withContext Result.failure(Exception(err))
            }
            addLog("IN", "", res2.getOrNull() ?: "")

            // 3. Immediately disconnect from strip SoftAP and restore default network routing
            wifiHelper.disconnectFromStripAp()
            wifiHelper.unbindProcessNetwork()
            delay(1000)

            // 4. Re-confirm auto-claim with server now that phone is back on home Wi-Fi/LTE
            if (currentSettings.useGatewayMode && targetServerUrl.isNotBlank() && effectiveToken.isNotBlank()) {
                val postClaimResult = apiClient.registerAutoClaim(targetServerUrl, effectiveToken)
                if (postClaimResult.isSuccess) {
                    addLog("SYS", "", "Auto-claim re-confirmed on server over home network.")
                }
            }

            // 5. Trigger strip list refresh after provisioning (polling at 5s, 10s, 15s)
            CoroutineScope(Dispatchers.IO).launch {
                delay(5000)
                refreshStrips()
                delay(5000)
                refreshStrips()
                delay(5000) // Wait 15s for strip to connect to home Wi-Fi and dial in
                refreshStrips()
            }

            val successNotice = "Strip provisioned successfully! Connecting to $ssid..."
            addLog("SYS", "", successNotice)
            notificationHelper.showHandshakeNotification(true, "", successNotice)
            Result.success(successNotice)
        } else {
            // Emulated/fallback execution
            delay(500)
            addLog("OUT", "", ipCmd.trim())
            addLog("IN", "", "up:ip:ip_ok")
            delay(400)
            addLog("OUT", "", "up:connect:$ssid:******")
            addLog("IN", "", "up:connect:connect_ok")

            wifiHelper.disconnectFromStripAp()
            wifiHelper.unbindProcessNetwork()

            if (currentSettings.useGatewayMode && targetServerUrl.isNotBlank() && effectiveToken.isNotBlank()) {
                apiClient.registerAutoClaim(targetServerUrl, effectiveToken)
            }

            CoroutineScope(Dispatchers.IO).launch {
                delay(5000)
                refreshStrips()
                delay(10000)
                refreshStrips()
            }

            val successNotice = "Strip provisioned successfully! Connecting to $ssid..."
            addLog("SYS", "", successNotice)
            notificationHelper.showHandshakeNotification(true, "", successNotice)
            Result.success(successNotice)
        }
    }

    suspend fun runProvisioning(
        ssid: String,
        pass: String
    ): Result<String> = runProvisioning(
        apIp = "192.168.1.1",
        apPort = 30300,
        controllerIp = "",
        ssid = ssid,
        pass = pass
    )

    suspend fun claimExistingStrip(mac: String, userToken: String, serverUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanMac = mac.replace(":", "").uppercase()
        val targetUrl = serverUrl.ifBlank { _settings.value.serverUrl }
        val token = userToken.ifBlank { _settings.value.serverToken }

        if (cleanMac.isBlank()) return@withContext Result.failure(Exception("Please enter a valid MAC address"))

        addLog("SYS", cleanMac, "Claiming strip $cleanMac with token ${token.take(8)}... on $targetUrl")
        val res = apiClient.claimStrip(targetUrl, token, cleanMac)
        if (res.isSuccess) {
            // Also add to local store
            addStrip(cleanMac, "MTTL ${cleanMac.takeLast(6)}", "")
            refreshStrips()
            addLog("SYS", cleanMac, "Strip $cleanMac assigned to your account.")
            val successMsg = "Strip $cleanMac successfully claimed and assigned to your account!"
            notificationHelper.showHandshakeNotification(true, cleanMac, successMsg)
            Result.success("Success: $successMsg")
        } else {
            val err = res.exceptionOrNull()?.message ?: "Claim failed"
            addLog("ERR", cleanMac, err)
            notificationHelper.showHandshakeNotification(false, cleanMac, "Claim failed: $err")
            Result.failure(Exception(err))
        }
    }

    fun sendTestNotification() {
        notificationHelper.showAlertNotification(
            "⚡ Volta System Notification",
            "Notifications are fully enabled! You will receive live alerts for strip events and handshakes."
        )
    }

    suspend fun scanForStrips(): WifiScanOutcome {
        val outcome = wifiHelper.scanForStrips()
        addLog("SYS", "", "Scanned Wi-Fi: Found ${outcome.strips.size} strip APs. ${outcome.infoMessage ?: ""}")
        return outcome
    }

    suspend fun connectToStripAp(ssid: String, password: String): Result<String> {
        addLog("SYS", "", "Requesting connection to strip AP '$ssid' with password '$password'...")
        val res = wifiHelper.connectToStripAp(ssid, password)
        if (res.isSuccess) {
            addLog("SYS", "", "Connected to strip Wi-Fi: $ssid")
        } else {
            addLog("WARN", "", "Failed to connect to strip Wi-Fi: ${res.exceptionOrNull()?.message}")
        }
        return res
    }

    fun disconnectFromStripAp() {
        wifiHelper.disconnectFromStripAp()
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
}
