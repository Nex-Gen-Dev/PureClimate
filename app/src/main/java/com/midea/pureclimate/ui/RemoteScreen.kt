package com.midea.pureclimate.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.midea.pureclimate.data.LearnedButton
import com.midea.pureclimate.data.Room
import kotlinx.coroutines.delay

/** Converts a Dp measurement into a font size, so text scales with its slot. */
@Composable
private fun Dp.asSp(): TextUnit = with(LocalDensity.current) { this@asSp.toSp() }

@Composable
fun RemoteScreen(
    room: Room,
    protocolName: String?,
    irOk: Boolean,
    sendCount: Int,
    onBack: () -> Unit,
    onPower: () -> Unit,
    onTemp: (Int) -> Unit,
    onMode: () -> Unit,
    onFan: () -> Unit,
    onSwing: () -> Unit,
    onEco: () -> Unit,
    onLearned: (LearnedButton) -> Unit,
    onChangeType: (SetupMethod) -> Unit,
) {
    val p = LocalPalette.current
    var showType by remember { mutableStateOf(false) }

    // Flash the IR dot for a moment after each transmission.
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(sendCount) {
        if (sendCount > 0) {
            flash = true
            delay(160)
            flash = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenBar(room.name, onBack) {
            Box(contentAlignment = Alignment.Center) {
                StatusDot(irOk)
                if (flash) Box(Modifier.size(18.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.55f)))
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            if (room.isLearnedOnly) {
                LearnedRemote(room, onLearned, onChangeType = { showType = true })
            } else {
                Lcd(
                    room = room,
                    protocolName = protocolName,
                    onTypeClick = { showType = true },
                    modifier = Modifier.weight(0.38f).fillMaxWidth(),
                )
                Spacer(Modifier.weight(0.04f))
                PowerAndRocker(room, onPower, onTemp, Modifier.weight(0.30f).fillMaxWidth())
                Spacer(Modifier.weight(0.04f))
                Keypad(room, onMode, onFan, onSwing, onEco, Modifier.weight(0.24f).fillMaxWidth())
            }
        }
    }

    if (showType) {
        AlertDialog(
            onDismissRequest = { showType = false },
            containerColor = p.surface,
            titleContentColor = p.text,
            title = { Text("AC type") },
            text = {
                Column {
                    Text(
                        "Currently: ${protocolName ?: if (room.isLearnedOnly) "Learned buttons" else "loading…"}",
                        color = p.textMuted, fontSize = 13.sp,
                    )
                    Gap(10.dp)
                    SetupMethod.values().forEach { m ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { showType = false; onChangeType(m) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(m.title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(m.subtitle, color = p.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showType = false }) { Text("Close", color = p.accent) } },
        )
    }
}

// ---- LCD -----------------------------------------------------------------------

@Composable
private fun Lcd(room: Room, protocolName: String?, onTypeClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(22.dp)
    val on = room.power
    val ink = if (on) p.lcdText else p.lcdText.copy(alpha = 0.45f)
    BoxWithConstraints(
        modifier.clip(shape).background(p.lcd).border(2.dp, p.lcdBorder, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        val h = maxHeight
        val w = maxWidth
        val small = min(h * 0.085f, 15.dp).asSp()
        val big = min(h * 0.50f, w * 0.34f).asSp()
        val mono = FontFamily.Monospace
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MODE ${room.mode.label}", color = ink, fontFamily = mono, fontSize = small, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(if (on) "● ON" else "○ OFF", color = ink, fontFamily = mono, fontSize = small, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(contentAlignment = Alignment.CenterEnd) {
                        // Unlit LCD segments, shown only while the AC is off so the
                        // live reading has nothing behind it.
                        if (!on) {
                            Text("88", color = p.lcdTextDim, fontFamily = mono, fontSize = big, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (room.mode.name == "FAN") "--" else room.temp.toString(),
                            color = ink, fontFamily = mono, fontSize = big, fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "°C", color = ink, fontFamily = mono, fontWeight = FontWeight.Bold,
                        fontSize = (h * 0.13f).asSp(), modifier = Modifier.padding(top = h * 0.07f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LcdBadge("SWING", lit = room.swing && on, size = small)
                HGap(8.dp)
                LcdBadge("FAN: ${room.fan.label}", lit = on, size = small)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onTypeClick)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Change AC type", tint = p.lcdText.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    HGap(3.dp)
                    Text(
                        (protocolName ?: "LOADING").substringBefore(" (").uppercase(),
                        color = p.lcdText.copy(alpha = 0.7f), fontFamily = mono,
                        fontSize = (small.value * 0.8f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(w * 0.34f), textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun LcdBadge(text: String, lit: Boolean, size: TextUnit) {
    val p = LocalPalette.current
    val c = if (lit) p.lcdText else p.lcdText.copy(alpha = 0.28f)
    Text(
        text,
        color = c,
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.border(1.5.dp, c, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

// ---- Power + temperature rocker -------------------------------------------------

@Composable
private fun PowerAndRocker(room: Room, onPower: () -> Unit, onTemp: (Int) -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    BoxWithConstraints(modifier) {
        val h = maxHeight
        val powerSize = min(h * 0.92f, maxWidth * 0.40f)
        val rockerW = min(h * 0.46f, maxWidth * 0.26f)
        val powerColor by animateColorAsState(if (room.power) p.powerOn else p.powerOff, tween(180), label = "power")
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RemoteKey(
                    onClick = onPower,
                    modifier = Modifier.size(powerSize * 0.86f),
                    shape = CircleShape,
                    color = powerColor,
                    borderColor = if (room.power) p.powerOn else p.buttonBorder,
                ) {
                    Icon(
                        Icons.Rounded.PowerSettingsNew,
                        contentDescription = if (room.power) "Turn off" else "Turn on",
                        tint = if (room.power) Color.White else p.text,
                        modifier = Modifier.size(powerSize * 0.38f),
                    )
                }
                Text("POWER", color = p.textMuted, fontSize = (powerSize * 0.075f).asSp(), letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
            // Vertical pill rocker: up half / down half.
            val pill = RoundedCornerShape(rockerW / 2)
            Column(
                Modifier.width(rockerW).fillMaxHeight(0.96f).clip(pill).background(p.button).border(1.dp, p.buttonBorder, pill),
            ) {
                RemoteKey(
                    onClick = { onTemp(+1) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = rockerW / 2, topEnd = rockerW / 2),
                    borderColor = Color.Transparent,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Temperature up", tint = p.text, modifier = Modifier.size(rockerW * 0.62f))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = rockerW * 0.2f).background(p.buttonBorder))
                Text("TEMP", color = p.textMuted, fontSize = (rockerW * 0.15f).asSp(), letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), textAlign = TextAlign.Center)
                Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = rockerW * 0.2f).background(p.buttonBorder))
                RemoteKey(
                    onClick = { onTemp(-1) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = rockerW / 2, bottomEnd = rockerW / 2),
                    borderColor = Color.Transparent,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Temperature down", tint = p.text, modifier = Modifier.size(rockerW * 0.62f))
                }
            }
        }
    }
}

// ---- Keypad -----------------------------------------------------------------------

@Composable
private fun Keypad(
    room: Room,
    onMode: () -> Unit,
    onFan: () -> Unit,
    onSwing: () -> Unit,
    onEco: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeypadKey("MODE", room.mode.label, Icons.Rounded.Tune, onMode, Modifier.weight(1f))
            KeypadKey("FAN", room.fan.label, Icons.Rounded.Air, onFan, Modifier.weight(1f))
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeypadKey("SWING", if (room.swing) "ON" else "OFF", Icons.Rounded.SwapVert, onSwing, Modifier.weight(1f))
            KeypadKey("ECO", "24° COOL", Icons.Rounded.Eco, onEco, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeypadKey(label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    RemoteKey(onClick = onClick, modifier = modifier.fillMaxHeight()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            val h = maxHeight
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = p.accent, modifier = Modifier.size(min(h * 0.42f, 28.dp)))
                HGap(10.dp)
                Column {
                    Text(label, color = p.text, fontWeight = FontWeight.Bold, fontSize = min(h * 0.24f, 17.dp).asSp(), letterSpacing = 1.sp, maxLines = 1)
                    Text(value, color = p.textMuted, fontFamily = FontFamily.Monospace, fontSize = min(h * 0.19f, 13.dp).asSp(), maxLines = 1)
                }
            }
        }
    }
}

// ---- Learned-only remote ----------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LearnedRemote(
    room: Room,
    onPress: (LearnedButton) -> Unit,
    onChangeType: () -> Unit,
) {
    val p = LocalPalette.current
    var last by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        Modifier.weight(0.24f).fillMaxWidth().clip(shape).background(p.lcd).border(2.dp, p.lcdBorder, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        val h = maxHeight
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LEARNED REMOTE", color = p.lcdText, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = min(h * 0.14f, 15.dp).asSp())
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.Tune, contentDescription = "Change AC type", tint = p.lcdText,
                    modifier = Modifier.size(22.dp).clickable(onClick = onChangeType))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Text(
                    last ?: "${room.learned.size} BUTTONS",
                    color = p.lcdText, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = min(h * 0.34f, 40.dp).asSp(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Spacer(Modifier.weight(0.03f))
    val buttons = room.learned
    val cols = if (buttons.size <= 4) 2 else 3
    val rows = buttons.chunked(cols)
    Column(Modifier.weight(0.73f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (buttons.isEmpty()) {
            Text("No learned buttons yet. Tap the tune icon to learn from your remote.", color = p.textMuted, fontSize = 14.sp)
        }
        rows.forEach { row ->
            Row(
                Modifier.weight(1f, fill = false).heightIn(min = 44.dp, max = 92.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { b ->
                    RemoteKey(onClick = { last = b.label.uppercase(); onPress(b) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text(b.label, color = p.text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, modifier = Modifier.padding(6.dp))
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
