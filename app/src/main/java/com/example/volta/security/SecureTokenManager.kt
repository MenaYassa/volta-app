package com.example.volta.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Enterprise-grade persistent credential storage backed by private SharedPreferences.
 * Maintains atomic persistence across process recreation, app closure, and device reboots.
 */
class SecureTokenManager(context: Context) {

    private val privatePrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        val clean = token.trim()
        privatePrefs.edit { putString(KEY_SERVER_TOKEN, clean) }
    }

    fun getToken(): String {
        return privatePrefs.getString(KEY_SERVER_TOKEN, "") ?: ""
    }

    fun clearToken() {
        privatePrefs.edit { remove(KEY_SERVER_TOKEN) }
    }

    fun saveHomeWifi(ssid: String, pass: String) {
        val cleanSsid = ssid.trim()
        privatePrefs.edit {
            putString(KEY_HOME_WIFI_SSID, cleanSsid)
            putString(KEY_HOME_WIFI_PASS, pass)
        }
    }

    fun getHomeWifiSsid(): String {
        return privatePrefs.getString(KEY_HOME_WIFI_SSID, "") ?: ""
    }

    fun getHomeWifiPassword(): String {
        return privatePrefs.getString(KEY_HOME_WIFI_PASS, "") ?: ""
    }

    fun clearHomeWifi() {
        privatePrefs.edit {
            remove(KEY_HOME_WIFI_SSID)
            remove(KEY_HOME_WIFI_PASS)
        }
    }

    companion object {
        private const val PREFS_NAME = "volta_secure_prefs"
        private const val KEY_SERVER_TOKEN = "server_token"
        private const val KEY_HOME_WIFI_SSID = "home_wifi_ssid"
        private const val KEY_HOME_WIFI_PASS = "home_wifi_pass"

        @Volatile
        private var instance: SecureTokenManager? = null

        fun getInstance(context: Context): SecureTokenManager {
            return instance ?: synchronized(this) {
                instance ?: SecureTokenManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
