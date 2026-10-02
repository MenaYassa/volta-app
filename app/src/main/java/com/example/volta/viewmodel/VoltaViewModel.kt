package com.example.volta.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.volta.model.PowerStrip
import com.example.volta.model.Schedule
import com.example.volta.model.TelemetryPoint
import com.example.volta.model.TopConsumer
import com.example.volta.model.VoltaSettings
import com.example.volta.network.DiscoveredStripAp
import com.example.volta.repository.VoltaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoltaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoltaRepository(application.applicationContext)

    val strips: StateFlow<List<PowerStrip>> = repository.strips
    val schedules: StateFlow<List<Schedule>> = repository.schedules
    val settings: StateFlow<VoltaSettings> = repository.settings
    val logs = repository.logs
    val isRefreshing = repository.isRefreshing
    val connectionStatus = repository.connectionStatus

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

    init {
        viewModelScope.launch {
            strips.collect { currentStrips ->
                if (_selectedAnalyticsMac.value == null && currentStrips.isNotEmpty()) {
                    _selectedAnalyticsMac.value = currentStrips.first().mac
                } else if (_selectedAnalyticsMac.value != null && currentStrips.none { it.mac == _selectedAnalyticsMac.value }) {
                    _selectedAnalyticsMac.value = currentStrips.firstOrNull()?.mac
                }
                loadAnalyticsData()
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

    // Analytics
    fun setTimeRange(range: String) {
        _selectedTimeRange.value = range
        loadAnalyticsData()
    }

    fun setSelectedAnalyticsMac(mac: String?) {
        _selectedAnalyticsMac.value = mac
        loadAnalyticsData()
    }

    fun setSelectedAnalyticsOutlet(outlet: Int) {
        _selectedAnalyticsOutlet.value = outlet
        loadAnalyticsData()
    }

    fun loadAnalyticsData() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val windowMs = when (_selectedTimeRange.value) {
                "1h" -> 3600 * 1000L
                "6h" -> 6 * 3600 * 1000L
                "7d" -> 7 * 24 * 3600 * 1000L
                else -> 24 * 3600 * 1000L
            }
            val since = now - windowMs
            val points = repository.getTelemetry(
                mac = _selectedAnalyticsMac.value,
                outlet = if (_selectedAnalyticsOutlet.value == 0) null else _selectedAnalyticsOutlet.value,
                sinceTimestamp = since,
                timeRange = _selectedTimeRange.value
            )
            _chartTelemetry.value = points
            _topConsumers.value = repository.getTopConsumers(since, _selectedTimeRange.value)
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
        pass: String
    ) {
        viewModelScope.launch {
            _isProvisioning.value = true
            _provisioningStatus.value = "Registering auto-claim and provisioning strip for '$ssid'..."
            val res = repository.runProvisioning(ssid, pass)
            _isProvisioning.value = false
            _provisioningStatus.value = res.getOrElse { "Error: ${it.message}" }
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
                _wifiConnectionStatus.value = "✓ Connected to ${ap.ssid}! Strip AP active at 192.168.1.1:30300. Enter server IP below and execute handshake."
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
}
