package com.example.volta.network

import com.example.volta.model.AnalyticsSummary
import com.example.volta.model.Outlet
import com.example.volta.model.OutletTimer
import com.example.volta.model.PowerStrip
import com.example.volta.model.TelemetryPoint
import com.example.volta.model.TimerMode
import com.example.volta.model.TopConsumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VoltaApiClient {

    var onSessionExpired: (() -> Unit)? = null
    var onAdminForbidden: (() -> Unit)? = null

    data class ServerHealthInfo(
        val ok: Boolean,
        val serverIp: String,
        val devicePort: Int = 10086,
        val controller: Boolean = true
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 401) {
                onSessionExpired?.invoke()
            } else if (response.code == 403) {
                onAdminForbidden?.invoke()
            }
            response
        }
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun addAuthHeaders(reqBuilder: Request.Builder, token: String) {
        if (token.isNotBlank()) {
            reqBuilder.addHeader("X-Token", token.trim())
            reqBuilder.addHeader("Authorization", "Bearer ${token.trim()}")
        }
    }

    suspend fun fetchHealth(baseUrl: String, token: String): Result<ServerHealthInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/health"
            val reqBuilder = Request.Builder().url(url)
            addAuthHeaders(reqBuilder, token)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val ok = json.optBoolean("ok", true)
                    val serverIp = json.optString("server_ip", "")
                    val devicePort = json.optInt("device_port", 10086)
                    val controller = json.optBoolean("controller", true)
                    Result.success(ServerHealthInfo(ok, serverIp, devicePort, controller))
                } else {
                    Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(baseUrl: String, token: String): Result<Boolean> = withContext(Dispatchers.IO) {
        fetchHealth(baseUrl, token).map { it.ok }
    }

    suspend fun fetchStrips(baseUrl: String, token: String): Result<List<PowerStrip>> = withContext(Dispatchers.IO) {
        try {
            // Check /api/live first (the multi-tenant scoped endpoint), fallback to /api/strips
            val urls = listOf(
                "${baseUrl.trimEnd('/')}/api/live",
                "${baseUrl.trimEnd('/')}/api/strips"
            )
            var lastError: Exception? = null

            for (url in urls) {
                try {
                    val reqBuilder = Request.Builder().url(url)
                    addAuthHeaders(reqBuilder, token)
                    client.newCall(reqBuilder.build()).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            lastError = Exception("HTTP ${resp.code}: ${resp.message}")
                            return@use
                        }
                        val body = resp.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val namesObj = json.optJSONObject("names")
                        val list = mutableListOf<PowerStrip>()
                        // Server may return {"strips": [...]} or {"devices": [...]}
                        val stripsArray = json.optJSONArray("strips")
                            ?: json.optJSONArray("devices")
                            ?: json.optJSONArray("data")
                            ?: JSONArray()

                        for (i in 0 until stripsArray.length()) {
                            val sObj = stripsArray.getJSONObject(i)
                            val mac = sObj.optString("mac")
                            if (mac.isBlank()) continue
                            val cleanMac = mac.replace(":", "").uppercase()
                            val lowerMac = cleanMac.lowercase()

                            val stripNameEntry = namesObj?.optJSONObject(cleanMac)
                                ?: namesObj?.optJSONObject(lowerMac)
                                ?: namesObj?.optJSONObject(mac)

                            val name = stripNameEntry?.optString("name")?.takeIf { it.isNotBlank() }
                                ?: sObj.optString("name").takeIf { it.isNotBlank() }
                                ?: "MTTL ${cleanMac.takeLast(6)}"

                            val model = sObj.optString("model", "MTTL-W01")
                            val fw = sObj.optString("fw", "1.0.0")
                            val ip = sObj.optString("ip", "192.168.1.1")
                            val online = sObj.optBoolean("online", false)
                            val voltageV = sObj.optDouble("voltage_v", 220.0)
                            val rssi = sObj.optInt("rssi", -65)
                            val lastSeen = (sObj.optDouble("last_seen", System.currentTimeMillis() / 1000.0) * 1000).toLong()

                            val outletsList = mutableListOf<Outlet>()
                            val outletsArray = sObj.optJSONArray("outlets") ?: JSONArray()
                            val outletNamesDict = stripNameEntry?.optJSONObject("outlets")

                            for (j in 0 until outletsArray.length()) {
                                val oObj = outletsArray.getJSONObject(j)
                                val n = oObj.optInt("n", j + 1)
                                val nStr = n.toString()

                                // Support nick (from /api/live), server names dictionary, or name
                                val oName = oObj.optString("nick").takeIf { it.isNotBlank() }
                                    ?: outletNamesDict?.optString(nStr)?.takeIf { it.isNotBlank() }
                                    ?: oObj.optString("name").takeIf { it.isNotBlank() && !it.equals("Outlet $n", ignoreCase = true) }
                                    ?: oObj.optString("name").takeIf { it.isNotBlank() }
                                    ?: "Outlet $n"

                                val on = oObj.optBoolean("on", false)
                                val powerW = oObj.optDouble("power_w", 0.0)
                                val energyKwh = oObj.optDouble("energy_kwh", 0.0)
                                val tempC = oObj.optInt("temp_c", 25)
                                val locked = oObj.optBoolean("locked", false)
                                outletsList.add(Outlet(n, oName, on, powerW, energyKwh, tempC, locked))
                            }
                            if (outletsList.isEmpty()) {
                                for (n in 1..4) {
                                    val defaultName = outletNamesDict?.optString(n.toString()) ?: "Outlet $n"
                                    outletsList.add(Outlet(n, defaultName))
                                }
                            }
                            val serverTotalPowerW = sObj.optDouble("total_power_w", sObj.optDouble("power_w", -1.0))
                            list.add(PowerStrip(mac, name, model, fw, ip, online, voltageV, rssi, lastSeen, outletsList, serverTotalPowerW = serverTotalPowerW))
                        }
                        return@withContext Result.success(list)
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            Result.failure(lastError ?: Exception("Failed to fetch strips"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun switchOutlet(
        baseUrl: String,
        token: String,
        mac: String,
        outlet: Int,
        on: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/switch"
            val payload = JSONObject().apply {
                put("mac", mac)
                put("outlet", outlet)
                put("on", on)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) Result.success(true)
                else Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun claimStrip(
        baseUrl: String,
        token: String,
        mac: String,
        targetToken: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/claim"
            val effectiveTarget = if (targetToken.isNotBlank()) targetToken.trim() else token.trim()
            val payload = JSONObject().apply {
                put("mac", mac.replace(":", "").uppercase())
                put("token", effectiveTarget)
                put("target_token", effectiveTarget)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Result.success("Strip $mac claimed successfully to account.")
                } else {
                    Result.failure(Exception("HTTP ${resp.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerAutoClaim(baseUrl: String, token: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || token.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("baseUrl and token are required"))
        }
        try {
            val url = "${baseUrl.trimEnd('/')}/api/claim"
            val jsonPayload = JSONObject().apply {
                put("expect_auto", true)
                put("token", token.trim())
                put("target_token", token.trim())
            }
            val body = jsonPayload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${token.trim()}")
                .addHeader("X-Token", token.trim())
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.body?.string()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun claimAuto(
        baseUrl: String,
        token: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val res = registerAutoClaim(baseUrl, token)
        if (res.isSuccess) {
            Result.success("Auto-claim expectation registered with backend.")
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Auto-claim failed"))
        }
    }

    suspend fun deleteStrip(
        baseUrl: String,
        token: String,
        mac: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanMac = mac.replace(":", "").uppercase()
            val url = "${baseUrl.trimEnd('/')}/api/strip?mac=$cleanMac"
            val reqBuilder = Request.Builder()
                .url(url)
                .delete()
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAnalyticsHistory(
        baseUrl: String,
        token: String,
        mac: String?,
        outlet: Int?,
        range: String
    ): Result<List<TelemetryPoint>> = withContext(Dispatchers.IO) {
        try {
            val cleanMac = mac ?: ""
            val cleanOutlet = if (outlet != null && outlet > 0) outlet.toString() else "all"
            val url = "${baseUrl.trimEnd('/')}/api/analytics?mac=$cleanMac&outlet=$cleanOutlet&range=$range"

            val reqBuilder = Request.Builder().url(url)
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val json = JSONObject(body)
                val pointsArray = json.optJSONArray("points")
                    ?: json.optJSONArray("readings")
                    ?: json.optJSONArray("data")
                    ?: JSONArray()

                val result = mutableListOf<TelemetryPoint>()
                for (i in 0 until pointsArray.length()) {
                    val p = pointsArray.getJSONObject(i)
                    val ts = when {
                        p.has("timestamp") -> p.getLong("timestamp")
                        p.has("ts") -> {
                            val rawTs = p.getLong("ts")
                            if (rawTs < 10000000000L) rawTs * 1000L else rawTs
                        }
                        else -> System.currentTimeMillis()
                    }
                    val pointMac = p.optString("mac", cleanMac)
                    val pointOutlet = p.optInt("outlet", outlet ?: 1)
                    val power = p.optDouble("power_w", p.optDouble("power", 0.0))
                    val volt = p.optDouble("voltage_v", p.optDouble("voltage", 220.0))
                    val temp = p.optInt("temp_c", p.optInt("temp", 25))
                    val energy = p.optDouble("energy_kwh", p.optDouble("energy", 0.0))

                    result.add(
                        TelemetryPoint(
                            id = i.toLong(),
                            timestamp = ts,
                            mac = pointMac,
                            outlet = pointOutlet,
                            powerW = (power * 10).toLong() / 10.0,
                            voltageV = (volt * 10).toLong() / 10.0,
                            tempC = temp,
                            energyKwh = (energy * 1000).toLong() / 1000.0
                        )
                    )
                }
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAnalyticsLeaderboard(
        baseUrl: String,
        token: String,
        startTs: Long,
        endTs: Long,
        range: String,
        costPerKwh: Double,
        knownStrips: List<PowerStrip> = emptyList()
    ): Result<List<TopConsumer>> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/analytics/leaderboard"
            val payload = JSONObject().apply {
                put("start_ts", startTs)
                put("end_ts", endTs)
                put("limit", 5)
                put("range", range)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val json = JSONObject(body)
                val itemsArray = json.optJSONArray("leaderboard")
                    ?: json.optJSONArray("consumers")
                    ?: json.optJSONArray("data")
                    ?: JSONArray()

                val result = mutableListOf<TopConsumer>()
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.getJSONObject(i)
                    val rawMac = item.optString("mac", "")
                    val outlet = item.optInt("outlet", 1)

                    val strip = knownStrips.find {
                        it.mac.equals(rawMac, ignoreCase = true) ||
                        it.mac.replace(":", "").equals(rawMac.replace(":", ""), ignoreCase = true)
                    }
                    val outletObj = strip?.outlets?.find { it.n == outlet }

                    val stripName = item.optString("strip_name").takeIf { it.isNotBlank() }
                        ?: strip?.displayName
                        ?: "MTTL ${rawMac.replace(":", "").takeLast(6).uppercase()}"

                    val outletName = item.optString("outlet_name").takeIf { it.isNotBlank() }
                        ?: outletObj?.name?.takeIf { it.isNotBlank() && !it.equals("Outlet $outlet", ignoreCase = true) }
                        ?: outletObj?.name
                        ?: "Outlet $outlet"

                    val kwh = when {
                        item.has("energy_kwh") -> item.getDouble("energy_kwh")
                        item.has("kwh") -> item.getDouble("kwh")
                        item.has("consumed_kwh") -> item.getDouble("consumed_kwh")
                        else -> 0.0
                    }
                    val cost = (kwh * costPerKwh * 100).toLong() / 100.0

                    result.add(
                        TopConsumer(
                            mac = rawMac,
                            stripName = stripName,
                            outlet = outlet,
                            outletName = outletName,
                            kwh = (kwh * 1000).toLong() / 1000.0,
                            cost = cost
                        )
                    )
                }
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAnalyticsLeaderboard(
        baseUrl: String,
        token: String,
        range: String,
        costPerKwh: Double
    ): Result<List<TopConsumer>> {
        val nowSec = System.currentTimeMillis() / 1000L
        val startSec = when (range) {
            "1h" -> nowSec - 3600L
            "6h" -> nowSec - 6 * 3600L
            "24h" -> nowSec - 24 * 3600L
            "7d" -> nowSec - 7 * 86400L
            "30d" -> nowSec - 30 * 86400L
            "6m" -> nowSec - 180 * 86400L
            "1y" -> nowSec - 365 * 86400L
            else -> nowSec - 7 * 86400L
        }
        return fetchAnalyticsLeaderboard(baseUrl, token, startSec, nowSec, range, costPerKwh)
    }

    suspend fun fetchAnalyticsSummary(
        baseUrl: String,
        token: String,
        startTs: Long,
        endTs: Long,
        mac: String = "__all__"
    ): Result<AnalyticsSummary> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/analytics/summary"
            val payload = JSONObject().apply {
                put("start_ts", startTs)
                put("end_ts", endTs)
                put("mac", if (mac.isBlank()) "__all__" else mac)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val json = JSONObject(body)
                val summaryObj = json.optJSONObject("summary")
                    ?: return@withContext Result.failure(Exception("Missing summary field in response"))

                val summary = AnalyticsSummary(
                    energyKwh = summaryObj.optDouble("energy_kwh", 0.0),
                    wattHours = summaryObj.optDouble("watt_hours", 0.0),
                    avgPowerW = summaryObj.optDouble("avg_power_w", 0.0),
                    peakPowerW = summaryObj.optDouble("peak_power_w", 0.0),
                    minVoltageV = if (summaryObj.has("min_voltage_v") && !summaryObj.isNull("min_voltage_v")) summaryObj.getDouble("min_voltage_v") else null,
                    maxVoltageV = if (summaryObj.has("max_voltage_v") && !summaryObj.isNull("max_voltage_v")) summaryObj.getDouble("max_voltage_v") else null,
                    avgVoltageV = if (summaryObj.has("avg_voltage_v") && !summaryObj.isNull("avg_voltage_v")) summaryObj.getDouble("avg_voltage_v") else null,
                    avgTempC = if (summaryObj.has("avg_temp_c") && !summaryObj.isNull("avg_temp_c")) summaryObj.getDouble("avg_temp_c") else null,
                    samples = summaryObj.optLong("samples", 0L),
                    stripsCount = summaryObj.optInt("strips_count", 1),
                    cost = summaryObj.optDouble("cost", 0.0),
                    currency = summaryObj.optString("currency", "EGP"),
                    costPerKwh = summaryObj.optDouble("cost_per_kwh", 2.18)
                )
                Result.success(summary)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendRawCommand(
        baseUrl: String,
        token: String,
        mac: String,
        command: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/tools/raw"
            val payload = JSONObject().apply {
                put("mac", mac)
                put("cmd", command)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Result.success(body)
                } else if (resp.code == 403) {
                    Result.failure(Exception("Admin access required (HTTP 403): This diagnostic tool is restricted to administrator tokens."))
                } else if (resp.code == 401) {
                    Result.failure(Exception("Authentication required (HTTP 401): Token required. Please configure your token in Settings."))
                } else {
                    Result.failure(Exception("HTTP ${resp.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameOutlet(
        baseUrl: String,
        token: String,
        mac: String,
        outletN: Int,
        newName: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/rename"
            val payload = JSONObject().apply {
                put("mac", mac)
                put("outlet", outletN)
                put("outlet_name", newName)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) Result.success(true)
                else Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameStrip(
        baseUrl: String,
        token: String,
        mac: String,
        newName: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/rename"
            val payload = JSONObject().apply {
                put("mac", mac)
                put("name", newName)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) Result.success(true)
                else Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTimers(baseUrl: String, token: String): Result<List<OutletTimer>> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/timers"
            val reqBuilder = Request.Builder().url(url)
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val json = JSONObject(body)
                val timersArray = json.optJSONArray("timers") ?: json.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<OutletTimer>()

                val now = System.currentTimeMillis()
                for (i in 0 until timersArray.length()) {
                    val tObj = timersArray.getJSONObject(i)
                    val id = tObj.optString("id", java.util.UUID.randomUUID().toString())
                    val mac = tObj.optString("mac")
                    val outlet = tObj.optInt("outlet", 0)
                    val on = tObj.optBoolean("on", false)
                    val modeStr = tObj.optString("mode", "countdown")
                    val mode = if (modeStr.equals("cyclic", ignoreCase = true)) TimerMode.CYCLIC else TimerMode.COUNTDOWN
                    val onSec = tObj.optLong("on_sec", 1800)
                    val offSec = tObj.optLong("off_sec", 1800)
                    val repeatStr = tObj.optString("repeat", "once")
                    val repeatCount = when (repeatStr) {
                        "once" -> 1
                        "forever" -> -1
                        else -> tObj.optInt("repeat_n", 1)
                    }
                    val label = tObj.optString("label", "")
                    val enabled = tObj.optBoolean("enabled", true)
                    val phase = tObj.optString("phase", "on")
                    val phaseStartedRaw = tObj.optLong("phase_started", now / 1000)
                    val phaseStartedMs = if (phaseStartedRaw < 10000000000L) phaseStartedRaw * 1000L else phaseStartedRaw
                    val cyclesDone = tObj.optInt("cycles_done", 0)

                    // Accurate countdown remaining time calculation based on server phase
                    val elapsedSec = ((now - phaseStartedMs) / 1000L).coerceAtLeast(0)
                    val totalSecInPhase = if (mode == TimerMode.COUNTDOWN) onSec else (if (phase == "on") onSec else offSec)
                    val remainingSec = (totalSecInPhase - elapsedSec).coerceAtLeast(0)

                    list.add(
                        OutletTimer(
                            id = id,
                            stripMac = mac,
                            outlet = outlet,
                            label = label,
                            mode = mode,
                            targetActionOn = on,
                            durationSeconds = onSec,
                            onDurationSeconds = onSec,
                            offDurationSeconds = offSec,
                            startPhaseOn = on,
                            repeatCount = repeatCount,
                            currentCycle = cyclesDone,
                            isRunning = enabled && remainingSec > 0,
                            isPaused = !enabled,
                            currentPhaseOn = phase == "on",
                            remainingSeconds = remainingSec,
                            totalSecondsInPhase = totalSecInPhase,
                            startedAt = phaseStartedMs,
                            lastTickAt = now,
                            phase = phase,
                            phaseStarted = phaseStartedMs
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createOrUpdateTimer(baseUrl: String, token: String, timer: OutletTimer): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/timers"
            val payload = JSONObject().apply {
                put("id", timer.id)
                put("mac", timer.stripMac)
                put("outlet", timer.outlet)
                put("on", timer.targetActionOn)
                put("mode", if (timer.mode == TimerMode.CYCLIC) "cyclic" else "countdown")
                put("on_sec", timer.onDurationSeconds)
                put("off_sec", timer.offDurationSeconds)
                put("repeat", timer.repeatType)
                put("repeat_n", if (timer.repeatCount > 0) timer.repeatCount else 1)
                put("label", timer.label)
                put("enabled", timer.isRunning && !timer.isPaused)
                put("phase_started", (timer.phaseStarted / 1000L))
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) Result.success(true)
                else Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTimer(baseUrl: String, token: String, timerId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/timers?id=$timerId"
            val reqBuilder = Request.Builder()
                .url(url)
                .delete()
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) Result.success(true)
                else Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
