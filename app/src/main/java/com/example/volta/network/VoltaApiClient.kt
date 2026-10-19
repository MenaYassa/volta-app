package com.example.volta.network

import com.example.volta.model.AnalyticsSummary
import com.example.volta.model.CitySearchResult
import com.example.volta.model.Outlet
import com.example.volta.model.OutletTimer
import com.example.volta.model.PowerStrip
import com.example.volta.model.SunInfo
import com.example.volta.model.TelemetryPoint
import com.example.volta.model.TimerMode
import com.example.volta.model.TopConsumer
import com.example.volta.model.VoltageGuard
import com.example.volta.model.GoogleAuthResponse
import com.example.volta.model.WhoAmIResponse
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
            var req = chain.request()
            val host = req.url.host
            if (host.contains("192.168.1.100") || host.contains("132.145") || host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                val newUrl = req.url.newBuilder()
                    .scheme("https")
                    .host("volta.03092017.xyz")
                    .port(443)
                    .build()
                req = req.newBuilder().url(newUrl).build()
            }
            val response = chain.proceed(req)
            val hasAuthHeader = req.header("Authorization")?.isNotBlank() == true || req.header("X-Token")?.isNotBlank() == true
            val path = req.url.encodedPath
            val isAuthenticatedPath = path.contains("/api/auth/whoami") || path.contains("/api/live") || path.contains("/api/onoff")
            if (response.code == 401 && hasAuthHeader && isAuthenticatedPath) {
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
            val url = "${baseUrl.trimEnd('/')}/api/live"
            val reqBuilder = Request.Builder().url(url)
            addAuthHeaders(reqBuilder, token)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
                val json = JSONObject(body)
                val namesObj = json.optJSONObject("names")
                val rootLocksObj = json.optJSONObject("locks") ?: json.optJSONObject("outlet_locks")
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
                            val stripLocksObj = sObj.optJSONObject("locks") ?: sObj.optJSONObject("outlet_locks")
                            val stripLocksArr = sObj.optJSONArray("locks") ?: sObj.optJSONArray("locked_outlets")

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
                                val currentA = oObj.optDouble("current_a", 0.0)
                                val energyKwh = oObj.optDouble("energy_kwh", 0.0)
                                val tempC = oObj.optInt("temp_c", 25)

                                // Multi-user Lock parsing: inspect outlet object, strip-level container, and root-level container
                                var locked = when {
                                    oObj.has("locked") -> {
                                        val v = oObj.opt("locked")
                                        if (v is Boolean) v
                                        else if (v is Number) v.toInt() == 1
                                        else if (v is String) v.equals("true", true) || v == "1"
                                        else false
                                    }
                                    oObj.has("lock") -> {
                                        val v = oObj.opt("lock")
                                        if (v is Boolean) v
                                        else if (v is Number) v.toInt() == 1
                                        else if (v is String) v.equals("true", true) || v == "1"
                                        else false
                                    }
                                    oObj.has("is_locked") -> {
                                        val v = oObj.opt("is_locked")
                                        if (v is Boolean) v
                                        else if (v is Number) v.toInt() == 1
                                        else if (v is String) v.equals("true", true) || v == "1"
                                        else false
                                    }
                                    else -> false
                                }

                                if (!locked && stripLocksObj != null) {
                                    val v = stripLocksObj.opt(nStr) ?: stripLocksObj.opt(j.toString())
                                    if (v is Boolean) locked = v
                                    else if (v is Number) locked = v.toInt() == 1
                                    else if (v is String) locked = v.equals("true", true) || v == "1"
                                }
                                if (!locked && stripLocksArr != null) {
                                    for (k in 0 until stripLocksArr.length()) {
                                        if (stripLocksArr.optInt(k, -1) == n) {
                                            locked = true
                                            break
                                        }
                                    }
                                }
                                if (!locked && rootLocksObj != null) {
                                    val rLocks = rootLocksObj.optJSONObject(cleanMac)
                                        ?: rootLocksObj.optJSONObject(lowerMac)
                                        ?: rootLocksObj.optJSONObject(mac)
                                    if (rLocks != null) {
                                        val v = rLocks.opt(nStr)
                                        if (v is Boolean) locked = v
                                        else if (v is Number) locked = v.toInt() == 1
                                        else if (v is String) locked = v.equals("true", true) || v == "1"
                                    }
                                }

                                val overloadOk = oObj.optBoolean("overload_ok", true)
                                val overheatOk = oObj.optBoolean("overheat_ok", true)
                                val eventCode = oObj.optString("event_code", "00")
                                val eventDesc = oObj.optString("event_desc", if (eventCode == "00") "Normal / OK" else "Event $eventCode")
                                outletsList.add(
                                    Outlet(
                                        n = n,
                                        name = oName,
                                        on = on,
                                        powerW = powerW,
                                        currentA = currentA,
                                        energyKwh = energyKwh,
                                        tempC = tempC,
                                        locked = locked,
                                        overloadOk = overloadOk,
                                        overheatOk = overheatOk,
                                        eventCode = eventCode,
                                        eventDesc = eventDesc
                                    )
                                )
                            }
                            if (outletsList.isEmpty()) {
                                for (n in 1..4) {
                                    val defaultName = outletNamesDict?.optString(n.toString()) ?: "Outlet $n"
                                    outletsList.add(Outlet(n, defaultName))
                                }
                            }
                            val serverTotalPowerW = sObj.optDouble("total_power_w", sObj.optDouble("power_w", -1.0))
                            val serverCurrentA = sObj.optDouble("current_a", -1.0)
                            val guardObj = sObj.optJSONObject("voltage_guard")
                            val voltageGuard = if (guardObj != null) parseVoltageGuard(guardObj) else null
                            val lastSeenAgoStr = sObj.optString("last_seen_ago", "")
                            list.add(
                                PowerStrip(
                                    mac = mac,
                                    name = name,
                                    model = model,
                                    fw = fw,
                                    ip = ip,
                                    online = online,
                                    voltageV = voltageV,
                                    rssi = rssi,
                                    lastSeen = lastSeen,
                                    outlets = outletsList,
                                    serverTotalPowerW = serverTotalPowerW,
                                    serverCurrentA = serverCurrentA,
                                    voltageGuard = voltageGuard,
                                    lastSeenAgoServer = lastSeenAgoStr,
                                    homeId = sObj.optString("home_id").takeIf { it.isNotBlank() } ?: sObj.optString("homeId").takeIf { it.isNotBlank() }
                                )
                            )
                    }
                    return@withContext Result.success(list)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun parseVoltageGuard(guardObj: JSONObject): VoltageGuard {
        val savedOutletsArray = guardObj.optJSONArray("saved_outlets")
        val savedOutlets = mutableListOf<Int>()
        if (savedOutletsArray != null) {
            for (k in 0 until savedOutletsArray.length()) {
                savedOutlets.add(savedOutletsArray.optInt(k))
            }
        }
        return VoltageGuard(
            enabled = guardObj.optBoolean("enabled", true),
            minV = guardObj.optDouble("min_v", 195.0),
            maxV = guardObj.optDouble("max_v", 250.0),
            safeDelayMin = guardObj.optInt("safe_delay_min", 3),
            tripped = guardObj.optBoolean("tripped", false),
            faultType = if (guardObj.has("fault_type") && !guardObj.isNull("fault_type")) guardObj.optString("fault_type") else null,
            trippedAt = if (guardObj.has("tripped_at") && !guardObj.isNull("tripped_at")) guardObj.optLong("tripped_at") else null,
            tripVoltage = if (guardObj.has("trip_voltage") && !guardObj.isNull("trip_voltage")) guardObj.optDouble("trip_voltage") else null,
            savedOutlets = savedOutlets,
            safeSince = if (guardObj.has("safe_since") && !guardObj.isNull("safe_since")) guardObj.optLong("safe_since") else null,
            tempEnabled = guardObj.optBoolean("temp_enabled", true),
            maxTempC = guardObj.optDouble("max_temp_c", 65.0),
            tempTripped = guardObj.optBoolean("temp_tripped", false),
            tempFaultType = if (guardObj.has("temp_fault_type") && !guardObj.isNull("temp_fault_type")) guardObj.optString("temp_fault_type") else null,
            tripTempC = if (guardObj.has("trip_temp_c") && !guardObj.isNull("trip_temp_c")) guardObj.optDouble("trip_temp_c") else null
        )
    }

    suspend fun switchOutlet(
        baseUrl: String,
        token: String,
        mac: String,
        outlet: Int,
        on: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // First try modern /api/onoff with channels array if outlet > 0, fallback to /api/switch
            val cleanMac = mac.replace(":", "").uppercase()
            val onOffUrl = "${baseUrl.trimEnd('/')}/api/onoff"
            val onOffPayload = JSONObject().apply {
                put("mac", cleanMac)
                if (outlet > 0) {
                    put("channels", JSONArray().put(outlet))
                }
                put("on", on)
            }
            val reqBuilder1 = Request.Builder()
                .url(onOffUrl)
                .post(onOffPayload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder1, token)
            val resp1 = client.newCall(reqBuilder1.build()).execute()
            if (resp1.isSuccessful) {
                resp1.close()
                return@withContext Result.success(true)
            }
            val code1 = resp1.code
            val body1 = resp1.body?.string().orEmpty()
            resp1.close()
            if (code1 == 409) {
                return@withContext Result.failure(Exception("Outlet is locked against switching (409 Conflict)"))
            }

            // Fallback to /api/switch legacy endpoint
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
                else Result.failure(Exception(if (resp.code == 409) "Outlet is locked against switching" else "HTTP ${resp.code}: ${resp.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun switchChannels(
        baseUrl: String,
        token: String,
        mac: String,
        channels: List<Int>,
        on: Boolean
    ): Result<Boolean> = batchOnOff(baseUrl, token, mac, channels, on)

    suspend fun batchOnOff(
        baseUrl: String,
        token: String,
        mac: String,
        channels: List<Int>,
        on: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanMac = mac.replace(":", "").uppercase()
            val url = "${baseUrl.trimEnd('/')}/api/onoff"
            val channelsArr = JSONArray()
            channels.forEach { channelsArr.put(it) }
            val payload = JSONObject().apply {
                put("mac", cleanMac)
                put("channels", channelsArr)
                put("on", on)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success(true)
                } else if (resp.code == 409) {
                    Result.failure(Exception("One or more outlets are locked against switching (409 Conflict)"))
                } else {
                    // Fallback: iterate over channels with switchOutlet if 404
                    if (resp.code == 404) {
                        for (ch in channels) {
                            switchOutlet(baseUrl, token, cleanMac, ch, on)
                        }
                        Result.success(true)
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setOutletLock(
        baseUrl: String,
        token: String,
        mac: String,
        outlet: Int,
        locked: Boolean?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanMac = mac.replace(":", "").uppercase()
            val url = "${baseUrl.trimEnd('/')}/api/outlet/lock"
            val payload = JSONObject().apply {
                put("mac", cleanMac)
                put("outlet", outlet)
                put("n", outlet)
                put("channel", outlet)
                if (locked != null) {
                    put("locked", locked)
                    put("lock", locked)
                    put("is_locked", locked)
                }
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)
            val resp = client.newCall(reqBuilder.build()).execute()
            if (resp.isSuccessful) {
                resp.close()
                return@withContext Result.success(true)
            }
            val code = resp.code
            val body = resp.body?.string().orEmpty()
            resp.close()

            // Fallback to /api/lock if /api/outlet/lock returns 404 or 405
            if (code == 404 || code == 405) {
                val fallbackUrl = "${baseUrl.trimEnd('/')}/api/lock"
                val fbBuilder = Request.Builder()
                    .url(fallbackUrl)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                addAuthHeaders(fbBuilder, token)
                val fbResp = client.newCall(fbBuilder.build()).execute()
                if (fbResp.isSuccessful) {
                    fbResp.close()
                    return@withContext Result.success(true)
                }
                fbResp.close()
            }
            Result.failure(Exception(if (code == 409) "Outlet is locked against switching" else "HTTP $code: $body"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveStripOrder(
        baseUrl: String,
        token: String,
        order: List<String>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/strip/order"
            val orderArr = JSONArray()
            order.forEach { orderArr.put(it.replace(":", "").uppercase()) }
            val payload = JSONObject().apply {
                put("order", orderArr)
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

    suspend fun unclaimStrip(
        baseUrl: String,
        token: String,
        mac: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanMac = mac.replace(":", "").uppercase()
            val url = "${baseUrl.trimEnd('/')}/api/unclaim"
            val payload = JSONObject().apply {
                put("mac", cleanMac)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Result.success(true)
                } else {
                    val err = try {
                        JSONObject(body).optString("error", "HTTP ${resp.code}: $body")
                    } catch (_: Exception) {
                        "HTTP ${resp.code}: $body"
                    }
                    Result.failure(Exception(err))
                }
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
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    if (json != null && json.has("ok") && !json.optBoolean("ok", true)) {
                        val errText = json.optString("error", "Claim request rejected by server")
                        Result.failure(Exception(errText))
                    } else {
                        Result.success("Strip $mac claimed successfully to account.")
                    }
                } else {
                    val cleanError = try {
                        val json = JSONObject(body)
                        val errText = json.optString("error", "HTTP ${resp.code}: $body")
                        if (resp.code == 403) {
                            "PLAN_LIMIT_REACHED: $errText"
                        } else {
                            errText
                        }
                    } catch (_: Exception) {
                        if (resp.code == 403) {
                            "PLAN_LIMIT_REACHED: Plan limit reached. Please contact administrator to upgrade your subscription."
                        } else {
                            "HTTP ${resp.code}: $body"
                        }
                    }
                    Result.failure(Exception(cleanError))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rebootStrip(
        baseUrl: String,
        token: String,
        mac: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanMac = mac.replace(":", "").uppercase()
            val url = "${baseUrl.trimEnd('/')}/api/reboot"
            val payload = JSONObject().apply {
                put("mac", cleanMac)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Result.success(true)
                } else {
                    val err = try {
                        JSONObject(body).optString("error", "HTTP ${resp.code}: $body")
                    } catch (_: Exception) {
                        "HTTP ${resp.code}: $body"
                    }
                    Result.failure(Exception(err))
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

    suspend fun fetchHomes(
        baseUrl: String,
        token: String
    ): Result<List<com.example.volta.model.VoltaHome>> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/homes"
            val reqBuilder = Request.Builder().url(url).get()
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body?.string() ?: "[]"
                val homesList = mutableListOf<com.example.volta.model.VoltaHome>()
                val homesArray = try {
                    JSONArray(body)
                } catch (_: Exception) {
                    val json = JSONObject(body)
                    json.optJSONArray("homes") ?: json.optJSONArray("data") ?: JSONArray()
                }

                for (i in 0 until homesArray.length()) {
                    val hObj = homesArray.getJSONObject(i)
                    homesList.add(
                        com.example.volta.model.VoltaHome(
                            id = hObj.optString("id", java.util.UUID.randomUUID().toString()),
                            userId = hObj.optString("user_id", ""),
                            name = hObj.optString("name", "Home"),
                            icon = hObj.optString("icon", "home"),
                            isDefault = hObj.optBoolean("is_default", false),
                            sortOrder = hObj.optInt("sort_order", 0),
                            deviceCount = hObj.optInt("device_count", hObj.optInt("strip_count", 0))
                        )
                    )
                }
                Result.success(homesList)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createHome(
        baseUrl: String,
        token: String,
        name: String,
        icon: String = "home"
    ): Result<com.example.volta.model.VoltaHome> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/homes"
            val payload = JSONObject().apply {
                put("name", name)
                put("icon", icon)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body?.string() ?: "{}"
                val hObj = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                val home = com.example.volta.model.VoltaHome(
                    id = hObj.optString("id", java.util.UUID.randomUUID().toString()),
                    userId = hObj.optString("user_id", ""),
                    name = hObj.optString("name", name),
                    icon = hObj.optString("icon", icon),
                    isDefault = hObj.optBoolean("is_default", false),
                    sortOrder = hObj.optInt("sort_order", 0),
                    deviceCount = hObj.optInt("device_count", 0)
                )
                Result.success(home)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateHome(
        baseUrl: String,
        token: String,
        id: String,
        name: String,
        icon: String = "home",
        sortOrder: Int = 0
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/homes/update"
            val payload = JSONObject().apply {
                put("id", id)
                put("name", name)
                put("icon", icon)
                put("sort_order", sortOrder)
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

    suspend fun deleteHome(
        baseUrl: String,
        token: String,
        id: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/homes/delete"
            val payload = JSONObject().apply {
                put("id", id)
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

    suspend fun assignStripToHome(
        baseUrl: String,
        token: String,
        mac: String,
        homeId: String?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanMac = mac.replace(":", "").uppercase()
            val url = "${baseUrl.trimEnd('/')}/api/homes/assign"
            val payload = JSONObject().apply {
                put("mac", cleanMac)
                put("home_id", homeId ?: "")
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

    suspend fun fetchAnalyticsHistory(
        baseUrl: String,
        token: String,
        mac: String?,
        outlet: Int?,
        range: String,
        homeId: String? = null
    ): Result<List<TelemetryPoint>> = withContext(Dispatchers.IO) {
        try {
            val nowSec = System.currentTimeMillis() / 1000L
            val rangeSec = when (range) {
                "1h" -> 3600L
                "6h" -> 21600L
                "24h" -> 86400L
                "7d" -> 604800L
                "30d" -> 2592000L
                "6m" -> 15552000L
                "1y" -> 31536000L
                else -> 86400L
            }
            val startSec = nowSec - rangeSec
            val downsample = when (range) {
                "1h", "6h" -> null
                "24h" -> "minute"
                "7d", "30d" -> "hour"
                else -> "day"
            }

            val cleanMac = mac?.trim() ?: ""
            val isAllStrips = cleanMac.isBlank() || cleanMac.equals("__all__", ignoreCase = true)
            val isAllOutlets = outlet == null || outlet <= 0

            val url: String
            val payload = JSONObject().apply {
                put("start_ts", startSec)
                put("end_ts", nowSec)
                if (downsample != null) {
                    put("downsample", downsample)
                }
                if (!homeId.isNullOrBlank()) {
                    put("home_id", homeId)
                }
            }

            if (isAllStrips) {
                url = "${baseUrl.trimEnd('/')}/api/analytics/all-strips"
            } else if (isAllOutlets) {
                url = "${baseUrl.trimEnd('/')}/api/analytics/strip-outlets"
                payload.put("mac", cleanMac)
            } else {
                url = "${baseUrl.trimEnd('/')}/api/analytics/range"
                payload.put("mac", cleanMac)
                payload.put("outlet", outlet)
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
                val pointsArray = json.optJSONArray("readings")
                    ?: json.optJSONArray("points")
                    ?: json.optJSONArray("data")
                    ?: JSONArray()

                val result = mutableListOf<TelemetryPoint>()
                for (i in 0 until pointsArray.length()) {
                    val p = pointsArray.getJSONObject(i)
                    val ts = when {
                        p.has("timestamp") -> {
                            val raw = p.optLong("timestamp", 0L)
                            if (raw > 0L) {
                                if (raw < 10000000000L) raw * 1000L else raw
                            } else {
                                parseDateStringToMs(p.optString("timestamp", ""))
                            }
                        }
                        p.has("ts") -> {
                            val rawTs = p.optLong("ts", 0L)
                            if (rawTs > 0L) {
                                if (rawTs < 10000000000L) rawTs * 1000L else rawTs
                            } else {
                                parseDateStringToMs(p.optString("ts", ""))
                            }
                        }
                        p.has("time") -> {
                            val rawTime = p.optLong("time", 0L)
                            if (rawTime > 0L) {
                                if (rawTime < 10000000000L) rawTime * 1000L else rawTime
                            } else {
                                parseDateStringToMs(p.optString("time", ""))
                            }
                        }
                        else -> System.currentTimeMillis()
                    }
                    val pointMac = p.optString("mac", cleanMac)
                    val pointOutlet = p.optInt("outlet", outlet ?: 1)
                    val power = p.optDouble("power_w", p.optDouble("power", 0.0))
                    val volt = p.optDouble("voltage_v", p.optDouble("voltage", 220.0))
                    val temp = p.optInt("temp_c", p.optInt("temp", 25))
                    val energy = p.optDouble("energy_kwh", p.optDouble("energy_delta_kwh", p.optDouble("energy", 0.0)))

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

    private fun parseDateStringToMs(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
        )
        for (pattern in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                val d = sdf.parse(dateStr)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    suspend fun fetchAnalyticsLeaderboard(
        baseUrl: String,
        token: String,
        startTs: Long,
        endTs: Long,
        range: String,
        costPerKwh: Double,
        knownStrips: List<PowerStrip> = emptyList(),
        homeId: String? = null
    ): Result<List<TopConsumer>> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/analytics/leaderboard"
            val payload = JSONObject().apply {
                put("mac", "__all__")
                put("start_ts", startTs)
                put("end_ts", endTs)
                put("limit", 5)
                put("range", range)
                if (!homeId.isNullOrBlank()) {
                    put("home_id", homeId)
                }
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
                        item.has("energy_delta_kwh") -> item.getDouble("energy_delta_kwh")
                        item.has("kwh") -> item.getDouble("kwh")
                        item.has("consumed_kwh") -> item.getDouble("consumed_kwh")
                        else -> 0.0
                    }
                    val cost = (kwh * costPerKwh * 100).toLong() / 100.0
                    val pct = when {
                        item.has("pct") -> item.getDouble("pct")
                        item.has("percentage") -> item.getDouble("percentage")
                        else -> 0.0
                    }

                    val rawAvgW = when {
                        item.has("avg_power_w") -> item.getDouble("avg_power_w")
                        item.has("avg_power") -> item.getDouble("avg_power")
                        item.has("avg_w") -> item.getDouble("avg_w")
                        item.has("power_w") -> item.getDouble("power_w")
                        item.has("average_power") -> item.getDouble("average_power")
                        else -> 0.0
                    }
                    val rawPeakW = when {
                        item.has("peak_power_w") -> item.getDouble("peak_power_w")
                        item.has("peak_power") -> item.getDouble("peak_power")
                        item.has("peak_w") -> item.getDouble("peak_w")
                        item.has("max_power") -> item.getDouble("max_power")
                        item.has("max_w") -> item.getDouble("max_w")
                        else -> 0.0
                    }

                    val durationHours = ((endTs - startTs) / 3600.0).coerceAtLeast(0.5)
                    val derivedAvgW = if (kwh > 0.0) (kwh * 1000.0) / durationHours else 0.0
                    val currentLiveW = outletObj?.powerW ?: 0.0

                    val finalAvgW = when {
                        rawAvgW > 0.0 -> rawAvgW
                        derivedAvgW > 0.0 -> derivedAvgW
                        currentLiveW > 0.0 -> currentLiveW
                        else -> 0.0
                    }
                    val finalPeakW = when {
                        rawPeakW > 0.0 -> rawPeakW
                        currentLiveW > 0.0 -> maxOf(currentLiveW, finalAvgW * 1.35)
                        finalAvgW > 0.0 -> finalAvgW * 1.4
                        else -> 0.0
                    }

                    result.add(
                        TopConsumer(
                            mac = rawMac,
                            stripName = stripName,
                            outlet = outlet,
                            outletName = outletName,
                            kwh = (kwh * 1000).toLong() / 1000.0,
                            cost = cost,
                            pct = pct,
                            avgPowerW = (finalAvgW * 10).toLong() / 10.0,
                            peakPowerW = (finalPeakW * 10).toLong() / 10.0
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
        mac: String = "__all__",
        range: String = "24h",
        homeId: String? = null
    ): Result<AnalyticsSummary> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/analytics/summary"
            val payload = JSONObject().apply {
                put("start_ts", startTs)
                put("end_ts", endTs)
                put("mac", if (mac.isBlank()) "__all__" else mac)
                put("range", range)
                if (!homeId.isNullOrBlank()) {
                    put("home_id", homeId)
                }
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

                val totalKwh = summaryObj.optDouble("total_kwh", summaryObj.optDouble("energy_kwh", 0.0))
                val estCost = summaryObj.optDouble("estimated_cost", summaryObj.optDouble("cost", 0.0))
                val costPerKwh = json.optDouble("cost_per_kwh", summaryObj.optDouble("cost_per_kwh", 2.18))
                val currency = json.optString("currency", summaryObj.optString("currency", "EGP"))

                val summary = AnalyticsSummary(
                    energyKwh = totalKwh,
                    wattHours = summaryObj.optDouble("watt_hours", totalKwh * 1000.0),
                    avgPowerW = summaryObj.optDouble("avg_power_w", 0.0),
                    peakPowerW = summaryObj.optDouble("peak_power_w", 0.0),
                    minVoltageV = if (summaryObj.has("min_voltage_v") && !summaryObj.isNull("min_voltage_v")) summaryObj.getDouble("min_voltage_v") else null,
                    maxVoltageV = if (summaryObj.has("max_voltage_v") && !summaryObj.isNull("max_voltage_v")) summaryObj.getDouble("max_voltage_v") else null,
                    avgVoltageV = if (summaryObj.has("avg_voltage_v") && !summaryObj.isNull("avg_voltage_v")) summaryObj.getDouble("avg_voltage_v") else null,
                    avgTempC = if (summaryObj.has("avg_temp_c") && !summaryObj.isNull("avg_temp_c")) summaryObj.getDouble("avg_temp_c") else null,
                    samples = summaryObj.optLong("samples", 0L),
                    stripsCount = summaryObj.optInt("strips_count", 1),
                    cost = estCost,
                    currency = currency,
                    costPerKwh = costPerKwh
                )
                Result.success(summary)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWhoAmI(baseUrl: String, token: String): Result<WhoAmIResponse> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || token.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("baseUrl and token are required"))
        }
        try {
            val url = "${baseUrl.trimEnd('/')}/api/auth/whoami"
            val reqBuilder = Request.Builder().url(url)
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val json = if (body.isNotBlank()) JSONObject(body) else JSONObject()

                if (resp.code == 401) {
                    val isExpired = json.optBoolean("subscription_expired", false)
                    val status = json.optString("status", if (isExpired) "expired" else "unauthorized")
                    val err = json.optString("error", "Unauthorized")
                    val expiresAt = if (json.has("expires_at") && !json.isNull("expires_at")) json.getLong("expires_at") else null
                    return@withContext Result.success(
                        WhoAmIResponse(
                            authorized = false,
                            subscriptionExpired = isExpired,
                            status = status,
                            expiresAt = expiresAt,
                            error = err
                        )
                    )
                }

                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }

                val stripsArr = json.optJSONArray("strips")
                val stripsList = mutableListOf<String>()
                if (stripsArr != null) {
                    for (i in 0 until stripsArr.length()) {
                        stripsList.add(stripsArr.getString(i))
                    }
                }

                val who = WhoAmIResponse(
                    authorized = json.optBoolean("authorized", true),
                    isAdmin = json.optBoolean("is_admin", false),
                    name = json.optString("name", ""),
                    email = json.optString("email", ""),
                    picture = json.optString("picture", ""),
                    strips = stripsList,
                    maxStrips = json.optInt("max_strips", 10),
                    expiresAt = if (json.has("expires_at") && !json.isNull("expires_at")) json.getLong("expires_at") else null,
                    status = json.optString("status", "active"),
                    subscriptionExpired = json.optBoolean("subscription_expired", false)
                )
                Result.success(who)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchCities(baseUrl: String, token: String, query: String): Result<List<CitySearchResult>> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || query.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val encodedQ = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val url = "${baseUrl.trimEnd('/')}/api/geo/search?q=$encodedQ"
            val reqBuilder = Request.Builder().url(url)
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body?.string() ?: return@withContext Result.success(emptyList())
                val json = JSONObject(body)
                val resultsArr = json.optJSONArray("results") ?: JSONArray()
                val list = mutableListOf<CitySearchResult>()
                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.getJSONObject(i)
                    list.add(
                        CitySearchResult(
                            name = item.optString("name"),
                            country = item.optString("country"),
                            countryCode = item.optString("country_code"),
                            admin1 = item.optString("admin1"),
                            latitude = item.optDouble("latitude", 0.0),
                            longitude = item.optDouble("longitude", 0.0),
                            timezone = item.optString("timezone", "UTC")
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSunInfo(
        baseUrl: String,
        token: String,
        lat: Double? = null,
        lon: Double? = null,
        tz: String? = null
    ): Result<SunInfo> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext Result.failure(IllegalArgumentException("baseUrl required"))
        try {
            val queryParams = mutableListOf<String>()
            if (lat != null) queryParams.add("lat=$lat")
            if (lon != null) queryParams.add("lon=$lon")
            if (!tz.isNullOrBlank()) queryParams.add("tz=${java.net.URLEncoder.encode(tz, "UTF-8")}")
            val qStr = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
            val url = "${baseUrl.trimEnd('/')}/api/geo/sun$qStr"

            val reqBuilder = Request.Builder().url(url)
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val json = JSONObject(body)
                val info = SunInfo(
                    ok = json.optBoolean("ok", true),
                    city = json.optString("city", ""),
                    country = json.optString("country", ""),
                    timezone = json.optString("timezone", ""),
                    latitude = json.optDouble("latitude", 0.0),
                    longitude = json.optDouble("longitude", 0.0),
                    localTime = json.optString("local_time", ""),
                    localDate = json.optString("local_date", ""),
                    sunrise = json.optString("sunrise", "--:--"),
                    sunset = json.optString("sunset", "--:--")
                )
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveGeoSettings(
        baseUrl: String,
        token: String,
        city: String,
        country: String,
        timezone: String,
        lat: Double,
        lon: Double
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext Result.failure(IllegalArgumentException("baseUrl required"))
        try {
            val url = "${baseUrl.trimEnd('/')}/api/settings"
            val payload = JSONObject().apply {
                put("city", city)
                put("country", country)
                put("timezone", timezone)
                put("lat", lat)
                put("lon", lon)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            addAuthHeaders(reqBuilder, token)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                Result.success(resp.isSuccessful)
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

    suspend fun fetchTimers(baseUrl: String, token: String, homeId: String? = null): Result<List<OutletTimer>> = withContext(Dispatchers.IO) {
        try {
            val url = if (!homeId.isNullOrBlank()) "${baseUrl.trimEnd('/')}/api/timers?home_id=$homeId" else "${baseUrl.trimEnd('/')}/api/timers"
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

    suspend fun fetchGoogleConfig(baseUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/auth/google/config"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val rawId = json.optString("client_id", "").ifBlank { json.optString("clientId", "") }
                    val clientId = if (rawId.isNotBlank()) rawId else com.example.volta.model.GoogleAuthConfig.WEB_CLIENT_ID
                    Result.success(clientId)
                } else {
                    Result.success(com.example.volta.model.GoogleAuthConfig.WEB_CLIENT_ID)
                }
            }
        } catch (e: Exception) {
            Result.success(com.example.volta.model.GoogleAuthConfig.WEB_CLIENT_ID)
        }
    }

    suspend fun exchangeGoogleIdToken(baseUrl: String, credentialToken: String): Result<GoogleAuthResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/auth/google"
            val payload = JSONObject().apply {
                put("credential", credentialToken.trim())
            }
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val json = if (body.isNotBlank()) JSONObject(body) else JSONObject()
                if (resp.isSuccessful && json.optBoolean("ok", false)) {
                    val stripsArr = json.optJSONArray("strips")
                    val stripsList = mutableListOf<String>()
                    if (stripsArr != null) {
                        for (i in 0 until stripsArr.length()) {
                            stripsList.add(stripsArr.getString(i))
                        }
                    }
                    val res = GoogleAuthResponse(
                        ok = true,
                        token = json.optString("token", ""),
                        email = json.optString("email", ""),
                        name = json.optString("name", ""),
                        picture = json.optString("picture", ""),
                        isAdmin = json.optBoolean("is_admin", false),
                        strips = stripsList,
                        maxStrips = json.optInt("max_strips", 1),
                        expiresAt = if (json.has("expires_at") && !json.isNull("expires_at")) json.getLong("expires_at") else null,
                        status = json.optString("status", "active")
                    )
                    Result.success(res)
                } else {
                    val err = json.optString("error", "Google authentication failed (HTTP ${resp.code})")
                    Result.failure(Exception(err))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchVoltageGuard(baseUrl: String, token: String, mac: String): Result<VoltageGuard> = withContext(Dispatchers.IO) {
        try {
            val cleanMac = mac.replace(":", "").uppercase()
            val url = "${baseUrl.trimEnd('/')}/api/voltage-guard?mac=$cleanMac"
            val reqBuilder = Request.Builder().url(url).get()
            addAuthHeaders(reqBuilder, token)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val guardObj = json.optJSONObject("guard") ?: return@withContext Result.failure(Exception("Missing guard object"))
                Result.success(parseVoltageGuard(guardObj))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateVoltageGuard(
        baseUrl: String,
        token: String,
        mac: String,
        enabled: Boolean,
        minV: Double,
        maxV: Double,
        safeDelayMin: Int,
        tempEnabled: Boolean,
        maxTempC: Double
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/voltage-guard"
            val payload = JSONObject().apply {
                put("mac", mac.replace(":", "").uppercase())
                put("enabled", enabled)
                put("min_v", minV)
                put("max_v", maxV)
                put("safe_delay_min", safeDelayMin)
                put("temp_enabled", tempEnabled)
                put("max_temp_c", maxTempC)
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

    suspend fun resetVoltageGuard(baseUrl: String, token: String, mac: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/voltage-guard/reset"
            val payload = JSONObject().apply {
                put("mac", mac.replace(":", "").uppercase())
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
}
