package com.midea.pureclimate

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.midea.pureclimate.data.AcMode
import com.midea.pureclimate.data.AppTheme
import com.midea.pureclimate.data.FanSpeed
import com.midea.pureclimate.data.LearnedButton
import com.midea.pureclimate.data.Room
import com.midea.pureclimate.data.RoomStore
import com.midea.pureclimate.ir.Codebook
import com.midea.pureclimate.ir.IrBlaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App-wide state and the actions the screens call. Lives as long as the activity. */
class AppController(context: Context, scope: CoroutineScope) {
    private val app = context.applicationContext
    private val store = RoomStore(app)
    val blaster = IrBlaster(app)

    var rooms by mutableStateOf(store.loadRooms())
        private set
    var theme by mutableStateOf(store.loadTheme())
        private set
    var codebook by mutableStateOf<Codebook?>(null)
        private set
    var codebookError by mutableStateOf<String?>(null)
        private set

    init {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { app.assets.open("codebook.bin.gz").use { Codebook.load(it) } }
            }
            result.onSuccess { codebook = it }
                .onFailure { codebookError = "Could not load AC codes: ${it.message}" }
        }
    }

    // ---- rooms ---------------------------------------------------------------

    fun room(id: String): Room? = rooms.firstOrNull { it.id == id }

    fun addRoom(name: String, protocolId: String? = Room.DEFAULT_PROTOCOL): Room {
        val room = Room(RoomStore.newId(), name.trim().ifBlank { "New Room" }, protocolId)
        rooms = rooms + room
        store.saveRooms(rooms)
        return room
    }

    fun deleteRoom(id: String) {
        rooms = rooms.filterNot { it.id == id }
        store.saveRooms(rooms)
    }

    fun updateRoom(room: Room) {
        rooms = rooms.map { if (it.id == room.id) room else it }
        store.saveRooms(rooms)
    }

    fun setProtocol(roomId: String, protocolId: String) {
        room(roomId)?.let { updateRoom(it.copy(protocolId = protocolId)) }
    }

    fun saveLearned(roomId: String, buttons: List<LearnedButton>) {
        room(roomId)?.let { updateRoom(it.copy(protocolId = null, learned = buttons)) }
    }

    fun setTheme(t: AppTheme) {
        theme = t
        store.saveTheme(t)
    }

    fun variantFor(room: Room): Codebook.Variant? =
        room.protocolId?.let { id -> codebook?.find(id) }

    // ---- remote actions ------------------------------------------------------

    fun togglePower(room: Room) {
        val next = room.copy(power = !room.power)
        updateRoom(next)
        val v = variantFor(next) ?: return
        if (next.power) sendState(v, next) else blaster.send(v.carrierHz, v.powerOff())
    }

    fun changeTemp(room: Room, delta: Int) {
        val t = (room.temp + delta).coerceIn(Room.MIN_TEMP, Room.MAX_TEMP)
        if (t == room.temp) return
        applyState(room.copy(temp = t))
    }

    fun cycleMode(room: Room) {
        val modes = AcMode.values()
        applyState(room.copy(mode = modes[(room.mode.ordinal + 1) % modes.size]))
    }

    fun cycleFan(room: Room) {
        val fans = FanSpeed.values()
        applyState(room.copy(fan = fans[(room.fan.ordinal + 1) % fans.size]))
    }

    fun toggleSwing(room: Room) {
        val next = room.copy(swing = !room.swing)
        updateRoom(next)
        if (!next.power) return
        val v = variantFor(next) ?: return
        if (v.swingIsToggle) blaster.send(v.carrierHz, v.swingToggle()) else sendState(v, next)
    }

    /** ECO preset: power on, 24°C, COOL, AUTO fan. */
    fun ecoPreset(room: Room) {
        applyState(room.copy(power = true, temp = 24, mode = AcMode.COOL, fan = FanSpeed.AUTO), force = true)
    }

    fun sendLearned(room: Room, button: LearnedButton) {
        blaster.send(button.carrierHz, button.pattern)
    }

    /** Saves the new state; transmits it only while the AC is on, like a real remote. */
    private fun applyState(next: Room, force: Boolean = false) {
        updateRoom(next)
        if (!next.power && !force) return
        val v = variantFor(next) ?: return
        sendState(v, next)
    }

    private fun sendState(v: Codebook.Variant, r: Room) {
        blaster.send(v.carrierHz, v.state(r.mode.ordinal, r.temp, r.fan.ordinal, r.swing))
    }
}
