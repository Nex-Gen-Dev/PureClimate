package com.midea.pureclimate.ir

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.charset.StandardCharsets

/**
 * Talks to the PureClimate ESP32 IR receiver over USB OTG serial (115200 8N1).
 * The firmware prints one line per captured signal: "IR:<mark>,<space>,...".
 * Supports CP210x, CH340/CH9102, FTDI and native-USB (CDC) ESP32 boards.
 */
class UsbIrReceiver(context: Context) {
    private val app = context.applicationContext
    private val usb = app.getSystemService(Context.USB_SERVICE) as UsbManager

    enum class Status { DISCONNECTED, NO_PERMISSION, CONNECTING, CONNECTED, ERROR }

    private val _status = MutableStateFlow(Status.DISCONNECTED)
    val status: StateFlow<Status> = _status

    private val _detail = MutableStateFlow("Plug the ESP32 receiver into your phone (USB OTG).")
    val detail: StateFlow<String> = _detail

    private val _captures = MutableSharedFlow<IntArray>(extraBufferCapacity = 16)
    val captures: SharedFlow<IntArray> = _captures

    private var port: UsbSerialPort? = null
    private var io: SerialInputOutputManager? = null
    private val lineBuf = StringBuilder()
    private var receiverRegistered = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted) connect() else {
                _status.value = Status.NO_PERMISSION
                _detail.value = "USB permission was declined. Tap Connect to try again."
            }
        }
    }

    /** Finds the first supported USB-serial device, asks permission if needed, opens it. */
    fun connect() {
        if (port != null) return
        val drivers = findDrivers()
        if (drivers.isEmpty()) {
            _status.value = Status.DISCONNECTED
            _detail.value = "No receiver found. Check the OTG adapter and cable (data, not charge-only)."
            return
        }
        val driver = drivers.first()
        val device: UsbDevice = driver.device
        if (!usb.hasPermission(device)) {
            requestPermission(device)
            return
        }
        _status.value = Status.CONNECTING
        try {
            val conn = usb.openDevice(device) ?: run {
                _status.value = Status.ERROR
                _detail.value = "Could not open the USB device."
                return
            }
            val p = driver.ports.first()
            p.open(conn)
            p.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching { p.setDTR(true); p.setRTS(false) }
            port = p
            io = SerialInputOutputManager(p, listener).also { it.start() }
            runCatching { p.write("PING\n".toByteArray(), 200) }
            _status.value = Status.CONNECTED
            _detail.value = "Receiver connected (${device.productName ?: "USB serial"})."
        } catch (e: Exception) {
            Log.e(TAG, "open failed", e)
            disconnect()
            _status.value = Status.ERROR
            _detail.value = "Connection failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Known USB-serial chips plus Espressif native USB, then any CDC-ACM device as a fallback. */
    private fun findDrivers(): List<UsbSerialDriver> {
        val table = UsbSerialProber.getDefaultProbeTable()
        for (pid in intArrayOf(0x1001, 0x4001, 0x0002, 0x80D1)) {
            table.addProduct(ESPRESSIF_VID, pid, CdcAcmSerialDriver::class.java)
        }
        val found = UsbSerialProber(table).findAllDrivers(usb)
        if (found.isNotEmpty()) return found
        return usb.deviceList.values.filter { dev ->
            (0 until dev.interfaceCount).any { dev.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_COMM }
        }.map { CdcAcmSerialDriver(it) }
    }

    fun disconnect() {
        runCatching { io?.stop() }
        io = null
        runCatching { port?.close() }
        port = null
        if (_status.value == Status.CONNECTED || _status.value == Status.CONNECTING) {
            _status.value = Status.DISCONNECTED
            _detail.value = "Receiver disconnected."
        }
    }

    fun release() {
        disconnect()
        if (receiverRegistered) {
            runCatching { app.unregisterReceiver(permissionReceiver) }
            receiverRegistered = false
        }
    }

    private fun requestPermission(device: UsbDevice) {
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                app, permissionReceiver, IntentFilter(ACTION_PERMISSION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(ACTION_PERMISSION).setPackage(app.packageName)
        val pi = PendingIntent.getBroadcast(app, 0, intent, flags)
        _status.value = Status.NO_PERMISSION
        _detail.value = "Allow PureClimate to use the USB receiver."
        usb.requestPermission(device, pi)
    }

    private val listener = object : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray) {
            synchronized(lineBuf) {
                lineBuf.append(String(data, StandardCharsets.US_ASCII))
                while (true) {
                    val nl = lineBuf.indexOf("\n")
                    if (nl < 0) break
                    val line = lineBuf.substring(0, nl).trim()
                    lineBuf.delete(0, nl + 1)
                    handleLine(line)
                }
                if (lineBuf.length > MAX_LINE) lineBuf.setLength(0)
            }
        }

        override fun onRunError(e: Exception) {
            Log.w(TAG, "serial stopped", e)
            runCatching { port?.close() }
            port = null
            io = null
            _status.value = Status.DISCONNECTED
            _detail.value = "Receiver unplugged."
        }
    }

    private fun handleLine(line: String) {
        when {
            line.startsWith("IR:") -> SignalMatcher.parseLine(line)?.let { _captures.tryEmit(it) }
            line.startsWith("PURECLIMATE-RX") -> _detail.value = "Receiver ready: $line"
        }
    }

    companion object {
        private const val TAG = "UsbIrReceiver"
        private const val ACTION_PERMISSION = "com.midea.pureclimate.USB_PERMISSION"
        private const val MAX_LINE = 64 * 1024
        private const val ESPRESSIF_VID = 0x303A
    }
}
