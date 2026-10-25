package com.midea.pureclimate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midea.pureclimate.data.AcMode
import com.midea.pureclimate.data.FanSpeed
import com.midea.pureclimate.data.LearnedButton
import com.midea.pureclimate.ir.Codebook
import com.midea.pureclimate.ir.SignalMatcher
import com.midea.pureclimate.ir.UsbIrReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Human description of a codebook state index, e.g. "COOL 24°C · FAN AUTO · SWING". */
fun describeState(index: Int): String {
    if (index == 0) return "POWER OFF"
    var i = index - 1
    val swing = i % 2; i /= 2
    val fan = i % 4; i /= 4
    val temp = i % 14 + Codebook.MIN_TEMP; i /= 14
    val mode = AcMode.values()[i.coerceIn(0, 4)]
    val t = if (mode == AcMode.FAN) "" else " $temp°C"
    return "${mode.label}$t · FAN ${FanSpeed.values()[fan].label}" + if (swing == 1) " · SWING" else ""
}

private data class Capture(val id: Int, val pattern: IntArray, val matches: List<SignalMatcher.Match>)

private val QUICK_LABELS = listOf("Power ON", "Power OFF", "Cool 22°", "Cool 24°", "Heat 26°", "Fan", "Swing", "Turbo")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearnScreen(
    roomName: String,
    codebook: Codebook?,
    irOk: Boolean,
    onSendRaw: (IntArray, Int) -> Unit,
    onUseProtocol: (Codebook.Variant) -> Unit,
    onSaveButtons: (List<LearnedButton>) -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val receiver = remember { UsbIrReceiver(context) }
    val status by receiver.status.collectAsState()
    val detail by receiver.detail.collectAsState()
    var captures by remember { mutableStateOf(listOf<Capture>()) }
    var buttons by remember { mutableStateOf(listOf<LearnedButton>()) }
    var showHelp by remember { mutableStateOf(false) }
    var nextId by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        receiver.connect()
        onDispose { receiver.release() }
    }
    LaunchedEffect(codebook) {
        receiver.captures.collect { pattern ->
            val matches = withContext(Dispatchers.Default) {
                codebook?.let { SignalMatcher.identify(it, pattern, 3) } ?: emptyList()
            }
            captures = (listOf(Capture(nextId++, pattern, matches)) + captures).take(6)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenBar("Learn remote", onBack) { StatusDot(status == UsbIrReceiver.Status.CONNECTED) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            // ---- receiver connection ----
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Usb, contentDescription = null, tint = p.accent)
                        HGap(8.dp)
                        Text(
                            when (status) {
                                UsbIrReceiver.Status.CONNECTED -> "Receiver connected"
                                UsbIrReceiver.Status.CONNECTING -> "Connecting…"
                                UsbIrReceiver.Status.NO_PERMISSION -> "Waiting for USB permission"
                                UsbIrReceiver.Status.ERROR -> "Receiver error"
                                UsbIrReceiver.Status.DISCONNECTED -> "No receiver"
                            },
                            color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        )
                    }
                    Gap(4.dp)
                    Text(detail, color = p.textMuted, fontSize = 13.sp, lineHeight = 17.sp)
                    if (status != UsbIrReceiver.Status.CONNECTED) {
                        Gap(12.dp)
                        PrimaryButton("Connect", onClick = { receiver.connect() })
                    }
                }
            }
            Gap(10.dp)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showHelp = !showHelp }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Why a receiver, and how to build one", color = p.accent, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(if (showHelp) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = p.accent)
            }
            if (showHelp) {
                Text(
                    "Phone IR blasters can only send, not receive, so recording a remote needs a small receiver. " +
                        "Total cost is about \$5.",
                    color = p.textMuted, fontSize = 13.sp, lineHeight = 17.sp,
                )
                Gap(8.dp)
                StepList(
                    listOf(
                        "Get an ESP32 dev board and a 38 kHz IR receiver module (VS1838B or TSOP38238).",
                        "Wire the receiver: OUT → GPIO 14, VCC → 3V3, GND → GND.",
                        "Flash the PureClimate receiver sketch (firmware/esp32-ir-receiver in the GitHub repo).",
                        "Plug the ESP32 into the phone with a USB OTG adapter and tap Connect.",
                    ),
                )
                Gap(8.dp)
            }
            Gap(10.dp)
            Text(
                "Point the original remote at the receiver from about 10 cm and press a button. " +
                    "Start with POWER. If PureClimate recognises the signal, you get full control. Otherwise, save each button you need.",
                color = p.textMuted, fontSize = 14.sp, lineHeight = 19.sp,
            )
            Gap(12.dp)

            if (captures.isEmpty()) {
                Panel(Modifier.fillMaxWidth()) {
                    Text(
                        if (status == UsbIrReceiver.Status.CONNECTED) "Listening… press a button on your remote." else "Connect the receiver to start.",
                        color = p.textMuted, fontSize = 14.sp,
                    )
                }
            }
            captures.forEachIndexed { i, cap ->
                CaptureCard(
                    cap = cap,
                    latest = i == 0,
                    onTestProtocol = { m -> onSendRaw(m.variant.sequence(m.sequence), m.variant.carrierHz) },
                    onUseProtocol = onUseProtocol,
                    onReplay = { onSendRaw(cap.pattern, 38000) },
                    onAddButton = { label ->
                        buttons = buttons.filterNot { it.label.equals(label, ignoreCase = true) } +
                            LearnedButton(label, cap.pattern, 38000)
                        captures = captures.filterNot { it.id == cap.id }
                    },
                )
                Gap(10.dp)
            }

            if (buttons.isNotEmpty()) {
                Gap(6.dp)
                SectionLabel("Learned buttons (${buttons.size})")
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { b ->
                        Row(
                            Modifier.clip(RoundedCornerShape(20.dp)).background(p.button).border(1.dp, p.buttonBorder, RoundedCornerShape(20.dp))
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(b.label, color = p.text, fontSize = 13.sp, modifier = Modifier.clickable { onSendRaw(b.pattern, b.carrierHz) })
                            Icon(
                                Icons.Rounded.Close, contentDescription = "Remove ${b.label}", tint = p.textMuted,
                                modifier = Modifier.size(26.dp).padding(4.dp).clickable { buttons = buttons - b },
                            )
                        }
                    }
                }
                Gap(14.dp)
                PrimaryButton("Save ${buttons.size} buttons to $roomName", onClick = { onSaveButtons(buttons) })
                Gap(16.dp)
            }
            if (!irOk) {
                Text("Note: this phone has no IR emitter, so it can record but not send.", color = p.bad, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureCard(
    cap: Capture,
    latest: Boolean,
    onTestProtocol: (SignalMatcher.Match) -> Unit,
    onUseProtocol: (Codebook.Variant) -> Unit,
    onReplay: () -> Unit,
    onAddButton: (String) -> Unit,
) {
    val p = LocalPalette.current
    val top = cap.matches.firstOrNull()
    var label by remember(cap.id) { mutableStateOf("") }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(p.surface)
            .border(1.dp, if (latest) p.accent.copy(alpha = 0.7f) else p.surfaceBorder, shape)
            .padding(14.dp),
    ) {
        Text("Signal · ${cap.pattern.size} pulses", color = p.textMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Gap(6.dp)
        when {
            top != null && top.isConfident -> {
                Text("Identified: ${top.variant.name}", color = p.ok, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                val st = top.states()
                Text(
                    when {
                        top.isSwingToggle -> "Swing toggle command"
                        st.isNotEmpty() -> describeState(st.first())
                        else -> ""
                    },
                    color = p.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                )
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Test on AC", onClick = { onTestProtocol(top) }, modifier = Modifier.weight(1f))
                    PrimaryButton("Use full control", onClick = { onUseProtocol(top.variant) }, modifier = Modifier.weight(1f))
                }
            }
            top != null && top.score >= 0.75 -> {
                Text("Close matches. Test to confirm:", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Gap(8.dp)
                cap.matches.filter { it.score >= 0.75 }.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.variant.name, color = p.text, fontSize = 14.sp)
                            Text("${(m.score * 100).toInt()}% match", color = p.textMuted, fontSize = 11.sp)
                        }
                        SecondaryButton("Test", onClick = { onTestProtocol(m) }, modifier = Modifier.size(width = 72.dp, height = 40.dp))
                        HGap(6.dp)
                        PrimaryButton("Use", onClick = { onUseProtocol(m.variant) }, modifier = Modifier.size(width = 64.dp, height = 40.dp))
                    }
                }
                Gap(6.dp)
                Text("Or save the raw signal as a button:", color = p.textMuted, fontSize = 12.sp)
                Gap(6.dp)
                RawSaveRow(label, { label = it }, onReplay, onAddButton)
            }
            else -> {
                Text("Unknown protocol", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Save it as a button and PureClimate will replay it exactly.", color = p.textMuted, fontSize = 13.sp)
                Gap(8.dp)
                RawSaveRow(label, { label = it }, onReplay, onAddButton)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RawSaveRow(label: String, onLabel: (String) -> Unit, onReplay: () -> Unit, onAdd: (String) -> Unit) {
    val p = LocalPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        QUICK_LABELS.forEach { q ->
            Text(
                q,
                color = if (label == q) p.onAccent else p.text,
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (label == q) p.accent else p.button)
                    .clickable { onLabel(q) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    Gap(8.dp)
    OutlinedTextField(
        value = label,
        onValueChange = { onLabel(it.take(18)) },
        singleLine = true,
        placeholder = { Text("Button name", color = p.textMuted) },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = p.text, unfocusedTextColor = p.text,
            focusedBorderColor = p.accent, unfocusedBorderColor = p.buttonBorder, cursorColor = p.accent,
        ),
    )
    Gap(8.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Replay", onClick = onReplay, modifier = Modifier.weight(1f))
        PrimaryButton("Add button", onClick = { onAdd(label.trim()) }, modifier = Modifier.weight(1f), enabled = label.isNotBlank())
    }
}
