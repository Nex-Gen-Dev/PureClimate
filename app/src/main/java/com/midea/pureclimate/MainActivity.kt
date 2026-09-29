package com.midea.pureclimate

import android.content.Context
import android.content.Intent
import android.hardware.ConsumerIrManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var irManager: ConsumerIrManager? = null
    private lateinit var deviceManager: DeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        irManager = getSystemService(CONSUMER_IR_SERVICE) as? ConsumerIrManager
        deviceManager = DeviceManager(this)

        setContent {
            val appTheme = remember { mutableStateOf(deviceManager.getTheme()) }
            val colors = getThemeColors(appTheme.value)

            MaterialTheme(colorScheme = darkColorScheme(background = colors.bg, surface = colors.cardBg)) {
                Surface(modifier = Modifier.fillMaxSize(), color = colors.bg) {
                    AppNavigation(deviceManager, irManager, appTheme, colors)
                }
            }
        }
    }
}

data class ThemeColors(
    val bg: Color, val cardBg: Color, val textMain: Color, val textMuted: Color,
    val lcdBg: Color, val lcdText: Color, val btnActive: Color, val btnInactive: Color, val accent: Color
)

fun getThemeColors(theme: AppTheme) = when (theme) {
    AppTheme.CLASSIC_LCD -> ThemeColors(
        bg = Color(0xFF0B0E14), cardBg = Color(0xFF131924), textMain = Color.White, textMuted = Color(0xFF8899B0),
        lcdBg = Color(0xFF98AF9F), lcdText = Color(0xFF0F1A12), btnActive = Color(0xFF222C3D), btnInactive = Color(0xFF18202D), accent = Color(0xFF48CAE4)
    )
    AppTheme.DARK_MINIMAL -> ThemeColors(
        bg = Color(0xFF000000), cardBg = Color(0xFF121212), textMain = Color(0xFFEEEEEE), textMuted = Color(0xFF666666),
        lcdBg = Color(0xFF1E1E1E), lcdText = Color(0xFF00FFCC), btnActive = Color(0xFF333333), btnInactive = Color(0xFF1A1A1A), accent = Color(0xFF00FFCC)
    )
    AppTheme.LIGHT_CLEAN -> ThemeColors(
        bg = Color(0xFFF1F5F9), cardBg = Color(0xFFFFFFFF), textMain = Color(0xFF1E293B), textMuted = Color(0xFF64748B),
        lcdBg = Color(0xFFE2E8F0), lcdText = Color(0xFF0F172A), btnActive = Color(0xFFDBEAFE), btnInactive = Color(0xFFF8FAFC), accent = Color(0xFF3B82F6)
    )
}

