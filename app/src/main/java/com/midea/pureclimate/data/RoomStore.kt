package com.midea.pureclimate.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Saves rooms, AC states and the theme choice in SharedPreferences. */
class RoomStore(context: Context) {
    private val prefs = context.getSharedPreferences("pure_climate_prefs", Context.MODE_PRIVATE)

    fun loadTheme(): AppTheme =
        runCatching { AppTheme.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(AppTheme.CLASSIC_LCD)

    fun saveTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun loadRooms(): List<Room> {
        val json = prefs.getString(KEY_ROOMS, null)
        if (json != null) {
            return runCatching { parseRooms(JSONArray(json)) }.getOrDefault(emptyList())
        }
        // First launch of this version: carry over rooms saved by the old app.
        val migrated = migrateLegacy()
        val rooms = migrated.ifEmpty { listOf(Room(newId(), "Living Room")) }
        saveRooms(rooms)
        return rooms
    }

    fun saveRooms(rooms: List<Room>) {
        val arr = JSONArray()
        rooms.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_ROOMS, arr.toString()).apply()
    }

    private fun toJson(r: Room) = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        put("protocol", r.protocolId ?: JSONObject.NULL)
        put("power", r.power)
        put("temp", r.temp)
        put("mode", r.mode.name)
        put("fan", r.fan.name)
        put("swing", r.swing)
        val buttons = JSONArray()
        r.learned.forEach { b ->
            buttons.put(JSONObject().apply {
                put("label", b.label)
                put("carrier", b.carrierHz)
                put("pattern", b.pattern.joinToString(","))
            })
        }
        put("learned", buttons)
    }

    private fun parseRooms(arr: JSONArray): List<Room> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val btns = o.optJSONArray("learned") ?: JSONArray()
        Room(
            id = o.getString("id"),
            name = o.getString("name"),
            protocolId = if (o.isNull("protocol")) null else o.optString("protocol", Room.DEFAULT_PROTOCOL),
            learned = (0 until btns.length()).map { j ->
                val b = btns.getJSONObject(j)
                LearnedButton(
                    label = b.getString("label"),
                    pattern = b.getString("pattern").split(",").filter { it.isNotBlank() }
                        .map { it.trim().toInt() }.toIntArray(),
                    carrierHz = b.optInt("carrier", 38000),
                )
            },
            power = o.optBoolean("power", false),
            temp = o.optInt("temp", 24).coerceIn(Room.MIN_TEMP, Room.MAX_TEMP),
            mode = runCatching { AcMode.valueOf(o.optString("mode")) }.getOrDefault(AcMode.COOL),
            fan = runCatching { FanSpeed.valueOf(o.optString("fan")) }.getOrDefault(FanSpeed.AUTO),
            swing = o.optBoolean("swing", false),
        )
    }

    /** Rooms saved by earlier PureClimate builds under "saved_devices". */
    private fun migrateLegacy(): List<Room> {
        val old = prefs.getString("saved_devices", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(old)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Room(
                    id = o.optString("id", newId()),
                    name = o.optString("room").ifBlank { o.optString("name", "Room ${i + 1}") },
                    temp = o.optInt("targetTemp", 24).coerceIn(Room.MIN_TEMP, Room.MAX_TEMP),
                    mode = runCatching { AcMode.valueOf(o.optString("mode")) }.getOrDefault(AcMode.COOL),
                    fan = runCatching { FanSpeed.valueOf(o.optString("fanSpeed")) }.getOrDefault(FanSpeed.AUTO),
                    swing = o.optBoolean("isSwingOn", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_ROOMS = "rooms_v2"
        private const val KEY_THEME = "app_theme"
        fun newId(): String = UUID.randomUUID().toString()
    }
}
