package com.example.volta.protocol

import com.example.volta.model.Outlet
import java.util.regex.Pattern

object TonlyProtocol {
    private val BOOTINFO_PATTERN = Pattern.compile(
        "^up:bootinfo:([^;\\r\\n]+);([0-9A-Fa-f]{12});([0-9A-Fa-f]{12});([^;\\r\\n]+);connect$",
        Pattern.CASE_INSENSITIVE
    )

    private val GETINFO_ENTRY_PATTERN = Pattern.compile(
        "(?<ch>[1-5]):(?<runtime>-?\\d+);(?<relay>on|off);(?<state>-?\\d+);(?<overload>[^;:]+);(?<overheat>[^;:]+);(?<power>-?\\d+);(?<energy>[0-9A-Fa-f]{8});(?<prev>[0-9A-Fa-f]{8});(?<config>[0-9A-Fa-f]{8});(?<status>[^;:]+);(?<event>[0-9A-Fa-f]{2});(?<temp>-?\\d+)",
        Pattern.CASE_INSENSITIVE
    )

    private val ONOFF_ACK_PATTERN = Pattern.compile("^up:onoff:([0-4]):(on|off)$", Pattern.CASE_INSENSITIVE)
    private val EVENT_ONOFF_PATTERN = Pattern.compile("^up:event:onoff:([0-4]):(on|off)$", Pattern.CASE_INSENSITIVE)
    private val POWER_REPORT_PATTERN = Pattern.compile("^up:power_report:([1-5]):(-?\\d+)$", Pattern.CASE_INSENSITIVE)
    private val QUERY_RSSI_PATTERN = Pattern.compile("^up:query:(-?\\d+)$")

    fun buildOnOff(outlet: Int, on: Boolean): String {
        val state = if (on) "on" else "off"
        return "up:onoff:$outlet:$state\r\n"
    }

    fun buildGetInfoAll(): String {
        return "up:getinfo:all\r\n"
    }

    fun buildQueryVoltage(): String {
        return "up:power_report:1:vol\r\n"
    }

    fun buildQueryRssi(): String {
        return "up:query:wifirssi\r\n"
    }

    fun buildProvisionIp(serverIp: String): String {
        return "up:ip:$serverIp\r\n"
    }

    fun buildProvisionConnect(ssid: String, pass: String): String {
        return "up:connect:$ssid:$pass\r\n"
    }

    data class BootInfo(
        val model: String,
        val mac: String,
        val fw: String
    )

    data class ParsedOutletInfo(
        val channel: Int,
        val on: Boolean,
        val powerW: Double,
        val energyKwh: Double,
        val tempC: Int
    )

    fun parseBootInfo(line: String): BootInfo? {
        val trimmed = line.trim()
        val matcher = BOOTINFO_PATTERN.matcher(trimmed)
        return if (matcher.matches()) {
            BootInfo(
                model = matcher.group(1) ?: "MTTL-W01",
                mac = (matcher.group(2) ?: "").uppercase(),
                fw = matcher.group(4) ?: "1.0.0"
            )
        } else null
    }

    fun parseGetInfo(line: String): List<ParsedOutletInfo> {
        var text = line.trim()
        if (text.startsWith("up:getinfo:")) {
            text = text.substring("up:getinfo:".length)
        }
        val results = mutableListOf<ParsedOutletInfo>()
        val matcher = GETINFO_ENTRY_PATTERN.matcher(text)
        while (matcher.find()) {
            val ch = matcher.group("ch")?.toIntOrNull() ?: continue
            if (ch > 4) continue // channel 5 is aggregate, skip
            val relay = matcher.group("relay")?.equals("on", ignoreCase = true) == true
            val powerMw = matcher.group("power")?.toLongOrNull() ?: 0L
            val energyHex = matcher.group("energy") ?: "00000000"
            val energyWh = try {
                energyHex.toLong(16)
            } catch (_: Exception) {
                0L
            }
            val tempC = matcher.group("temp")?.toIntOrNull() ?: 25

            results.add(
                ParsedOutletInfo(
                    channel = ch,
                    on = relay,
                    powerW = (powerMw / 1000.0 * 100.0).toLong() / 100.0,
                    energyKwh = (energyWh / 1000.0 * 1000.0).toLong() / 1000.0,
                    tempC = tempC
                )
            )
        }
        return results
    }

    fun parseVoltage(line: String): Double? {
        val trimmed = line.trim()
        val matcher = POWER_REPORT_PATTERN.matcher(trimmed)
        if (matcher.matches()) {
            val ch = matcher.group(1)?.toIntOrNull() ?: 1
            if (ch == 1) {
                val mv = matcher.group(2)?.toDoubleOrNull() ?: 0.0
                if (mv > 1000.0) {
                    return mv / 1000.0
                }
            }
        }
        return null
    }

    fun parseRssi(line: String): Int? {
        val trimmed = line.trim()
        val matcher = QUERY_RSSI_PATTERN.matcher(trimmed)
        return if (matcher.matches()) {
            matcher.group(1)?.toIntOrNull()
        } else null
    }

    data class OnOffEvent(val channel: Int, val on: Boolean)

    fun parseOnOffAck(line: String): OnOffEvent? {
        val trimmed = line.trim()
        val matcher = ONOFF_ACK_PATTERN.matcher(trimmed)
        return if (matcher.matches()) {
            val ch = matcher.group(1)?.toIntOrNull() ?: 0
            val on = matcher.group(2)?.equals("on", ignoreCase = true) == true
            OnOffEvent(ch, on)
        } else null
    }

    fun parseButtonEvent(line: String): OnOffEvent? {
        val trimmed = line.trim()
        val matcher = EVENT_ONOFF_PATTERN.matcher(trimmed)
        return if (matcher.matches()) {
            val ch = matcher.group(1)?.toIntOrNull() ?: 0
            val on = matcher.group(2)?.equals("on", ignoreCase = true) == true
            OnOffEvent(ch, on)
        } else null
    }
}
