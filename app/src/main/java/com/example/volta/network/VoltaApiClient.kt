package com.example.volta.network

import com.example.volta.model.Outlet
import com.example.volta.model.PowerStrip
import com.example.volta.model.TelemetryPoint
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

    data class ServerHealthInfo(
        val ok: Boolean,
        val serverIp: String,
        val devicePort: Int = 10086,
        val controller: Boolean = true
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
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
                            list.add(PowerStrip(mac, name, model, fw, ip, online, voltageV, rssi, lastSeen, outletsList))
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
        range: String,
        costPerKwh: Double
    ): Result<List<TopConsumer>> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/analytics/leaderboard"
            val payload = JSONObject().apply {
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
                    val mac = item.optString("mac", "")
                    val stripName = item.optString("strip_name", "MTTL ${mac.takeLast(6)}")
                    val outlet = item.optInt("outlet", 1)
                    val outletName = item.optString("outlet_name", "Outlet $outlet")
                    val kwh = item.optDouble("kwh", item.optDouble("consumed_kwh", 0.0))
                    val cost = item.optDouble("cost", (kwh * costPerKwh * 100).toLong() / 100.0)

                    result.add(
                        TopConsumer(
                            mac = mac,
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
                if (resp.isSuccessful) Result.success(body)
                else Result.failure(Exception("HTTP ${resp.code}: $body"))
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
}
