package com.midea.pureclimate

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.midea.pureclimate.ui.BrandScreen
import com.midea.pureclimate.ui.HomeScreen
import com.midea.pureclimate.ui.LearnScreen
import com.midea.pureclimate.ui.LocalPalette
import com.midea.pureclimate.ui.PureClimateTheme
import com.midea.pureclimate.ui.RemoteScreen
import com.midea.pureclimate.ui.ScanScreen
import com.midea.pureclimate.ui.SettingsScreen
import com.midea.pureclimate.ui.SetupMethod

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Remote(val roomId: String) : Screen
    data class Scan(val roomId: String) : Screen
    data class Brand(val roomId: String) : Screen
    data class Learn(val roomId: String) : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var controller: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = AppController(this, lifecycleScope)
        setContent {
            val theme = controller.theme
            LaunchedEffect(theme) {
                val dark = com.midea.pureclimate.ui.paletteFor(theme).isDark
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PureClimateTheme(theme) {
                Box(
                    Modifier.fillMaxSize().background(LocalPalette.current.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    App(controller)
                }
            }
        }
    }
}

@Composable
private fun App(c: AppController) {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val screen = stack.last()
    val sendCount by c.blaster.sendCount.collectAsState()
    val irError by c.blaster.lastError.collectAsState()
    val irOk = c.blaster.hasEmitter

    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    /** Replace everything above Home with the room's remote. */
    fun showRemote(roomId: String) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(Screen.Remote(roomId))
    }
    fun setup(roomId: String, m: SetupMethod) {
        when (m) {
            SetupMethod.MIDEA -> { c.setProtocol(roomId, com.midea.pureclimate.data.Room.DEFAULT_PROTOCOL); showRemote(roomId) }
            SetupMethod.SCAN -> go(Screen.Scan(roomId))
            SetupMethod.BRAND -> go(Screen.Brand(roomId))
            SetupMethod.LEARN -> go(Screen.Learn(roomId))
        }
    }

    BackHandler(enabled = stack.size > 1) { back() }

    LaunchedEffect(irError) {
        irError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            c.blaster.clearError()
        }
    }
    LaunchedEffect(c.codebookError) {
        c.codebookError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val roomName: (String) -> String = { id -> c.room(id)?.name ?: "" }

    when (screen) {
        Screen.Home -> HomeScreen(
            rooms = c.rooms,
            irOk = irOk,
            onOpen = { go(Screen.Remote(it.id)) },
            onDelete = { c.deleteRoom(it.id) },
            onAdd = { name, method ->
                val room = c.addRoom(name)
                go(Screen.Remote(room.id))
                if (method != SetupMethod.MIDEA) setup(room.id, method)
            },
            onSettings = { go(Screen.Settings) },
        )

        Screen.Settings -> SettingsScreen(
            theme = c.theme,
            irOk = irOk,
            protocolCount = c.codebook?.variants()?.size ?: 0,
            version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
            },
            onTheme = c::changeTheme,
            onBack = ::back,
        )

        is Screen.Remote -> {
            val room = c.room(screen.roomId)
            if (room == null) {
                LaunchedEffect(Unit) { back() }
            } else {
                RemoteScreen(
                    room = room,
                    protocolName = c.variantFor(room)?.name,
                    irOk = irOk,
                    sendCount = sendCount,
                    onBack = ::back,
                    onPower = { c.togglePower(room) },
                    onTemp = { c.changeTemp(room, it) },
                    onMode = { c.cycleMode(room) },
                    onFan = { c.cycleFan(room) },
                    onSwing = { c.toggleSwing(room) },
                    onEco = { c.ecoPreset(room) },
                    onLearned = { c.sendLearned(room, it) },
                    onChangeType = { setup(room.id, it) },
                )
            }
        }

        is Screen.Scan -> ScanScreen(
            roomName = roomName(screen.roomId),
            codebook = c.codebook,
            irOk = irOk,
            onSend = { v, pattern -> c.blaster.send(v.carrierHz, pattern) },
            onFound = { v -> c.setProtocol(screen.roomId, v.id); showRemote(screen.roomId) },
            onBack = ::back,
        )

        is Screen.Brand -> BrandScreen(
            codebook = c.codebook,
            currentId = c.room(screen.roomId)?.protocolId,
            irOk = irOk,
            onSend = { v, pattern -> c.blaster.send(v.carrierHz, pattern) },
            onPick = { v -> c.setProtocol(screen.roomId, v.id); showRemote(screen.roomId) },
            onBack = ::back,
        )

        is Screen.Learn -> LearnScreen(
            roomName = roomName(screen.roomId),
            codebook = c.codebook,
            irOk = irOk,
            onSendRaw = { pattern, carrier -> c.blaster.send(carrier, pattern) },
            onUseProtocol = { v -> c.setProtocol(screen.roomId, v.id); showRemote(screen.roomId) },
            onSaveButtons = { b -> c.saveLearned(screen.roomId, b); showRemote(screen.roomId) },
            onBack = ::back,
        )
    }
}
