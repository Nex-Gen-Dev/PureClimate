package com.midea.pureclimate.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Wraps the phone's IR emitter. Transmissions run off the main thread, one at a
 * time; if the user taps faster than frames can be sent, only the newest frame
 * is kept (AC remotes send the whole state, so intermediate frames don't matter).
 */
class IrBlaster(context: Context) {
    private val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Frame>(Channel.CONFLATED)

    val hasEmitter: Boolean = runCatching { ir?.hasIrEmitter() == true }.getOrDefault(false)

    private val _sendCount = MutableStateFlow(0)
    /** Increments after every completed transmission (drives the send LED). */
    val sendCount: StateFlow<Int> = _sendCount

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private data class Frame(val carrierHz: Int, val pattern: IntArray)

    init {
        scope.launch {
            for (frame in queue) transmitNow(frame)
        }
    }

    fun send(carrierHz: Int, pattern: IntArray?) {
        if (pattern == null || pattern.isEmpty()) return
        if (!hasEmitter) {
            _lastError.value = "This phone has no IR emitter"
            return
        }
        queue.trySend(Frame(carrierHz, pattern))
    }

    fun clearError() {
        _lastError.value = null
    }

    private fun transmitNow(frame: Frame) {
        val mgr = ir ?: return
        try {
            val pattern = normalize(frame.pattern)
            mgr.transmit(pickCarrier(mgr, frame.carrierHz), pattern)
            _lastError.value = null
            _sendCount.value = _sendCount.value + 1
        } catch (e: Exception) {
            Log.e(TAG, "IR transmit failed", e)
            _lastError.value = "IR send failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** All values positive; even length (ends with a short off period). */
    private fun normalize(p: IntArray): IntArray {
        val clean = p.map { it.coerceIn(1, 1_000_000) }.toMutableList()
        if (clean.size % 2 == 1) clean.add(TRAILING_GAP_US)
        return clean.toIntArray()
    }

    /** Uses the requested carrier, or the closest one the hardware supports. */
    private fun pickCarrier(mgr: ConsumerIrManager, want: Int): Int {
        val ranges = runCatching { mgr.carrierFrequencies }.getOrNull()
        if (ranges.isNullOrEmpty()) return want
        if (ranges.any { want in it.minFrequency..it.maxFrequency }) return want
        return ranges.flatMap { listOf(it.minFrequency, it.maxFrequency) }
            .minByOrNull { kotlin.math.abs(it - want) } ?: want
    }

    companion object {
        private const val TAG = "IrBlaster"
        private const val TRAILING_GAP_US = 10_000
    }
}