@Composable
fun AppNavigation(deviceManager: DeviceManager, irManager: ConsumerIrManager?, themeState: MutableState<AppTheme>, colors: ThemeColors) {
    var devices by remember { mutableStateOf(deviceManager.loadDevices()) }
    var currentRoute by remember { mutableStateOf("home") }
    var activeAcId by remember { mutableStateOf<String?>(null) }

    if (currentRoute == "home") {
        HomeScreen(
            devices = devices, colors = colors,
            themeState = themeState, deviceManager = deviceManager,
            onDeviceClick = {
                activeAcId = it.id
                currentRoute = "remote"
            },
            onDevicesUpdated = {
                devices = it
                deviceManager.saveDevices(it)
            }
        )
    } else {
        val ac = devices.find { it.id == activeAcId }
        if (ac != null) {
            RemoteScreen(
                ac = ac, colors = colors, hasIr = irManager?.hasIrEmitter() == true,
                onBack = { currentRoute = "home" },
                onUpdateAc = { updatedAc, triggerIr, isSwing ->
                    val newList = devices.toMutableList()
                    val idx = newList.indexOfFirst { it.id == updatedAc.id }
                    if (idx != -1) {
                        newList[idx] = updatedAc
                        devices = newList
                        deviceManager.saveDevices(newList)
                        if (triggerIr) {
                            try {
                                val pattern = MideaProtocol.buildPacket(updatedAc.isPowerOn, updatedAc.targetTemp, updatedAc.mode, updatedAc.fanSpeed, isSwing)
                                irManager?.transmit(38000, pattern)
                            } catch (e: Exception) {
                                // Silent catch
                            }
                        }
                    }
                }
            )
        } else {
            currentRoute = "home"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    devices: List<AirConditioner>, colors: ThemeColors, themeState: MutableState<AppTheme>,
    deviceManager: DeviceManager, onDeviceClick: (AirConditioner) -> Unit, onDevicesUpdated: (List<AirConditioner>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PureClimate", fontSize = 28.sp, fontWeight = FontWeight.Black, color = colors.textMain)
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = colors.textMain)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("YOUR ROOMS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textMuted, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(devices) { dev ->
                Card(
                    modifier = Modifier.height(110.dp).clickable { onDeviceClick(dev) },
                    shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = colors.cardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Icon(Icons.Default.AcUnit, contentDescription = null, tint = if (dev.isPowerOn) colors.accent else colors.textMuted)
                            IconButton(onClick = { onDevicesUpdated(devices.filter { it.id != dev.id }) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                        Column {
                            Text(dev.room, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textMain)
                            Text(if (dev.isPowerOn) "${dev.targetTemp}°C • ${dev.mode.label}" else "OFF", fontSize = 12.sp, color = colors.textMuted)
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.height(110.dp).clickable { showAddDialog = true },
                    shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    border = CardDefaults.outlinedCardBorder(true)
                ) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Add AC", color = colors.textMuted, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column {
                    Text("Theme Style", fontWeight = FontWeight.Bold)
                    AppTheme.values().forEach { themeOpt ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            themeState.value = themeOpt
                            deviceManager.setTheme(themeOpt)
                        }.padding(vertical = 8.dp)) {
                            RadioButton(selected = themeState.value == themeOpt, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(themeOpt.label)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        scope.launch { checkGitHubUpdates(context) }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Update, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for Updates")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Close") } }
        )
    }

    if (showAddDialog) {
        var roomName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Room") },
            text = {
                OutlinedTextField(value = roomName, onValueChange = { roomName = it }, label = { Text("Room Name") })
            },
            confirmButton = {
                Button(onClick = {
                    if (roomName.isNotBlank()) {
                        onDevicesUpdated(devices + AirConditioner(UUID.randomUUID().toString(), "AC", roomName))
                        showAddDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

suspend fun checkGitHubUpdates(context: Context) {
    withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/Nex-Gen-Dev/PureClimate/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val latest = json.getString("tag_name").removePrefix("v")
                val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                if (latest > current) {
                    val htmlUrl = json.getString("html_url")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Update v$latest found! Opening browser...", Toast.LENGTH_LONG).show()
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)))
                    }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "App is up to date (v$current)", Toast.LENGTH_SHORT).show() }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Failed to check for updates.", Toast.LENGTH_SHORT).show() }
        }
    }
}

@Composable
fun RemoteScreen(ac: AirConditioner, colors: ThemeColors, hasIr: Boolean, onBack: () -> Unit, onUpdateAc: (AirConditioner, Boolean, Boolean) -> Unit) {
    fun update(block: (AirConditioner) -> Unit, triggerIr: Boolean = true, isSwing: Boolean = false) {
        val copy = ac.copy()
        block(copy)
        onUpdateAc(copy, triggerIr, isSwing)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textMain) }
            Text(ac.room.uppercase(), fontWeight = FontWeight.Bold, color = colors.textMain)
            Surface(shape = CircleShape, color = if (hasIr) Color(0xFF06D6A0) else Color(0xFFEF476F), modifier = Modifier.size(10.dp)) {}
        }
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().shadow(12.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = colors.cardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // LCD
                Card(
                    modifier = Modifier.fillMaxWidth().border(2.dp, colors.btnInactive, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = colors.lcdBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ac.mode.label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = colors.lcdText, fontSize = 14.sp)
                            Text(if(ac.isPowerOn) "ON" else "OFF", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = colors.lcdText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Top) {
                            Text("${ac.targetTemp}", fontSize = 64.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = colors.lcdText)
                            Text("°C", fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.lcdText, modifier = Modifier.padding(top = 8.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (ac.isSwingOn) "SWING" else "", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.lcdText)
                            Text("FAN: ${ac.fanSpeed.label}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.lcdText)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                // Power & Temp
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = { update({ it.isPowerOn = !it.isPowerOn }) }, shape = CircleShape, color = if (ac.isPowerOn) Color(0xFFE63946) else colors.btnActive, modifier = Modifier.size(72.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power", tint = Color.White, modifier = Modifier.size(36.dp)) }
                    }
                    Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = colors.btnInactive), modifier = Modifier.width(120.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { if (ac.targetTemp < 30) update({ it.targetTemp++ }) }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = colors.accent, modifier = Modifier.size(36.dp)) }
                            IconButton(onClick = { if (ac.targetTemp > 17) update({ it.targetTemp-- }) }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = colors.accent, modifier = Modifier.size(36.dp)) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                // Keypad
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteBtn("MODE", ac.mode.label, Icons.Default.Tune, colors, Modifier.weight(1f)) { update({ it.mode = MideaProtocol.Mode.values()[(it.mode.ordinal + 1) % 5] }) }
                    RemoteBtn("FAN", ac.fanSpeed.label, Icons.Default.Air, colors, Modifier.weight(1f)) { update({ it.fanSpeed = MideaProtocol.FanSpeed.values()[(it.fanSpeed.ordinal + 1) % 4] }) }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RemoteBtn("SWING", if(ac.isSwingOn) "ON" else "OFF", Icons.Default.SwapVert, colors, Modifier.weight(1f), ac.isSwingOn) { update({ it.isSwingOn = !it.isSwingOn }, triggerIr = true, isSwing = true) }
                    RemoteBtn("ECO", "24°C", Icons.Default.Eco, colors, Modifier.weight(1f)) { update({ it.targetTemp = 24; it.mode = MideaProtocol.Mode.COOL; it.fanSpeed = MideaProtocol.FanSpeed.AUTO }) }
                }
            }
        }
    }
}

@Composable
fun RemoteBtn(label: String, sub: String, icon: ImageVector, colors: ThemeColors, mod: Modifier, active: Boolean = false, onClick: () -> Unit) {
    Card(modifier = mod.height(64.dp).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if(active) colors.accent.copy(alpha=0.2f) else colors.btnInactive)) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textMuted)
                Text(sub, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = colors.textMain)
            }
        }
    }
}
