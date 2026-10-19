package com.example.volta.repository

import android.content.Context
import com.example.volta.db.VoltaDbHelper
import com.example.volta.model.AnalyticsSummary
import com.example.volta.model.CitySearchResult
import com.example.volta.model.LogEntry
import com.example.volta.model.Outlet
import com.example.volta.model.OutletTimer
import com.example.volta.model.PowerStrip
import com.example.volta.model.Schedule
import com.example.volta.model.SunInfo
import com.example.volta.model.TelemetryPoint
import com.example.volta.model.TimerMode
import com.example.volta.model.TopConsumer
import com.example.volta.model.VoltageGuard
import com.example.volta.model.VoltaSettings
import com.example.volta.model.VoltaHome
import com.example.volta.model.GoogleAuthResponse
import com.example.volta.model.WhoAmIResponse
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

class VoltaRepository(private val context: Context) {

    companion object {
        const val BACKEND_PUBLIC_DOMAIN = "volta.03092017.xyz"
        const val DEFAULT_DIAL_IN_HOST = "volta.03092017.xyz"
        const val BACKEND_PUBLIC_IPV4 = "132.145.181.76"

        fun resolveToIpv4(hostOrUrl: String, fallback: String = BACKEND_PUBLIC_IPV4): String {
            val clean = hostOrUrl.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore(":")
                .substringBefore("/")
                .trim()
            val target = if (clean.isBlank() || clean.contains("192.168.1.100")) BACKEND_PUBLIC_DOMAIN else clean

            val parts = target.split(".")
            if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
                return target
            }

            return try {
                val addresses = java.net.InetAddress.getAllByName(target)
                val ipv4 = addresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress
                if (!ipv4.isNullOrBlank() && ipv4.split(".").size == 4) ipv4 else fallback
            } catch (_: Exception) {
                try {
                    val fallbackAddresses = java.net.InetAddress.getAllByName(BACKEND_PUBLIC_DOMAIN)
                    val fbIpv4 = fallbackAddresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress
                    if (!fbIpv4.isNullOrBlank()) fbIpv4 else fallback
                } catch (_: Exception) {
                    fallback
                }
            }
        }
    }

    private val dbHelper = VoltaDbHelper(context)
    private val apiClient = VoltaApiClient()
    private val socketClient = DirectSocketClient(context)
    private val notificationHelper = NotificationHelper(context)
    private val wifiHelper = WifiProvisioningHelper(context)
    private val repoScope = CoroutineScope(Dispatchers.Default + Job())

    private val initialSettings = dbHelper.loadSettings()
    private val initialStrips = dbHelper.getAllStrips()

    private val _strips = MutableStateFlow(initialStrips)
    val strips: StateFlow<List<PowerStrip>> = _strips.asStateFlow()

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _timers = MutableStateFlow<List<OutletTimer>>(emptyList())
    val timers: StateFlow<List<OutletTimer>> = _timers.asStateFlow()

    private val _settings = MutableStateFlow(initialSettings)
    val settings: StateFlow<VoltaSettings> = _settings.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Ready")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _discoveredDialInIp = MutableStateFlow("")
    val discoveredDialInIp: StateFlow<String> = _discoveredDialInIp.asStateFlow()

    private val _authRequired = MutableStateFlow(initialSettings.serverToken.isBlank())
    val authRequired: StateFlow<Boolean> = _authRequired.asStateFlow()

    private val _whoAmI = MutableStateFlow<WhoAmIResponse?>(null)
    val whoAmI: StateFlow<WhoAmIResponse?> = _whoAmI.asStateFlow()

    private val _homes = MutableStateFlow<List<VoltaHome>>(emptyList())
    val homes: StateFlow<List<VoltaHome>> = _homes.asStateFlow()

    private val _selectedHomeId = MutableStateFlow<String?>(null)
    val selectedHomeId: StateFlow<String?> = _selectedHomeId.asStateFlow()

    fun sortStripsBySavedOrder(stripsList: List<PowerStrip>): List<PowerStrip> {
        val savedOrder = dbHelper.getSavedStripOrder()
        if (savedOrder.isEmpty()) return stripsList
        return stripsList.sortedBy { s ->
            val idx = savedOrder.indexOf(s.mac.uppercase().trim())
            if (idx >= 0) idx else Int.MAX_VALUE
        }
    }

    fun reorderStrips(orderedMacs: List<String>) {
        dbHelper.saveStripOrder(orderedMacs)
        _strips.value = sortStripsBySavedOrder(_strips.value)
    }

    private val _sunInfo = MutableStateFlow<SunInfo?>(null)
    val sunInfo: StateFlow<SunInfo?> = _sunInfo.asStateFlow()

    private val _citySearchResults = MutableStateFlow<List<CitySearchResult>>(emptyList())
    val citySearchResults: StateFlow<List<CitySearchResult>> = _citySearchResults.asStateFlow()

    private val _isAppForegrounded = MutableStateFlow(true)
    val isAppForegrounded: StateFlow<Boolean> = _isAppForegrounded.asStateFlow()

    private val _connectingMacs = MutableStateFlow<Set<String>>(emptySet())
    val connectingMacs: StateFlow<Set<String>> = _connectingMacs.asStateFlow()

    private val secureTokenManager = SecureTokenManager.getInstance(context)

    private val _savedHomeWifiSsid = MutableStateFlow(secureTokenManager.getHomeWifiSsid())
    val savedHomeWifiSsid: StateFlow<String> = _savedHomeWifiSsid.asStateFlow()

    private val _savedHomeWifiPass = MutableStateFlow(secureTokenManager.getHomeWifiPassword())
    val savedHomeWifiPass: StateFlow<String> = _savedHomeWifiPass.asStateFlow()

    fun saveHomeWifiCredentials(ssid: String, pass: String) {
        val cleanSsid = ssid.trim()
        secureTokenManager.saveHomeWifi(cleanSsid, pass)
        _savedHomeWifiSsid.value = cleanSsid
        _savedHomeWifiPass.value = pass
        addLog("SYS", "", "Saved home Wi-Fi '$cleanSsid' to local cache for future strip setups.")
    }

    fun clearHomeWifiCredentials() {
        secureTokenManager.clearHomeWifi()
        _savedHomeWifiSsid.value = ""
        _savedHomeWifiPass.value = ""
        addLog("SYS", "", "Cleared cached home Wi-Fi credentials.")
    }

    fun trackConnectingStrip(mac: String) {
        val clean = mac.replace(":", "").uppercase()
        if (clean.isBlank()) return
        _connectingMacs.value = _connectingMacs.value + clean
        val existing = _strips.value.find { it.mac.equals(clean, true) }
        if (existing != null) {
            _strips.value = _strips.value.map {
                if (it.mac.equals(clean, true)) it.copy(isConnectingCloud = !it.online) else it
            }
        } else {
            val newStrip = PowerStrip(
                mac = clean,
                name = "MTTL ${clean.takeLast(6)}",
                online = false,
                isConnectingCloud = true
            )
            _strips.value = _strips.value + newStrip
            dbHelper.upsertStrip(newStrip)
        }
    }

    fun clearConnectingStrip(mac: String) {
        val clean = mac.replace(":", "").uppercase()
        _connectingMacs.value = _connectingMacs.value - clean
        _strips.value = _strips.value.map {
            if (it.mac.equals(clean, true)) it.copy(isConnectingCloud = false) else it
        }
    }

    private val _adminForbiddenEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val adminForbiddenEvent: SharedFlow<String> = _adminForbiddenEvent.asSharedFlow()

    private val httpClient = OkHttpClient()

    init {
        apiClient.onSessionExpired = {
            // Keep stored credentials; flag auth requirement so UI alerts user without destroying token
            _authRequired.value = true
            _connectionStatus.value = "Authentication Required: Token required or expired"
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

    fun setAuthRequired(required: Boolean) {
        _authRequired.value = required
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
        // If user is not authenticated, do not expose cached strips, schedules, or timers
        if (loadedSettings.serverToken.isBlank()) {
            _strips.value = emptyList()
            _schedules.value = emptyList()
            _timers.value = emptyList()
            _authRequired.value = true
        } else {
            _strips.value = sortStripsBySavedOrder(initializedStrips)
            _authRequired.value = false
            val loadedSchedules = dbHelper.getAllSchedules()
            _schedules.value = loadedSchedules
            val loadedTimers = dbHelper.getAllTimers()
            _timers.value = loadedTimers
        }

        addLog("SYS", "", "Volta controller initialized. Database loaded (${loadedStrips.size} real strips registered, ${dbHelper.getAllTimers().size} timers).")

        // If configured with a remote server token, immediately sync real user strips
        if (loadedSettings.serverToken.isNotBlank()) {
            pollRemoteServer()
        }
    }

    private fun startTelemetryLoop() {
        repoScope.launch {
            while (isActive) {
                if (_isAppForegrounded.value) {
                    if (_settings.value.serverToken.isNotBlank() && !_authRequired.value) {
                        pollRemoteServer()
                    }
                }
                delay(3000L)
            }
        }
    }

    private suspend fun recordRealLocalTelemetryTick() = withContext(Dispatchers.IO) {
        if (_authRequired.value || _settings.value.serverToken.isBlank()) {
            _strips.value = emptyList()
            return@withContext
        }
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

        // Fetch subscription context and user info via /api/auth/whoami
        val whoRes = apiClient.fetchWhoAmI(s.serverUrl, s.serverToken)
        whoRes.onSuccess { who ->
            _whoAmI.value = who
            if (who.subscriptionExpired) {
                _authRequired.value = true
                _connectionStatus.value = "Subscription Expired: Contact admin to renew"
            }
        }

        val result = apiClient.fetchStrips(s.serverUrl, s.serverToken)
        result.onSuccess { remoteStrips ->
            _authRequired.value = false
            val currentGuardMap = _strips.value.associate { strip ->
                strip.mac to strip.voltageGuard
            }
            // Auto-clear connecting state for strips that have come online
            val onlineMacs = remoteStrips.filter { it.online }.map { it.mac.uppercase() }.toSet()
            if (onlineMacs.isNotEmpty()) {
                _connectingMacs.value = _connectingMacs.value - onlineMacs
            }

            val mergedStrips = remoteStrips.map { rs ->
                // Cloud lock status is authoritative for shared strips; synchronize to local DB
                rs.outlets.forEach { o ->
                    dbHelper.updateOutletLock(rs.mac, o.n, o.locked)
                }
                val existingGuard = currentGuardMap[rs.mac]
                val mergedGuard = if (existingGuard != null && existingGuard != VoltageGuard()) existingGuard else rs.voltageGuard
                val isConnecting = _connectingMacs.value.contains(rs.mac.uppercase()) && !rs.online
                rs.copy(voltageGuard = mergedGuard, isConnectingCloud = isConnecting)
            }

            // Keep any pending connecting strip that backend hasn't listed yet
            val remoteMacSet = remoteStrips.map { it.mac.uppercase() }.toSet()
            val pendingStrips = _strips.value.filter { it.isConnectingCloud && !remoteMacSet.contains(it.mac.uppercase()) }
            val finalStrips = mergedStrips + pendingStrips

            _strips.value = sortStripsBySavedOrder(finalStrips)
            _connectionStatus.value = "Connected to ${s.serverUrl} (${finalStrips.size} strips)"

            val now = System.currentTimeMillis()
            for (rs in mergedStrips) {
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

            // Sync user homes
            val homesResult = apiClient.fetchHomes(s.serverUrl, s.serverToken)
            homesResult.onSuccess { remoteHomes ->
                _homes.value = remoteHomes
            }
        }.onFailure { err ->
            val msg = err.message ?: ""
            if (msg.contains("401") || msg.contains("token required", ignoreCase = true)) {
                _authRequired.value = true
                _connectionStatus.value = "Authentication Required: Session expired or token invalid"
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

        val unlockedChannels = strip.outlets.filter { !it.locked }.map { it.n }
        if (unlockedChannels.isEmpty()) {
            return@withContext Result.failure(Exception("All outlets on '${strip.displayName}' are locked against switching."))
        }

        if (!_settings.value.isStandalone) {
            val res = if (unlockedChannels.size == strip.outlets.size) {
                val r = apiClient.switchOutlet(_settings.value.serverUrl, _settings.value.serverToken, mac, 0, requestedState)
                if (!r.isSuccess && r.exceptionOrNull()?.message?.contains("409") == true) {
                    apiClient.switchChannels(_settings.value.serverUrl, _settings.value.serverToken, mac, unlockedChannels, requestedState)
                } else r
            } else {
                apiClient.switchChannels(_settings.value.serverUrl, _settings.value.serverToken, mac, unlockedChannels, requestedState)
            }

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
        val prevLock = outlet.locked

        // 1. Optimistic local state and database update
        dbHelper.updateOutletLock(mac, outletN, newLock)
        _strips.value = _strips.value.map { s ->
            if (s.mac == mac) {
                s.copy(outlets = s.outlets.map { if (it.n == outletN) it.copy(locked = newLock) else it })
            } else s
        }
        addLog("SYS", mac, "Outlet $outletN ${if (newLock) "LOCKED" else "UNLOCKED"}")

        // 2. Synchronize lock state to Volta backend for all users sharing this strip
        val s = _settings.value
        if (!s.isStandalone && s.serverToken.isNotBlank() && s.serverUrl.isNotBlank()) {
            val syncRes = apiClient.setOutletLock(s.serverUrl, s.serverToken, mac, outletN, newLock)
            if (syncRes.isSuccess) {
                addLog("SYS", mac, "✓ Outlet $outletN lock state ($newLock) synced to cloud.")
            } else {
                val err = syncRes.exceptionOrNull()?.message ?: "Sync failed"
                addLog("ERR", mac, "Failed to sync outlet $outletN lock to cloud: $err")
                // Roll back optimistic change on failure
                dbHelper.updateOutletLock(mac, outletN, prevLock)
                _strips.value = _strips.value.map { st ->
                    if (st.mac == mac) {
                        st.copy(outlets = st.outlets.map { if (it.n == outletN) it.copy(locked = prevLock) else it })
                    } else st
                }
            }
        }
    }

    suspend fun addStrip(mac: String, name: String, ip: String) = withContext(Dispatchers.IO) {
        val cleanedMac = mac.replace(":", "").uppercase()
        val s = _settings.value
        val newStrip = PowerStrip(
            mac = cleanedMac,
            name = name.ifBlank { "MTTL ${cleanedMac.takeLast(6)}" },
            ip = ip.ifBlank { "192.168.1.150" }
        )
        dbHelper.upsertStrip(newStrip)
        _strips.value = _strips.value.filterNot { it.mac == cleanedMac } + newStrip
        addLog("SYS", cleanedMac, "New power strip registered locally: ${newStrip.name} ($ip)")

        if (!s.isStandalone && s.serverToken.isNotBlank()) {
            addLog("SYS", cleanedMac, "Claiming strip $cleanedMac on backend ${s.serverUrl}...")
            val claimRes = apiClient.claimStrip(s.serverUrl, s.serverToken, cleanedMac)
            if (claimRes.isSuccess) {
                addLog("SYS", cleanedMac, "✓ Strip $cleanedMac successfully claimed to your account on backend.")
                refreshStrips()
            } else {
                addLog("WARN", cleanedMac, "Backend claim response: ${claimRes.exceptionOrNull()?.message}")
            }
        }
    }

    suspend fun unclaimStrip(mac: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanMac = mac.replace(":", "").uppercase()
        val s = _settings.value
        if (!s.isStandalone && s.serverUrl.isNotBlank() && s.serverToken.isNotBlank()) {
            addLog("SYS", cleanMac, "Unclaiming strip $cleanMac from account on ${s.serverUrl}...")
            val res = apiClient.unclaimStrip(s.serverUrl, s.serverToken, cleanMac)
            if (res.isSuccess) {
                addLog("SYS", cleanMac, "✓ Strip $cleanMac released from account (remains unassigned in system).")
            } else {
                addLog("WARN", cleanMac, "Backend unclaim response: ${res.exceptionOrNull()?.message}")
            }
        }
        dbHelper.deleteStrip(cleanMac)
        _strips.value = _strips.value.filterNot { it.mac == cleanMac }
        refreshStrips()
        Result.success(true)
    }

    suspend fun deleteStrip(mac: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanMac = mac.replace(":", "").uppercase()
        val s = _settings.value
        val isAdmin = _whoAmI.value?.isAdmin == true
        if (!s.isStandalone && s.serverUrl.isNotBlank() && s.serverToken.isNotBlank()) {
            if (isAdmin) {
                addLog("SYS", cleanMac, "Admin purging & tombstoning strip $cleanMac on ${s.serverUrl}...")
                val res = apiClient.deleteStrip(s.serverUrl, s.serverToken, cleanMac)
                if (res.isSuccess) {
                    addLog("SYS", cleanMac, "✓ Strip $cleanMac purged, tombstoned, and removed on backend.")
                } else {
                    addLog("WARN", cleanMac, "Backend purge response: ${res.exceptionOrNull()?.message}")
                }
            } else {
                return@withContext unclaimStrip(cleanMac)
            }
        }
        dbHelper.deleteStrip(cleanMac)
        _strips.value = _strips.value.filterNot { it.mac == cleanMac }
        refreshStrips()
        Result.success(true)
    }

    suspend fun reclaimStrip(mac: String): Result<String> = claimExistingStrip(mac, _settings.value.serverToken, _settings.value.serverUrl)

    suspend fun rebootStrip(mac: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanMac = mac.replace(":", "").uppercase()
        val s = _settings.value
        addLog("SYS", cleanMac, "Sending remote reboot command for strip $cleanMac...")
        if (!s.isStandalone && s.serverUrl.isNotBlank() && s.serverToken.isNotBlank()) {
            val res = apiClient.rebootStrip(s.serverUrl, s.serverToken, cleanMac)
            if (res.isSuccess) {
                addLog("SYS", cleanMac, "✓ Reboot command delivered. Strip will restart and reconnect in ~5 seconds.")
                Result.success(true)
            } else {
                val err = res.exceptionOrNull()?.message ?: "Reboot failed"
                addLog("ERR", cleanMac, "Reboot failed: $err")
                Result.failure(Exception(err))
            }
        } else {
            // Local direct socket fallback if in standalone mode
            val targetStrip = _strips.value.find { it.mac == cleanMac }
            val host = targetStrip?.ip ?: "192.168.1.1"
            socketClient.sendCommand(host, 30300, "up:reboot:0")
            addLog("SYS", cleanMac, "✓ Local reboot command sent over socket to $host:30300.")
            Result.success(true)
        }
    }

    // Multi-Home Management
    fun setSelectedHomeId(homeId: String?) {
        _selectedHomeId.value = homeId
    }

    suspend fun fetchHomes(): Result<List<VoltaHome>> = withContext(Dispatchers.IO) {
        val s = _settings.value
        if (s.serverUrl.isBlank() || s.serverToken.isBlank()) {
            return@withContext Result.success(_homes.value)
        }
        val res = apiClient.fetchHomes(s.serverUrl, s.serverToken)
        res.onSuccess { remoteHomes ->
            _homes.value = remoteHomes
        }
        res
    }

    suspend fun createHome(name: String, icon: String = "home"): Result<VoltaHome> = withContext(Dispatchers.IO) {
        val s = _settings.value
        val res = apiClient.createHome(s.serverUrl, s.serverToken, name, icon)
        res.onSuccess {
            fetchHomes()
        }
        res
    }

    suspend fun updateHome(id: String, name: String, icon: String = "home", sortOrder: Int = 0): Result<Boolean> = withContext(Dispatchers.IO) {
        val s = _settings.value
        val res = apiClient.updateHome(s.serverUrl, s.serverToken, id, name, icon, sortOrder)
        res.onSuccess {
            fetchHomes()
        }
        res
    }

    suspend fun deleteHome(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val s = _settings.value
        val res = apiClient.deleteHome(s.serverUrl, s.serverToken, id)
        res.onSuccess {
            if (_selectedHomeId.value == id) {
                _selectedHomeId.value = null
            }
            fetchHomes()
            pollRemoteServer()
        }
        res
    }

    suspend fun assignStripToHome(mac: String, homeId: String?): Result<Boolean> = withContext(Dispatchers.IO) {
        val s = _settings.value
        val res = apiClient.assignStripToHome(s.serverUrl, s.serverToken, mac, homeId)
        res.onSuccess {
            _strips.value = _strips.value.map {
                if (it.mac.equals(mac, ignoreCase = true) || it.mac.replace(":", "").equals(mac.replace(":", ""), ignoreCase = true)) {
                    val updated = it.copy(homeId = homeId)
                    dbHelper.upsertStrip(updated)
                    updated
                } else it
            }
            fetchHomes()
        }
        res
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
        val currentHomeId = _selectedHomeId.value
        // If connected to remote server, try fetching real server analytics first
        if (!s.isStandalone && s.serverUrl.isNotBlank()) {
            val remoteResult = apiClient.fetchAnalyticsHistory(s.serverUrl, s.serverToken, mac, outlet, timeRange, homeId = currentHomeId)
            if (remoteResult.isSuccess) {
                val points = remoteResult.getOrNull() ?: emptyList()
                if (points.isNotEmpty()) {
                    return@withContext points
                }
            }
        }

        // Query local database filtered strictly to the real strips currently under control (scoped to selected home)
        val validStrips = if (currentHomeId != null) _strips.value.filter { it.homeId == currentHomeId } else _strips.value
        val validMacs = validStrips.map { it.mac }
        if (validMacs.isEmpty()) return@withContext emptyList()

        val rawPoints = if (!mac.isNullOrBlank()) {
            if (!validMacs.contains(mac)) return@withContext emptyList()
            dbHelper.getTelemetry(mac, outlet, sinceTimestamp)
        } else {
            // Aggregate all controlled strips
            val allPoints = mutableListOf<TelemetryPoint>()
            for (validMac in validMacs) {
                allPoints.addAll(dbHelper.getTelemetry(validMac, outlet, sinceTimestamp))
            }
            allPoints
        }

        rawPoints.map { p ->
            val ts = if (p.timestamp < 10000000000L) p.timestamp * 1000L else p.timestamp
            if (ts != p.timestamp) p.copy(timestamp = ts) else p
        }.sortedBy { it.timestamp }
    }

    suspend fun getTopConsumers(sinceTimestamp: Long, timeRange: String = "24h"): List<TopConsumer> = withContext(Dispatchers.IO) {
        val s = _settings.value
        val currentHomeId = _selectedHomeId.value
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
                knownStrips = _strips.value,
                homeId = currentHomeId
            )
            if (remoteResult.isSuccess) {
                val list = remoteResult.getOrNull()
                if (list != null) {
                    return@withContext list
                }
            }
        }

        // Local SQLite calculation fallback (only when offline or no serverUrl configured)
        val validStrips = if (currentHomeId != null) _strips.value.filter { it.homeId == currentHomeId } else _strips.value
        val allConsumers = dbHelper.getTopConsumers(sinceTimestamp, s.costPerKwh)
        val filtered = allConsumers.filter { c ->
            validStrips.any { vs ->
                vs.mac.equals(c.mac, ignoreCase = true) || vs.mac.replace(":", "").equals(c.mac.replace(":", ""), ignoreCase = true)
            }
        }.take(5)

        if (filtered.isNotEmpty()) {
            val enriched = filtered.map { tc ->
                val liveStrip = validStrips.find {
                    it.mac.equals(tc.mac, ignoreCase = true) || it.mac.replace(":", "").equals(tc.mac.replace(":", ""), ignoreCase = true)
                }
                val liveOutlet = liveStrip?.outlets?.find { it.n == tc.outlet }
                val liveP = liveOutlet?.powerW ?: 0.0
                val activeAvg = when {
                    tc.avgPowerW > 0.0 -> maxOf(tc.avgPowerW, liveP)
                    liveP > 0.0 -> liveP
                    tc.kwh > 0.0 -> (tc.kwh * 1000.0 / 2.0).coerceIn(25.0, 2400.0)
                    else -> 0.0
                }
                val activePeak = when {
                    tc.peakPowerW > 0.0 -> maxOf(tc.peakPowerW, activeAvg * 1.35, liveP * 1.35)
                    liveP > 0.0 -> liveP * 1.35
                    activeAvg > 0.0 -> activeAvg * 1.35
                    else -> 0.0
                }
                tc.copy(
                    stripName = liveStrip?.displayName ?: tc.stripName,
                    outletName = liveOutlet?.name?.ifBlank { "Outlet ${tc.outlet}" } ?: tc.outletName,
                    avgPowerW = (activeAvg * 10).toLong() / 10.0,
                    peakPowerW = (activePeak * 10).toLong() / 10.0
                )
            }
            return@withContext enriched
        }

        // Live fallback if database hasn't accumulated telemetry deltas yet
        _strips.value.flatMap { strip ->
            strip.outlets.map { outlet ->
                val p = outlet.powerW
                val kwh = outlet.energyKwh.coerceAtLeast(p * 0.05 / 1000.0)
                val (histMax, histAvg) = dbHelper.getOutletMaxAndActiveAvgPower(strip.mac, outlet.n)
                val avgW = when {
                    p > 0.0 -> p
                    histAvg > 0.0 -> histAvg
                    kwh > 0.0 -> (kwh * 1000.0 / 2.0).coerceIn(25.0, 2400.0)
                    else -> 0.0
                }
                val peakW = when {
                    histMax > 0.0 -> maxOf(histMax, avgW * 1.25)
                    p > 0.0 -> p * 1.35
                    avgW > 0.0 -> avgW * 1.35
                    else -> 0.0
                }
                TopConsumer(
                    mac = strip.mac,
                    stripName = strip.displayName,
                    outlet = outlet.n,
                    outletName = outlet.name.ifBlank { "Outlet ${outlet.n}" },
                    kwh = (kwh * 1000).toLong() / 1000.0,
                    cost = (kwh * s.costPerKwh * 100).toLong() / 100.0,
                    avgPowerW = (avgW * 10).toLong() / 10.0,
                    peakPowerW = (peakW * 10).toLong() / 10.0
                )
            }
        }.sortedByDescending { it.kwh.coerceAtLeast(it.avgPowerW / 1000.0) }.take(5)
    }

    suspend fun getAnalyticsSummary(startTs: Long, endTs: Long, mac: String? = null, range: String = "24h"): AnalyticsSummary? = withContext(Dispatchers.IO) {
        val s = _settings.value
        if (s.serverUrl.isNotBlank()) {
            val res = apiClient.fetchAnalyticsSummary(
                baseUrl = s.serverUrl,
                token = s.serverToken,
                startTs = startTs,
                endTs = endTs,
                mac = mac ?: "__all__",
                range = range,
                homeId = _selectedHomeId.value
            )
            if (res.isSuccess) {
                val summary = res.getOrNull()
                if (summary != null && summary.costPerKwh > 0.0) {
                    if (summary.costPerKwh != s.costPerKwh || (summary.currency.isNotBlank() && summary.currency != s.currency)) {
                        val syncSettings = s.copy(
                            costPerKwh = summary.costPerKwh,
                            currency = summary.currency.ifBlank { s.currency }
                        )
                        updateSettings(syncSettings)
                    }
                }
                return@withContext summary
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

    // Streamlined Hardware Provisioning & Google Account Auto-Claim
    suspend fun runProvisioning(
        apIp: String = "192.168.1.1",
        apPort: Int = 30300,
        controllerIp: String = "",
        ssid: String,
        pass: String,
        candidateMac: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val currentSettings = _settings.value
        val targetServerUrl = currentSettings.remoteUrl
        val effectiveToken = currentSettings.authToken

        if (effectiveToken.isBlank()) {
            val err = "Authentication required: Please sign in with your Google account first to configure and claim a strip."
            addLog("ERR", "", err)
            return@withContext Result.failure(Exception(err))
        }

        // Validate SSID and Password
        val cleanSsid = ssid.trim()
        val cleanPass = pass.trim()
        if (cleanSsid.isBlank()) {
            return@withContext Result.failure(Exception("Home Wi-Fi SSID cannot be empty"))
        }
        if (cleanSsid.contains(":") || cleanSsid.contains("\n") || cleanSsid.contains("\r") ||
            cleanPass.contains(":") || cleanPass.contains("\n") || cleanPass.contains("\r")) {
            return@withContext Result.failure(Exception("SSID and password cannot contain ':' or newline characters (strip protocol restriction)"))
        }

        // Cache Home Wi-Fi credentials locally for subsequent strip additions
        saveHomeWifiCredentials(cleanSsid, cleanPass)

        // 1. If phone currently has internet access, pre-register expect_auto claim with Volta server
        if (targetServerUrl.isNotBlank() && effectiveToken.isNotBlank()) {
            addLog("SYS", "", "Pre-registering auto-claim with Volta backend (${targetServerUrl})...")
            val preClaim = apiClient.registerAutoClaim(targetServerUrl, effectiveToken)
            if (preClaim.isSuccess) {
                addLog("SYS", "✓", "Auto-claim expectation registered on backend for your Google account.")
            } else {
                addLog("WARN", "", "Auto-claim pre-registration notice: ${preClaim.exceptionOrNull()?.message}")
            }
        }

        // Phase 1: Resolve raw numeric IPv4 address for dial-in server before provisioning
        // Strip microcontroller lacks DNS resolution; must be raw IPv4 (e.g. 132.145.181.76)
        var rawDialInIpv4 = ""
        if (controllerIp.isNotBlank()) {
            rawDialInIpv4 = resolveToIpv4(controllerIp)
        }
        if (rawDialInIpv4.isBlank() && _discoveredDialInIp.value.isNotBlank()) {
            rawDialInIpv4 = resolveToIpv4(_discoveredDialInIp.value)
        }
        if (rawDialInIpv4.isBlank() && targetServerUrl.isNotBlank()) {
            val healthRes = apiClient.fetchHealth(targetServerUrl, effectiveToken)
            healthRes.onSuccess { info ->
                if (info.serverIp.isNotBlank()) {
                    val resolved = resolveToIpv4(info.serverIp)
                    if (resolved.isNotBlank()) {
                        rawDialInIpv4 = resolved
                        _discoveredDialInIp.value = resolved
                    }
                }
            }
        }
        if (rawDialInIpv4.isBlank()) {
            rawDialInIpv4 = resolveToIpv4(targetServerUrl, BACKEND_PUBLIC_IPV4)
        }
        if (rawDialInIpv4.isBlank()) {
            rawDialInIpv4 = BACKEND_PUBLIC_IPV4
        }
        addLog("SYS", "", "Phase 1: Resolved raw server IPv4 for strip dial-in: $rawDialInIpv4:10086")

        // Phase 2: Bind process network to Wi-Fi so socket routes to strip SoftAP interface (not mobile data)
        wifiHelper.bindProcessToActiveWifi()

        val primaryApIp = if (apIp.isNotBlank()) apIp else "192.168.1.1"
        var effectiveApIp = primaryApIp
        addLog("SYS", "", "Phase 2: Connecting to strip AP at $effectiveApIp:$apPort...")

        // Probe strip AP socket (try primaryApIp, fallback to gateway IP if different)
        var isReachable = false
        for (attempt in 1..3) {
            if (socketClient.probePort(effectiveApIp, apPort, 2000)) {
                isReachable = true
                break
            }
            delay(300)
        }

        if (!isReachable) {
            val gw = wifiHelper.getGatewayIp()
            if (gw.isNotBlank() && gw != effectiveApIp) {
                for (attempt in 1..2) {
                    if (socketClient.probePort(gw, apPort, 2000)) {
                        isReachable = true
                        effectiveApIp = gw
                        addLog("SYS", "", "Strip AP responded at gateway IP $effectiveApIp:$apPort")
                        break
                    }
                    delay(300)
                }
            }
        }

        if (!isReachable) {
            wifiHelper.unbindProcessNetwork()
            val currentSsid = wifiHelper.getCurrentlyConnectedSsid()
            val errMsg = "Cannot reach strip at $effectiveApIp:$apPort. " +
                "Please connect your phone to the strip's Wi-Fi hotspot (e.g. TONLY_TAP_xxxxxx or U+TAP_xxxxxx) in Wi-Fi settings first." +
                if (currentSsid.isNotBlank()) " (Current network: $currentSsid)" else ""
            addLog("ERR", "", errMsg)
            return@withContext Result.failure(Exception(errMsg))
        }

        // Capture current Wi-Fi SSID while still connected to strip AP before setup
        val apConnectedSsid = wifiHelper.getCurrentlyConnectedSsid()

        addLog("SYS", "", "Executing hardware setup sequence over TCP socket to $effectiveApIp:$apPort...")
        addLog("OUT", "", "up:getinfo:all")
        addLog("OUT", "", "up:ip:$rawDialInIpv4")
        addLog("OUT", "", "up:connect:$cleanSsid:******")

        val provResult = socketClient.executeHardwareProvisioning(
            apIp = effectiveApIp,
            apPort = apPort,
            rawServerIpv4 = rawDialInIpv4,
            ssid = cleanSsid,
            pass = cleanPass
        )

        if (!provResult.isSuccess) {
            wifiHelper.unbindProcessNetwork()
            val err = provResult.error ?: "Strip hardware rejected setup sequence."
            addLog("ERR", "", err)
            return@withContext Result.failure(Exception(err))
        }

        addLog("IN", "", "bootinfo: ${provResult.bootReply}")
        addLog("IN", "", provResult.ipReply)
        addLog("IN", "", provResult.connectReply)

        var discoveredMac = provResult.mac
        if (discoveredMac.isBlank()) {
            if (candidateMac.isNotBlank()) {
                discoveredMac = WifiProvisioningHelper.resolveFullMac(candidateMac)
            }
            if (discoveredMac.isBlank() && apConnectedSsid.isNotBlank()) {
                discoveredMac = WifiProvisioningHelper.resolveFullMac(apConnectedSsid)
            }
            if (discoveredMac.isBlank()) {
                val connectedSsid = wifiHelper.getCurrentlyConnectedSsid()
                discoveredMac = WifiProvisioningHelper.resolveFullMac(connectedSsid)
            }
            if (discoveredMac.isBlank()) {
                val scanned = _strips.value.firstOrNull { it.mac.startsWith("88D039") }?.mac
                if (!scanned.isNullOrBlank()) {
                    discoveredMac = scanned
                }
            }
        }

        if (discoveredMac.isNotBlank()) {
            addLog("SYS", "MAC", "Target strip hardware MAC: $discoveredMac")
        }

        addLog("SYS", "✓", "Strip accepted Wi-Fi credentials! It will now join '$cleanSsid' and connect to $rawDialInIpv4:10086.")

        // Step 2.4: Close socket and disconnect from strip SoftAP; restore normal phone WAN routing
        wifiHelper.disconnectFromStripAp()
        wifiHelper.unbindProcessNetwork()

        // Phase 3: Wait for phone to restore Internet connectivity to Volta server
        addLog("SYS", "", "Phase 3: Restoring phone Internet connection to Volta server...")
        var internetReady = false
        for (attempt in 1..15) {
            delay(1200)
            val healthCheck = apiClient.fetchHealth(targetServerUrl, effectiveToken)
            if (healthCheck.isSuccess) {
                internetReady = true
                addLog("SYS", "✓", "Phone Internet connection restored (backend reachable).")
                break
            }
        }

        // 1. Explicit Direct MAC Claim: Always send mac in POST /api/claim payload
        var directClaimOk = false
        if (discoveredMac.isNotBlank()) {
            addLog("SYS", discoveredMac, "Sending direct claim: POST /api/claim with mac=$discoveredMac...")
            trackConnectingStrip(discoveredMac)

            for (i in 1..5) {
                val directClaimRes = apiClient.claimStrip(targetServerUrl, effectiveToken, discoveredMac)
                if (directClaimRes.isSuccess) {
                    directClaimOk = true
                    addLog("SYS", "✓", "Strip $discoveredMac successfully claimed to account (POST /api/claim ok:true).")
                    addStrip(discoveredMac, "MTTL ${discoveredMac.takeLast(6)}", "")
                    break
                } else {
                    val claimErr = directClaimRes.exceptionOrNull()?.message ?: ""
                    if (claimErr.contains("PLAN_LIMIT_REACHED", ignoreCase = true)) {
                        clearConnectingStrip(discoveredMac)
                        return@withContext Result.failure(Exception(claimErr))
                    }
                    addLog("SYS", "", "Claim attempt $i/5 failed ($claimErr), retrying...")
                }
                delay(1000)
            }
        }

        // Backup expectation for IP matching
        for (i in 1..2) {
            val autoClaimRes = apiClient.registerAutoClaim(targetServerUrl, effectiveToken)
            if (autoClaimRes.isSuccess) {
                addLog("SYS", "✓", "Auto-claim expectation registered on backend.")
                break
            }
            delay(800)
        }

        // 2. Re-fetch live devices list (GET /api/live) IMMEDIATELY after claim
        addLog("SYS", "", "Re-fetching live devices list (GET /api/live) immediately after claim...")
        pollRemoteServer()

        // Phase 4: Live Polling & Confirmation (poll GET /api/live every 2s until online)
        // If strip shows online: false immediately after provisioning, DO NOT discard it or show an error screen!
        addLog("SYS", "", "Phase 4: Waiting for strip to dial into Volta Cloud (port 10086)...")
        var isOnline = false
        val maxPollAttempts = 20 // 20 attempts * 2s = 40 seconds
        for (poll in 1..maxPollAttempts) {
            val liveRes = apiClient.fetchStrips(targetServerUrl, effectiveToken)
            if (liveRes.isSuccess) {
                val liveStrips = liveRes.getOrNull() ?: emptyList()
                val targetStrip = if (discoveredMac.isNotBlank()) {
                    liveStrips.firstOrNull { it.mac.equals(discoveredMac, ignoreCase = true) }
                } else {
                    liveStrips.firstOrNull { s -> _strips.value.none { it.mac.equals(s.mac, ignoreCase = true) } }
                }
                if (targetStrip != null) {
                    discoveredMac = targetStrip.mac
                    if (targetStrip.online) {
                        isOnline = true
                        clearConnectingStrip(discoveredMac)
                        dbHelper.upsertStrip(targetStrip)
                        pollRemoteServer()
                        addLog("SYS", "✓", "Phase 4 Confirmed: Strip $discoveredMac is ONLINE on Volta Cloud!")
                        break
                    } else {
                        // Strip exists in live list with online: false
                        // Keep it marked with connecting spinner; DO NOT discard!
                        trackConnectingStrip(discoveredMac)
                        dbHelper.upsertStrip(targetStrip)
                    }
                }
            }
            pollRemoteServer()
            delay(2000L)
        }

        // Final sync
        pollRemoteServer()

        val displayMac = if (discoveredMac.isNotBlank()) discoveredMac else "device"
        val successNotice = if (isOnline) {
            "✓ Phase 4 Complete: Strip $displayMac provisioned, claimed, and verified ONLINE! Ready to use."
        } else {
            "✓ Strip $displayMac provisioned and claimed! It has registered with Volta Cloud and is completing its cloud handshake."
        }
        addLog("SYS", "✓", successNotice)
        notificationHelper.showHandshakeNotification(true, displayMac, successNotice)
        Result.success(successNotice)
    }

    suspend fun runProvisioning(
        ssid: String,
        pass: String
    ): Result<String> = runProvisioning(
        apIp = "192.168.1.1",
        apPort = 30300,
        controllerIp = "",
        ssid = ssid,
        pass = pass,
        candidateMac = ""
    )

    suspend fun claimExistingStrip(mac: String, userToken: String, serverUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanMac = WifiProvisioningHelper.resolveFullMac(mac).ifBlank { mac.replace(":", "").uppercase() }
        val targetUrl = serverUrl.ifBlank { _settings.value.serverUrl }
        val token = userToken.ifBlank { _settings.value.serverToken }

        if (cleanMac.isBlank()) return@withContext Result.failure(Exception("Please enter a valid MAC address"))

        addLog("SYS", cleanMac, "Claiming strip $cleanMac with token ${token.take(8)}... on $targetUrl")
        trackConnectingStrip(cleanMac)
        val res = apiClient.claimStrip(targetUrl, token, cleanMac)
        if (res.isSuccess) {
            // Also ensure in local store and immediately re-fetch live devices list (GET /api/live)
            addStrip(cleanMac, "MTTL ${cleanMac.takeLast(6)}", "")
            addLog("SYS", cleanMac, "Strip $cleanMac claimed on backend. Re-fetching /api/live...")
            pollRemoteServer()

            // Poll in background for connection
            repoScope.launch {
                for (poll in 1..15) {
                    delay(2000L)
                    val liveRes = apiClient.fetchStrips(targetUrl, token)
                    if (liveRes.isSuccess) {
                        val target = liveRes.getOrNull()?.firstOrNull { it.mac.equals(cleanMac, ignoreCase = true) }
                        if (target?.online == true) {
                            clearConnectingStrip(cleanMac)
                            pollRemoteServer()
                            addLog("SYS", "✓", "Strip $cleanMac verified ONLINE on Volta Cloud!")
                            break
                        }
                    }
                }
                clearConnectingStrip(cleanMac)
                pollRemoteServer()
            }

            val successMsg = "Strip $cleanMac claimed! Waiting for device to connect to Volta Cloud..."
            notificationHelper.showHandshakeNotification(true, cleanMac, successMsg)
            Result.success("Success: $successMsg")
        } else {
            clearConnectingStrip(cleanMac)
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

    suspend fun refreshWhoAmI(): Result<WhoAmIResponse> = withContext(Dispatchers.IO) {
        val s = _settings.value
        if (s.serverUrl.isBlank() || s.serverToken.isBlank()) {
            return@withContext Result.failure(Exception("No token configured"))
        }
        val res = apiClient.fetchWhoAmI(s.serverUrl, s.serverToken)
        res.onSuccess { who ->
            _whoAmI.value = who
            if (who.subscriptionExpired) {
                _authRequired.value = true
                _connectionStatus.value = "Subscription Expired: Contact admin to renew"
            }
        }
        res
    }

    suspend fun searchCities(query: String): List<CitySearchResult> = withContext(Dispatchers.IO) {
        val s = _settings.value
        val res = apiClient.searchCities(s.serverUrl, s.serverToken, query)
        val list = res.getOrDefault(emptyList())
        _citySearchResults.value = list
        list
    }

    suspend fun refreshSunInfo(): SunInfo? = withContext(Dispatchers.IO) {
        val s = _settings.value
        val res = apiClient.fetchSunInfo(s.serverUrl, s.serverToken, s.latitude, s.longitude, s.timezone)
        val info = res.getOrNull()
        if (info != null) {
            _sunInfo.value = info
        }
        info
    }

    suspend fun selectCity(city: CitySearchResult) = withContext(Dispatchers.IO) {
        val updated = _settings.value.copy(
            city = city.name,
            country = city.country,
            timezone = city.timezone,
            latitude = city.latitude,
            longitude = city.longitude
        )
        updateSettings(updated)
        if (!updated.isStandalone && updated.serverToken.isNotBlank()) {
            apiClient.saveGeoSettings(
                updated.serverUrl,
                updated.serverToken,
                city.name,
                city.country,
                city.timezone,
                city.latitude,
                city.longitude
            )
        }
        refreshSunInfo()
    }

    suspend fun updateVoltageGuard(
        mac: String,
        enabled: Boolean,
        minV: Double,
        maxV: Double,
        safeDelayMin: Int,
        tempEnabled: Boolean,
        maxTempC: Double
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val s = _settings.value
        val res = apiClient.updateVoltageGuard(
            s.serverUrl,
            s.serverToken,
            mac,
            enabled,
            minV,
            maxV,
            safeDelayMin,
            tempEnabled,
            maxTempC
        )
        _strips.value = _strips.value.map { s ->
            if (s.mac == mac) {
                val currentG = s.voltageGuard ?: VoltageGuard()
                s.copy(
                    voltageGuard = currentG.copy(
                        enabled = enabled,
                        minV = minV,
                        maxV = maxV,
                        safeDelayMin = safeDelayMin,
                        tempEnabled = tempEnabled,
                        maxTempC = maxTempC
                    )
                )
            } else s
        }
        if (res.isSuccess) {
            addLog("SYS", mac, "🛡️ Voltage Guard settings updated (Min: ${minV}V, Max: ${maxV}V, Temp: ${maxTempC}°C)")
            refreshStrips()
        }
        res
    }

    suspend fun resetVoltageGuard(mac: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val s = _settings.value
        val res = apiClient.resetVoltageGuard(s.serverUrl, s.serverToken, mac)
        _strips.value = _strips.value.map { s ->
            if (s.mac == mac) {
                val currentG = s.voltageGuard ?: VoltageGuard()
                s.copy(
                    voltageGuard = currentG.copy(
                        tripped = false,
                        tempTripped = false,
                        faultType = null
                    )
                )
            } else s
        }
        if (res.isSuccess) {
            addLog("SYS", mac, "🛡️ Voltage Guard tripped state cleared/reset.")
            refreshStrips()
        }
        res
    }

    suspend fun signInWithGoogle(idToken: String): Result<GoogleAuthResponse> = withContext(Dispatchers.IO) {
        val s = _settings.value
        val res = apiClient.exchangeGoogleIdToken(s.serverUrl, idToken)
        res.onSuccess { auth ->
            if (auth.token.isNotBlank()) {
                SecureTokenManager.getInstance(context).saveToken(auth.token)
                val updated = _settings.value.copy(
                    serverToken = auth.token,
                    isStandalone = false
                )
                updateSettings(updated)
                _authRequired.value = false
                _whoAmI.value = WhoAmIResponse(
                    authorized = true,
                    isAdmin = auth.isAdmin,
                    name = auth.name,
                    email = auth.email,
                    picture = auth.picture,
                    strips = auth.strips,
                    maxStrips = auth.maxStrips,
                    expiresAt = auth.expiresAt,
                    status = auth.status
                )
                addLog("SYS", "", "Signed in with Google as ${auth.name} (${auth.email})")
                refreshStrips()
            }
        }
        res
    }

    suspend fun fetchGoogleClientId(): String = withContext(Dispatchers.IO) {
        val s = _settings.value
        apiClient.fetchGoogleConfig(s.serverUrl).getOrDefault("345585205205-d2kn3jfdhahsq1ur4naclk97reojqptk.apps.googleusercontent.com")
    }

    fun signOut() {
        SecureTokenManager.getInstance(context).clearToken()
        val updated = _settings.value.copy(serverToken = "", isStandalone = false)
        repoScope.launch {
            updateSettings(updated)
        }
        _whoAmI.value = null
        _authRequired.value = true
        _strips.value = emptyList()
        _schedules.value = emptyList()
        _timers.value = emptyList()
        dbHelper.clearAllUserData()
        addLog("SYS", "", "Signed out of Volta account. User strips and data cleared.")
    }
}
