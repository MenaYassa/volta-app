package com.example.volta.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.volta.model.Outlet
import com.example.volta.model.PowerStrip
import com.example.volta.model.Schedule
import com.example.volta.model.TelemetryPoint
import com.example.volta.model.TopConsumer
import com.example.volta.model.VoltaSettings

class VoltaDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "volta.db"
        const val DATABASE_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE strips (
                mac TEXT PRIMARY KEY,
                name TEXT,
                model TEXT,
                fw TEXT,
                ip TEXT,
                online INTEGER,
                voltage_v REAL,
                rssi INTEGER,
                last_seen INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE outlets (
                mac TEXT,
                n INTEGER,
                name TEXT,
                on_state INTEGER,
                power_w REAL,
                energy_kwh REAL,
                temp_c INTEGER,
                locked INTEGER,
                PRIMARY KEY (mac, n)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE schedules (
                id TEXT PRIMARY KEY,
                strip_mac TEXT,
                outlet INTEGER,
                action_on INTEGER,
                kind TEXT,
                time TEXT,
                offset_minutes INTEGER,
                days TEXT,
                timezone TEXT,
                label TEXT,
                enabled INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE telemetry (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER,
                mac TEXT,
                outlet INTEGER,
                power_w REAL,
                voltage_v REAL,
                temp_c INTEGER,
                energy_kwh REAL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_telemetry_time ON telemetry (timestamp)")
        db.execSQL("CREATE INDEX idx_telemetry_mac_outlet ON telemetry (mac, outlet)")

        db.execSQL(
            """
            CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS settings")
        db.execSQL("DROP TABLE IF EXISTS telemetry")
        db.execSQL("DROP TABLE IF EXISTS schedules")
        db.execSQL("DROP TABLE IF EXISTS outlets")
        db.execSQL("DROP TABLE IF EXISTS strips")
        onCreate(db)
    }

    fun getAllStrips(): List<PowerStrip> {
        val db = readableDatabase
        val strips = mutableListOf<PowerStrip>()
        val cursor = db.rawQuery("SELECT mac, name, model, fw, ip, online, voltage_v, rssi, last_seen FROM strips ORDER BY mac ASC", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                val mac = c.getString(0)
                val name = c.getString(1) ?: ""
                val model = c.getString(2) ?: "MTTL-W01"
                val fw = c.getString(3) ?: "1.0.0"
                val ip = c.getString(4) ?: "192.168.1.1"
                val online = c.getInt(5) == 1
                val voltageV = c.getDouble(6)
                val rssi = c.getInt(7)
                val lastSeen = c.getLong(8)

                val outlets = getOutletsForStrip(mac)
                strips.add(
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
                        outlets = outlets
                    )
                )
            }
        }
        return strips
    }

    private fun getOutletsForStrip(mac: String): List<Outlet> {
        val db = readableDatabase
        val outlets = mutableListOf<Outlet>()
        val cursor = db.rawQuery(
            "SELECT n, name, on_state, power_w, energy_kwh, temp_c, locked FROM outlets WHERE mac = ? ORDER BY n ASC",
            arrayOf(mac)
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val n = c.getInt(0)
                val name = c.getString(1) ?: "Outlet $n"
                val onState = c.getInt(2) == 1
                val powerW = c.getDouble(3)
                val energyKwh = c.getDouble(4)
                val tempC = c.getInt(5)
                val locked = c.getInt(6) == 1
                outlets.add(
                    Outlet(
                        n = n,
                        name = name,
                        on = onState,
                        powerW = powerW,
                        energyKwh = energyKwh,
                        tempC = tempC,
                        locked = locked
                    )
                )
            }
        }
        if (outlets.isEmpty()) {
            return (1..4).map { Outlet(it, "Outlet $it") }
        }
        return outlets
    }

    fun upsertStrip(strip: PowerStrip) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cvStrip = ContentValues().apply {
                put("mac", strip.mac)
                put("name", strip.name)
                put("model", strip.model)
                put("fw", strip.fw)
                put("ip", strip.ip)
                put("online", if (strip.online) 1 else 0)
                put("voltage_v", strip.voltageV)
                put("rssi", strip.rssi)
                put("last_seen", strip.lastSeen)
            }
            db.insertWithOnConflict("strips", null, cvStrip, SQLiteDatabase.CONFLICT_REPLACE)

            for (outlet in strip.outlets) {
                val cvOutlet = ContentValues().apply {
                    put("mac", strip.mac)
                    put("n", outlet.n)
                    put("name", outlet.name)
                    put("on_state", if (outlet.on) 1 else 0)
                    put("power_w", outlet.powerW)
                    put("energy_kwh", outlet.energyKwh)
                    put("temp_c", outlet.tempC)
                    put("locked", if (outlet.locked) 1 else 0)
                }
                db.insertWithOnConflict("outlets", null, cvOutlet, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteStrip(mac: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("outlets", "mac = ?", arrayOf(mac))
            db.delete("schedules", "strip_mac = ?", arrayOf(mac))
            db.delete("telemetry", "mac = ?", arrayOf(mac))
            db.delete("strips", "mac = ?", arrayOf(mac))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateStripName(mac: String, newName: String) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("name", newName) }
        db.update("strips", cv, "mac = ?", arrayOf(mac))
    }

    fun updateOutletName(mac: String, outletN: Int, newName: String) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("name", newName) }
        db.update("outlets", cv, "mac = ? AND n = ?", arrayOf(mac, outletN.toString()))
    }

    fun updateOutletLock(mac: String, outletN: Int, locked: Boolean) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("locked", if (locked) 1 else 0) }
        db.update("outlets", cv, "mac = ? AND n = ?", arrayOf(mac, outletN.toString()))
    }

    fun getAllSchedules(): List<Schedule> {
        val db = readableDatabase
        val list = mutableListOf<Schedule>()
        val cursor = db.rawQuery(
            "SELECT id, strip_mac, outlet, action_on, kind, time, offset_minutes, days, timezone, label, enabled FROM schedules ORDER BY time ASC",
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val daysStr = c.getString(7) ?: "0,1,2,3,4,5,6"
                val daysList = daysStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                list.add(
                    Schedule(
                        id = c.getString(0),
                        stripMac = c.getString(1),
                        outlet = c.getInt(2),
                        actionOn = c.getInt(3) == 1,
                        kind = c.getString(4) ?: "time",
                        time = c.getString(5) ?: "23:00",
                        offsetMinutes = c.getInt(6),
                        days = daysList,
                        timezone = c.getString(8) ?: "Africa/Cairo",
                        label = c.getString(9) ?: "",
                        enabled = c.getInt(10) == 1
                    )
                )
            }
        }
        return list
    }

    fun upsertSchedule(schedule: Schedule) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", schedule.id)
            put("strip_mac", schedule.stripMac)
            put("outlet", schedule.outlet)
            put("action_on", if (schedule.actionOn) 1 else 0)
            put("kind", schedule.kind)
            put("time", schedule.time)
            put("offset_minutes", schedule.offsetMinutes)
            put("days", schedule.days.joinToString(","))
            put("timezone", schedule.timezone)
            put("label", schedule.label)
            put("enabled", if (schedule.enabled) 1 else 0)
        }
        db.insertWithOnConflict("schedules", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteSchedule(id: String) {
        val db = writableDatabase
        db.delete("schedules", "id = ?", arrayOf(id))
    }

    fun toggleSchedule(id: String, enabled: Boolean) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        db.update("schedules", cv, "id = ?", arrayOf(id))
    }

    fun insertTelemetry(point: TelemetryPoint) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("timestamp", point.timestamp)
            put("mac", point.mac)
            put("outlet", point.outlet)
            put("power_w", point.powerW)
            put("voltage_v", point.voltageV)
            put("temp_c", point.tempC)
            put("energy_kwh", point.energyKwh)
        }
        db.insert("telemetry", null, cv)
    }

    fun getTelemetry(mac: String?, outlet: Int?, sinceTimestamp: Long): List<TelemetryPoint> {
        val db = readableDatabase
        val points = mutableListOf<TelemetryPoint>()
        val args = mutableListOf<String>()
        var query = "SELECT id, timestamp, mac, outlet, power_w, voltage_v, temp_c, energy_kwh FROM telemetry WHERE timestamp >= ?"
        args.add(sinceTimestamp.toString())

        if (!mac.isNullOrBlank()) {
            query += " AND mac = ?"
            args.add(mac)
        }
        if (outlet != null && outlet > 0) {
            query += " AND outlet = ?"
            args.add(outlet.toString())
        }
        query += " ORDER BY timestamp ASC"

        val cursor = db.rawQuery(query, args.toTypedArray())
        cursor.use { c ->
            while (c.moveToNext()) {
                points.add(
                    TelemetryPoint(
                        id = c.getLong(0),
                        timestamp = c.getLong(1),
                        mac = c.getString(2),
                        outlet = c.getInt(3),
                        powerW = c.getDouble(4),
                        voltageV = c.getDouble(5),
                        tempC = c.getInt(6),
                        energyKwh = c.getDouble(7)
                    )
                )
            }
        }
        return points
    }

    fun getTopConsumers(sinceTimestamp: Long, costPerKwh: Double): List<TopConsumer> {
        val db = readableDatabase
        val result = mutableListOf<TopConsumer>()
        // Calculate max energy - min energy per outlet in the time window
        val query = """
            SELECT t.mac, IFNULL(s.name, t.mac) as strip_name, t.outlet, IFNULL(o.name, 'Outlet ' || t.outlet) as outlet_name,
                   MAX(t.energy_kwh) - MIN(t.energy_kwh) as consumed_kwh
            FROM telemetry t
            LEFT JOIN strips s ON t.mac = s.mac
            LEFT JOIN outlets o ON t.mac = o.mac AND t.outlet = o.n
            WHERE t.timestamp >= ?
            GROUP BY t.mac, t.outlet
            HAVING consumed_kwh >= 0
            ORDER BY consumed_kwh DESC
            LIMIT 10
        """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(sinceTimestamp.toString()))
        cursor.use { c ->
            while (c.moveToNext()) {
                val mac = c.getString(0)
                val stripName = c.getString(1) ?: mac
                val outlet = c.getInt(2)
                val outletName = c.getString(3) ?: "Outlet $outlet"
                val kwh = c.getDouble(4).coerceAtLeast(0.0)
                result.add(
                    TopConsumer(
                        mac = mac,
                        stripName = stripName,
                        outlet = outlet,
                        outletName = outletName,
                        kwh = (kwh * 1000.0).toLong() / 1000.0,
                        cost = (kwh * costPerKwh * 100.0).toLong() / 100.0
                    )
                )
            }
        }
        return result
    }

    fun clearTelemetry() {
        val db = writableDatabase
        db.delete("telemetry", null, null)
    }

    fun loadSettings(): VoltaSettings {
        val db = readableDatabase
        val map = mutableMapOf<String, String>()
        val cursor = db.rawQuery("SELECT key, value FROM settings", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                map[c.getString(0)] = c.getString(1) ?: ""
            }
        }
        return VoltaSettings(
            serverUrl = map["serverUrl"] ?: "http://192.168.1.100:8080",
            serverToken = map["serverToken"] ?: "",
            isStandalone = map["isStandalone"]?.toBooleanStrictOrNull() ?: true,
            costPerKwh = map["costPerKwh"]?.toDoubleOrNull() ?: 1.2,
            currency = map["currency"] ?: "EGP",
            ntfyTopic = map["ntfyTopic"] ?: "",
            alertOfflineMin = map["alertOfflineMin"]?.toIntOrNull() ?: 10,
            notifyOffline = map["notifyOffline"]?.toBooleanStrictOrNull() ?: true,
            notifySwitch = map["notifySwitch"]?.toBooleanStrictOrNull() ?: false,
            tempAlertC = map["tempAlertC"]?.toIntOrNull() ?: 60,
            notifyTemp = map["notifyTemp"]?.toBooleanStrictOrNull() ?: true,
            voltageMin = map["voltageMin"]?.toDoubleOrNull() ?: 200.0,
            voltageMax = map["voltageMax"]?.toDoubleOrNull() ?: 250.0,
            notifyVoltage = map["notifyVoltage"]?.toBooleanStrictOrNull() ?: true,
            latitude = map["latitude"]?.toDoubleOrNull() ?: 30.0444,
            longitude = map["longitude"]?.toDoubleOrNull() ?: 31.2357
        )
    }

    fun saveSettings(s: VoltaSettings) {
        val db = writableDatabase
        val map = mapOf(
            "serverUrl" to s.serverUrl,
            "serverToken" to s.serverToken,
            "isStandalone" to s.isStandalone.toString(),
            "costPerKwh" to s.costPerKwh.toString(),
            "currency" to s.currency,
            "ntfyTopic" to s.ntfyTopic,
            "alertOfflineMin" to s.alertOfflineMin.toString(),
            "notifyOffline" to s.notifyOffline.toString(),
            "notifySwitch" to s.notifySwitch.toString(),
            "tempAlertC" to s.tempAlertC.toString(),
            "notifyTemp" to s.notifyTemp.toString(),
            "voltageMin" to s.voltageMin.toString(),
            "voltageMax" to s.voltageMax.toString(),
            "notifyVoltage" to s.notifyVoltage.toString(),
            "latitude" to s.latitude.toString(),
            "longitude" to s.longitude.toString()
        )
        db.beginTransaction()
        try {
            for ((k, v) in map) {
                val cv = ContentValues().apply {
                    put("key", k)
                    put("value", v)
                }
                db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
