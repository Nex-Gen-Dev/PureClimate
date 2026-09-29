package com.midea.pureclimate

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AppTheme(val label: String) {
    CLASSIC_LCD("Classic LCD"),
    DARK_MINIMAL("Dark Minimal"),
    LIGHT_CLEAN("Light Clean")
}

data class AirConditioner(
    val id: String,
    var name: String,
    var room: String,
    var isPowerOn: Boolean = false,
    var targetTemp: Int = 24,
    var mode: MideaProtocol.Mode = MideaProtocol.Mode.COOL,
    var fanSpeed: MideaProtocol.FanSpeed = MideaProtocol.FanSpeed.AUTO,
    var isSwingOn: Boolean = false
)

class DeviceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pure_climate_prefs", Context.MODE_PRIVATE)

    fun getTheme(): AppTheme {
        val name = prefs.getString("app_theme", AppTheme.CLASSIC_LCD.name)
        return AppTheme.valueOf(name ?: AppTheme.CLASSIC_LCD.name)
    }

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("app_theme", theme.name).apply()
    }

    fun loadDevices(): List<AirConditioner> {
        val jsonStr = prefs.getString("saved_devices", null) ?: return defaultDevices()
        return try {
            val list = mutableListOf<AirConditioner>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AirConditioner(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        room = obj.getString("room"),
                        isPowerOn = obj.optBoolean("isPowerOn", false),
                        targetTemp = obj.optInt("targetTemp", 24),
                        mode = MideaProtocol.Mode.valueOf(obj.optString("mode", "COOL")),
                        fanSpeed = MideaProtocol.FanSpeed.valueOf(obj.optString("fanSpeed", "AUTO")),
                        isSwingOn = obj.optBoolean("isSwingOn", false)
                    )
                )
            }
            if (list.isEmpty()) defaultDevices() else list
        } catch (e: Exception) {
            defaultDevices()
        }
    }

    fun saveDevices(devices: List<AirConditioner>) {
        val array = JSONArray()
        for (dev in devices) {
            val obj = JSONObject().apply {
                put("id", dev.id)
                put("name", dev.name)
                put("room", dev.room)
                put("isPowerOn", dev.isPowerOn)
                put("targetTemp", dev.targetTemp)
                put("mode", dev.mode.name)
                put("fanSpeed", dev.fanSpeed.name)
                put("isSwingOn", dev.isSwingOn)
            }
            array.put(obj)
        }
        prefs.edit().putString("saved_devices", array.toString()).apply()
    }

    private fun defaultDevices(): List<AirConditioner> {
        return listOf(
            AirConditioner(UUID.randomUUID().toString(), "Main AC", "Living Room", false, 24, MideaProtocol.Mode.COOL, MideaProtocol.FanSpeed.AUTO)
        )
    }
}
