package com.example.volta.network

import com.example.volta.model.Outlet
import com.example.volta.model.PowerStrip
import com.example.volta.model.Schedule
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(baseUrl: String, token: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/health"
            val reqBuilder = Request.Builder().url(url)
            if (token.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }
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

    suspend fun fetchStrips(baseUrl: String, token: String): Result<List<PowerStrip>> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/strips"
            val reqBuilder = Request.Builder().url(url)
            if (token.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                val body = resp.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val json = JSONObject(body)
                val list = mutableListOf<PowerStrip>()
                val stripsArray = json.optJSONArray("strips") ?: JSONArray()
                for (i in 0 until stripsArray.length()) {
                    val sObj = stripsArray.getJSONObject(i)
                    val mac = sObj.optString("mac")
                    val name = sObj.optString("name", "MTTL ${mac.takeLast(6)}")
                    val model = sObj.optString("model", "MTTL-W01")
                    val fw = sObj.optString("fw", "1.0.0")
                    val ip = sObj.optString("ip", "192.168.1.1")
                    val online = sObj.optBoolean("online", false)
                    val voltageV = sObj.optDouble("voltage_v", 220.0)
                    val rssi = sObj.optInt("rssi", -65)
                    val lastSeen = (sObj.optDouble("last_seen", System.currentTimeMillis() / 1000.0) * 1000).toLong()

                    val outletsList = mutableListOf<Outlet>()
                    val outletsArray = sObj.optJSONArray("outlets") ?: JSONArray()
                    for (j in 0 until outletsArray.length()) {
                        val oObj = outletsArray.getJSONObject(j)
                        val n = oObj.optInt("n", j + 1)
                        val oName = oObj.optString("name", "Outlet $n")
                        val on = oObj.optBoolean("on", false)
                        val powerW = oObj.optDouble("power_w", 0.0)
                        val energyKwh = oObj.optDouble("energy_kwh", 0.0)
                        val tempC = oObj.optInt("temp_c", 25)
                        val locked = oObj.optBoolean("locked", false)
                        outletsList.add(Outlet(n, oName, on, powerW, energyKwh, tempC, locked))
                    }
                    list.add(PowerStrip(mac, name, model, fw, ip, online, voltageV, rssi, lastSeen, outletsList))
                }
                Result.success(list)
            }
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
            if (token.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) Result.success(true)
                else Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
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
            if (token.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful) Result.success(body)
                else Result.failure(Exception("HTTP ${resp.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
