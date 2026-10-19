package com.example.volta.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.volta.model.AnalyticsSummary
import com.example.volta.model.OutletTimer
import com.example.volta.model.PowerStrip
import com.example.volta.model.Schedule
import com.example.volta.model.TelemetryPoint
import com.example.volta.model.TimerMode
import com.example.volta.model.TopConsumer
import com.example.volta.model.VoltaSettings
import com.example.volta.model.VoltaHome
import com.example.volta.network.DiscoveredStripAp
import com.example.volta.repository.VoltaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoltaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoltaRepository(application.applicationContext)

    val strips: StateFlow<List<PowerStrip>> = repository.strips
    val schedules: StateFlow<List<Schedule>> = repository.schedules
    val timers: StateFlow<List<OutletTimer>> = repository.timers
    val settings: StateFlow<VoltaSettings> = repository.settings
    val logs = repository.logs
    val isRefreshing = repository.isRefreshing
    val connectionStatus = repository.connectionStatus

    // Multi-Home state
    val homes: StateFlow<List<VoltaHome>> = repository.homes
    val selectedHomeId: StateFlow<String?> = repository.selectedHomeId

    fun setSelectedHomeId(homeId: String?) {
        repository.setSelectedHomeId(homeId)
        loadAnalyticsData()
    }

    fun refreshHomes() {
        viewModelScope.launch {
            repository.fetchHomes()
        }
    }

    fun createHome(name: String, icon: String = "home", onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.createHome(name, icon)
            if (res.isSuccess) {
                onComplete?.invoke(true, null)
            } else {
                onComplete?.invoke(false, res.exceptionOrNull()?.message ?: "Failed to create home")
            }
        }
    }

    fun updateHome(id: String, name: String, icon: String = "home", sortOrder: Int = 0, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.updateHome(id, name, icon, sortOrder)
            if (res.isSuccess) {
                onComplete?.invoke(true, null)
            } else {
                onComplete?.invoke(false, res.exceptionOrNull()?.message ?: "Failed to update home")
            }
        }
    }

    fun deleteHome(id: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.deleteHome(id)
            if (res.isSuccess) {
                onComplete?.invoke(true, null)
            } else {
                onComplete?.invoke(false, res.exceptionOrNull()?.message ?: "Failed to delete home")
            }
        }
    }

    fun assignStripToHome(mac: String, homeId: String?, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.assignStripToHome(mac, homeId)
            if (res.isSuccess) {
                onComplete?.invoke(true, null)
            } else {
                onComplete?.invoke(false, res.exceptionOrNull()?.message ?: "Failed to assign home")
            }
        }
    }

    // Analytics state
    private val _selectedTimeRange = MutableStateFlow("24h")
    val selectedTimeRange: StateFlow<String> = _selectedTimeRange.asStateFlow()

    private val _selectedAnalyticsMac = MutableStateFlow<String?>(null)
    val selectedAnalyticsMac: StateFlow<String?> = _selectedAnalyticsMac.asStateFlow()

    private val _selectedAnalyticsOutlet = MutableStateFlow(0) // 0 for all
    val selectedAnalyticsOutlet: StateFlow<Int> = _selectedAnalyticsOutlet.asStateFlow()

    private val _chartTelemetry = MutableStateFlow<List<TelemetryPoint>>(emptyList())
    val chartTelemetry: StateFlow<List<TelemetryPoint>> = _chartTelemetry.asStateFlow()

    private val _topConsumers = MutableStateFlow<List<TopConsumer>>(emptyList())
    val topConsumers: StateFlow<List<TopConsumer>> = _topConsumers.asStateFlow()

    private val _topConsumersTimeRange = MutableStateFlow("7d")
    val topConsumersTimeRange: StateFlow<String> = _topConsumersTimeRange.asStateFlow()

    private var summaryJob: Job? = null
    private val _analyticsSummary = MutableStateFlow<AnalyticsSummary?>(null)
    val analyticsSummary: StateFlow<AnalyticsSummary?> = _analyticsSummary.asStateFlow()

    // Provisioning & Claim state
    val discoveredDialInIp: StateFlow<String> = repository.discoveredDialInIp

    private val _provisioningStatus = MutableStateFlow<String?>(null)
    val provisioningStatus: StateFlow<String?> = _provisioningStatus.asStateFlow()

    private val _isProvisioning = MutableStateFlow(false)
    val isProvisioning: StateFlow<Boolean> = _isProvisioning.asStateFlow()

    private val _claimStatus = MutableStateFlow<String?>(null)
    val claimStatus: StateFlow<String?> = _claimStatus.asStateFlow()

    private val _isClaiming = MutableStateFlow(false)
    val isClaiming: StateFlow<Boolean> = _isClaiming.asStateFlow()

    // Wi-Fi Strip Discovery & Direct Connection state
    private val _discoveredStrips = MutableStateFlow<List<DiscoveredStripAp>>(emptyList())
    val discoveredStrips: StateFlow<List<DiscoveredStripAp>> = _discoveredStrips.asStateFlow()

    private val _isScanningStrips = MutableStateFlow(false)
    val isScanningStrips: StateFlow<Boolean> = _isScanningStrips.asStateFlow()

    private val _isConnectingWifi = MutableStateFlow(false)
    val isConnectingWifi: StateFlow<Boolean> = _isConnectingWifi.asStateFlow()

    private val _connectedStripAp = MutableStateFlow<DiscoveredStripAp?>(null)
    val connectedStripAp: StateFlow<DiscoveredStripAp?> = _connectedStripAp.asStateFlow()

    private val _wifiConnectionStatus = MutableStateFlow<String?>(null)
    val wifiConnectionStatus: StateFlow<String?> = _wifiConnectionStatus.asStateFlow()

    // Raw command state
    private val _rawCommandResponse = MutableStateFlow<String?>(null)
    val rawCommandResponse: StateFlow<String?> = _rawCommandResponse.asStateFlow()

    // Dark mode state
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // Auth status & active navigation tab
    val authRequired: StateFlow<Boolean> = repository.authRequired
    val adminForbiddenEvent = repository.adminForbiddenEvent
    val whoAmI = repository.whoAmI
    val sunInfo = repository.sunInfo
    val citySearchResults = repository.citySearchResults

    fun setAppForegrounded(foregrounded: Boolean) {
        repository.setAppForegrounded(foregrounded)
    }

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _shouldFocusTokenOnSettings = MutableStateFlow(false)
    val shouldFocusTokenOnSettings: StateFlow<Boolean> = _shouldFocusTokenOnSettings.asStateFlow()

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun navigateToTokenConfiguration() {
        _shouldFocusTokenOnSettings.value = true
        _selectedTab.value = 6 // Settings tab
    }

    fun onTokenFocused() {
        _shouldFocusTokenOnSettings.value = false
    }

    fun searchCities(query: String) {
        viewModelScope.launch {
            repository.searchCities(query)
        }
    }

    fun selectCity(city: com.example.volta.model.CitySearchResult) {
        viewModelScope.launch {
            repository.selectCity(city)
        }
    }

    fun refreshSunInfo() {
        viewModelScope.launch {
            repository.refreshSunInfo()
        }
    }

    fun refreshWhoAmI() {
        viewModelScope.launch {
            repository.refreshWhoAmI()
        }
    }

    fun signInWithGoogle(idToken: String, onResult: (Result<com.example.volta.model.GoogleAuthResponse>) -> Unit) {
        viewModelScope.launch {
            val res = repository.signInWithGoogle(idToken)
            onResult(res)
        }
    }

    fun fetchGoogleClientId(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val cid = repository.fetchGoogleClientId()
            onResult(cid)
        }
    }

    fun saveToken(token: String) {
        viewModelScope.launch {
            val current = settings.value
            val updated = current.copy(
                serverToken = token.trim(),
                isStandalone = false
            )
            repository.updateSettings(updated)
            repository.setAuthRequired(false)
            repository.refreshWhoAmI()
            repository.refreshStrips()
        }
    }

    fun signOut() {
        val current = settings.value
        val updated = current.copy(
            serverToken = "",
            isStandalone = false
        )
        viewModelScope.launch {
            repository.updateSettings(updated)
        }
        repository.signOut()
        _chartTelemetry.value = emptyList()
        _topConsumers.value = emptyList()
        _analyticsSummary.value = null
        _selectedAnalyticsMac.value = null
        _selectedAnalyticsOutlet.value = 0
    }

    fun setSelectedTimeRange(range: String) {
        setTimeRange(range)
    }

    fun updateVoltageGuard(
        mac: String,
        enabled: Boolean,
        minV: Double,
        maxV: Double,
        safeDelayMin: Int,
        tempEnabled: Boolean,
        maxTempC: Double,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.updateVoltageGuard(
                mac,
                enabled,
                minV,
                maxV,
                safeDelayMin,
                tempEnabled,
                maxTempC
            )
            onResult(res.isSuccess)
        }
    }

    fun resetVoltageGuard(mac: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.resetVoltageGuard(mac)
            onResult(res.isSuccess)
        }
    }

    init {
        viewModelScope.launch {
            loadAnalyticsData()
        }
        viewModelScope.launch {
            strips.collect { currentStrips ->
                val currentMac = _selectedAnalyticsMac.value
                if (currentMac != null && currentStrips.isNotEmpty() && currentStrips.none { it.mac == currentMac }) {
                    _selectedAnalyticsMac.value = null
                    loadAnalyticsData()
                }
            }
        }
    }

    fun toggleDarkTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refreshStrips()
            loadAnalyticsData()
        }
    }

    fun toggleOutlet(mac: String, outletN: Int, state: Boolean) {
        viewModelScope.launch {
            repository.toggleOutlet(mac, outletN, state)
            loadAnalyticsData()
        }
    }

    fun toggleMaster(mac: String, state: Boolean) {
        viewModelScope.launch {
            repository.toggleMaster(mac, state)
            loadAnalyticsData()
        }
    }

    fun renameStrip(mac: String, newName: String) {
        viewModelScope.launch {
            repository.renameStrip(mac, newName)
        }
    }

    fun renameOutlet(mac: String, outletN: Int, newName: String) {
        viewModelScope.launch {
            repository.renameOutlet(mac, outletN, newName)
        }
    }

    fun toggleOutletLock(mac: String, outletN: Int) {
        viewModelScope.launch {
            repository.toggleOutletLock(mac, outletN)
        }
    }

    fun addStrip(mac: String, name: String, ip: String) {
        viewModelScope.launch {
            repository.addStrip(mac, name, ip)
            loadAnalyticsData()
        }
    }

    fun deleteStrip(mac: String) {
        viewModelScope.launch {
            repository.deleteStrip(mac)
            if (_selectedAnalyticsMac.value == mac) {
                _selectedAnalyticsMac.value = strips.value.firstOrNull()?.mac
            }
            loadAnalyticsData()
        }
    }

    fun rebootStrip(mac: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.rebootStrip(mac)
            if (res.isSuccess) {
                onComplete?.invoke(true, null)
            } else {
                onComplete?.invoke(false, res.exceptionOrNull()?.message)
            }
        }
    }

    // Schedules
    fun addSchedule(schedule: Schedule) {
        viewModelScope.launch {
            repository.addSchedule(schedule)
        }
    }

    fun toggleSchedule(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleSchedule(id, enabled)
        }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            repository.deleteSchedule(id)
        }
    }

    // Timers
    fun addTimer(timer: OutletTimer) {
        viewModelScope.launch {
            repository.addTimer(timer)
        }
    }

    fun toggleTimerPause(timerId: String) {
        viewModelScope.launch {
            repository.toggleTimerPause(timerId)
        }
    }

    fun resetTimer(timerId: String) {
        viewModelScope.launch {
            repository.resetTimer(timerId)
        }
    }

    fun deleteTimer(timerId: String) {
        viewModelScope.launch {
            repository.deleteTimer(timerId)
        }
    }

    // Analytics
    fun setTimeRange(range: String) {
        _selectedTimeRange.value = range
        _topConsumersTimeRange.value = range
        loadAnalyticsData()
    }

    fun setSelectedAnalyticsMac(mac: String?) {
        _selectedAnalyticsMac.value = mac
        if (mac == null) {
            _selectedAnalyticsOutlet.value = 0
        }
        loadAnalyticsData()
    }

    fun setSelectedAnalyticsOutlet(outlet: Int) {
        if (_selectedAnalyticsMac.value == null) {
            _selectedAnalyticsOutlet.value = 0
            loadAnalyticsData()
            return
        }
        _selectedAnalyticsOutlet.value = outlet
        loadAnalyticsData()
    }

    fun setTopConsumersTimeRange(range: String) {
        _topConsumersTimeRange.value = range
        loadTopConsumersData()
    }

    fun loadTopConsumersData() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val windowMs = when (_topConsumersTimeRange.value) {
                "1h" -> 3600 * 1000L
                "6h" -> 6 * 3600 * 1000L
                "24h" -> 24 * 3600 * 1000L
                "7d" -> 7 * 24 * 3600 * 1000L
                "30d" -> 30L * 24 * 3600 * 1000L
                "6m" -> 180L * 24 * 3600 * 1000L
                "1y" -> 365L * 24 * 3600 * 1000L
                else -> 7 * 24 * 3600 * 1000L
            }
            val since = now - windowMs
            _topConsumers.value = repository.getTopConsumers(since, _topConsumersTimeRange.value)
        }
    }

    fun loadPeriodSummary(startTs: Long, endTs: Long, mac: String = "__all__", range: String = "24h") {
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            try {
                val summary = repository.getAnalyticsSummary(startTs, endTs, mac, range)
                // Replace state directly — NEVER accumulate or add to old values
                _analyticsSummary.value = summary
            } catch (_: CancellationException) {
                // Expected when user quickly toggles periods
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    fun loadAnalyticsData() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val windowMs = when (_selectedTimeRange.value) {
                "1h" -> 3600 * 1000L
                "6h" -> 6 * 3600 * 1000L
                "24h" -> 24 * 3600 * 1000L
                "7d" -> 7 * 24 * 3600 * 1000L
                "30d" -> 30L * 24 * 3600 * 1000L
                "6m" -> 180L * 24 * 3600 * 1000L
                "1y" -> 365L * 24 * 3600 * 1000L
                else -> 24 * 3600 * 1000L
            }
            val since = now - windowMs
            loadPeriodSummary(
                startTs = since / 1000L,
                endTs = now / 1000L,
                mac = _selectedAnalyticsMac.value ?: "__all__",
                range = _selectedTimeRange.value
            )
            val points = repository.getTelemetry(
                mac = _selectedAnalyticsMac.value,
                outlet = if (_selectedAnalyticsOutlet.value == 0) null else _selectedAnalyticsOutlet.value,
                sinceTimestamp = since,
                timeRange = _selectedTimeRange.value
            )
            _chartTelemetry.value = points
            loadTopConsumersData()
        }
    }

    fun clearTelemetry() {
        viewModelScope.launch {
            repository.clearTelemetryData()
            loadAnalyticsData()
        }
    }

    // Tools
    fun sendRawCommand(mac: String, cmd: String) {
        viewModelScope.launch {
            val resp = repository.sendRawCommand(mac, cmd)
            _rawCommandResponse.value = resp
        }
    }

    fun clearRawResponse() {
        _rawCommandResponse.value = null
    }

    fun clearLogs() {
        repository.clearLogs()
    }

    // Streamlined Zero-Touch Provisioning (Only SSID & Password needed!)
    fun runProvisioning(
        ssid: String,
        pass: String,
        candidateMac: String = ""
    ) {
        viewModelScope.launch {
            _isProvisioning.value = true
            _provisioningStatus.value = "Registering auto-claim and provisioning strip for '$ssid'..."
            val effectiveMac = candidateMac.ifBlank { _connectedStripAp.value?.macSuffix ?: "" }
            val res = repository.runProvisioning(
                apIp = "192.168.1.1",
                apPort = 30300,
                controllerIp = "",
                ssid = ssid,
                pass = pass,
                candidateMac = effectiveMac
            )
            _isProvisioning.value = false
            _provisioningStatus.value = res.getOrElse { "Error: ${it.message}" }
            repository.refreshStrips()
            loadAnalyticsData()
        }
    }

    fun runProvisioning(
        apIp: String,
        apPort: Int,
        controllerIp: String,
        ssid: String,
        pass: String
    ) {
        viewModelScope.launch {
            _isProvisioning.value = true
            _provisioningStatus.value = "Registering auto-claim and provisioning strip for '$ssid'..."
            val res = repository.runProvisioning(apIp, apPort, controllerIp, ssid, pass)
            _isProvisioning.value = false
            _provisioningStatus.value = res.getOrElse { "Error: ${it.message}" }
            loadAnalyticsData()
        }
    }

    // Claim Strip to Account
    fun claimExistingStrip(mac: String, userToken: String, serverUrl: String) {
        viewModelScope.launch {
            _isClaiming.value = true
            _claimStatus.value = "Sending claim request to $serverUrl for strip $mac..."
            val res = repository.claimExistingStrip(mac, userToken, serverUrl)
            _isClaiming.value = false
            _claimStatus.value = res.getOrElse { "Error: ${it.message}" }
            loadAnalyticsData()
        }
    }

    // Strip ordering
    fun reorderStrips(orderedMacs: List<String>) {
        repository.reorderStrips(orderedMacs)
    }

    // Settings
    fun updateSettings(newSettings: VoltaSettings) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
            loadAnalyticsData()
        }
    }

    fun sendTestNotification() {
        repository.sendTestNotification()
    }

    // Wi-Fi Discovery & Strip AP Connection
    fun scanForStrips() {
        viewModelScope.launch {
            _isScanningStrips.value = true
            _wifiConnectionStatus.value = "Scanning for TONLY_TAP / U+TAP networks nearby…"
            val outcome = repository.scanForStrips()
            _discoveredStrips.value = outcome.strips
            _isScanningStrips.value = false
            _wifiConnectionStatus.value = if (outcome.strips.isNotEmpty()) {
                "Found ${outcome.strips.size} strip AP(s). Tap 'Connect' to pair with LGU_<suffix> password."
            } else {
                outcome.infoMessage ?: "No TONLY_TAP Wi-Fi networks found in range."
            }
        }
    }

    fun connectToStrip(ap: DiscoveredStripAp) {
        viewModelScope.launch {
            _isConnectingWifi.value = true
            _wifiConnectionStatus.value = "Connecting to ${ap.ssid} using password ${ap.password}…"
            val res = repository.connectToStripAp(ap.ssid, ap.password)
            _isConnectingWifi.value = false
            if (res.isSuccess) {
                _connectedStripAp.value = ap
                _wifiConnectionStatus.value = "✓ Connected to ${ap.ssid}! Strip AP active at 192.168.1.1:30300. Ready to pair with Wi-Fi and Volta Cloud."
            } else {
                _wifiConnectionStatus.value = "Connection note: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun disconnectFromStripAp() {
        repository.disconnectFromStripAp()
        _connectedStripAp.value = null
        _wifiConnectionStatus.value = null
    }

    // Cached Home Wi-Fi Credentials for Streamlined Strip Claiming
    val savedHomeWifiSsid: StateFlow<String> = repository.savedHomeWifiSsid
    val savedHomeWifiPass: StateFlow<String> = repository.savedHomeWifiPass

    fun saveHomeWifiCredentials(ssid: String, pass: String) {
        repository.saveHomeWifiCredentials(ssid, pass)
    }

    fun clearHomeWifiCredentials() {
        repository.clearHomeWifiCredentials()
    }
}
