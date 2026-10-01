package com.example.volta.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.volta.model.PowerStrip
import com.example.volta.model.Schedule
import com.example.volta.model.TelemetryPoint
import com.example.volta.model.TopConsumer
import com.example.volta.model.VoltaSettings
import com.example.volta.repository.VoltaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    // Provisioning state
    private val _provisioningStatus = MutableStateFlow<String?>(null)
    val provisioningStatus: StateFlow<String?> = _provisioningStatus.asStateFlow()

    private val _isProvisioning = MutableStateFlow(false)
    val isProvisioning: StateFlow<Boolean> = _isProvisioning.asStateFlow()

    // Raw command state
    private val _rawCommandResponse = MutableStateFlow<String?>(null)
    val rawCommandResponse: StateFlow<String?> = _rawCommandResponse.asStateFlow()

    // Dark mode state
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    init {
        viewModelScope.launch {
            loadAnalyticsData()
        }
        viewModelScope.launch {
            strips.collect { currentStrips ->
                if (_selectedAnalyticsMac.value == null && currentStrips.isNotEmpty()) {
                    _selectedAnalyticsMac.value = currentStrips.first().mac
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
        }
    }

    fun deleteStrip(mac: String) {
        viewModelScope.launch {
            repository.deleteStrip(mac)
            if (_selectedAnalyticsMac.value == mac) {
                _selectedAnalyticsMac.value = strips.value.firstOrNull()?.mac
                loadAnalyticsData()
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
                else -> 24 * 3600 * 1000L // 24h
            }
            val since = now - windowMs
            val points = repository.getTelemetry(
                mac = _selectedAnalyticsMac.value,
                outlet = if (_selectedAnalyticsOutlet.value == 0) null else _selectedAnalyticsOutlet.value,
                sinceTimestamp = since
            )
            _chartTelemetry.value = points
            _topConsumers.value = repository.getTopConsumers(since)
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

    // Provisioning
    fun runProvisioning(
        apIp: String,
        apPort: Int,
        controllerIp: String,
        ssid: String,
        pass: String
    ) {
        viewModelScope.launch {
            _isProvisioning.value = true
            _provisioningStatus.value = "Sending configuration to strip at $apIp:$apPort..."
            val res = repository.runProvisioning(apIp, apPort, controllerIp, ssid, pass)
            _isProvisioning.value = false
            _provisioningStatus.value = res.getOrElse { "Error: ${it.message}" }
        }
    }

    // Settings
    fun updateSettings(newSettings: VoltaSettings) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
            loadAnalyticsData()
        }
    }
}
