package com.midea.pureclimate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midea.pureclimate.data.AcMode
import com.midea.pureclimate.data.FanSpeed
import com.midea.pureclimate.ir.Codebook
import kotlinx.coroutines.delay

/** Test frame: power on, COOL, 24°C, AUTO fan. Most ACs beep and start when they understand it. */
fun Codebook.Variant.testOn(): IntArray = state(AcMode.COOL.ordinal, 24, FanSpeed.AUTO.ordinal, false)

@Composable
fun SearchField(query: String, onChange: (String) -> Unit, hint: String) {
    val p = LocalPalette.current
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = p.textMuted) },
        placeholder = { Text(hint, color = p.textMuted) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = p.text, unfocusedTextColor = p.text,
            focusedBorderColor = p.accent, unfocusedBorderColor = p.buttonBorder, cursorColor = p.accent,
        ),
    )
}

private enum class Phase { TESTING, PICK_RECENT, CONFIRM_OFF }

/**
 * "Find my AC": walks through the protocols (optionally narrowed by brand),
 * sending a power-on test from each until the user says the AC responded.
 */
@Composable
fun ScanScreen(
    roomName: String,
    codebook: Codebook?,
    irOk: Boolean,
    onSend: (Codebook.Variant, IntArray) -> Unit,
    onFound: (Codebook.Variant) -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    var index by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.TESTING) }
    var auto by remember { mutableStateOf(false) }
    var sentCurrent by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf(listOf<Codebook.Variant>()) }
    var chosen by remember { mutableStateOf<Codebook.Variant?>(null) }

    val list = remember(codebook, query) { codebook?.search(query) ?: emptyList() }
    val current = list.getOrNull(index)

    fun sendTest(v: Codebook.Variant) {
        onSend(v, v.testOn())
        recent = (listOf(v) + recent.filter { it !== v }).take(3)
        sentCurrent = true
    }

    // Auto-scan: one protocol every 3 seconds until stopped.
    LaunchedEffect(auto, list) {
        while (auto && phase == Phase.TESTING) {
            val v = list.getOrNull(index) ?: run { auto = false; return@LaunchedEffect }
            sendTest(v)
            delay(3000)
            if (!auto) break
            if (index < list.lastIndex) { index++; sentCurrent = false } else auto = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenBar("Find my AC", onBack) { StatusDot(irOk) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (codebook == null) {
                Text("Loading AC codes…", color = p.textMuted)
                return@Column
            }
            when (phase) {
                Phase.TESTING -> {
                    Text(
                        "Stand near the AC for “$roomName”, point the phone's IR window at it, and send each test. " +
                            "When the AC beeps or switches on, tap “It responded”.",
                        color = p.textMuted, fontSize = 14.sp, lineHeight = 19.sp,
                    )
                    Gap(14.dp)
                    SearchField(query, { query = it; index = 0; sentCurrent = false; auto = false }, "Brand (optional), e.g. Midea, Electra")
                    Gap(14.dp)
                    if (list.isEmpty()) {
                        Text("No protocol matches “$query”. Clear the search to test all ${codebook.variants().size}.", color = p.textMuted)
                        return@Column
                    }
                    Panel(Modifier.fillMaxWidth()) {
                        Column {
                            SectionLabel("Test ${index + 1} of ${list.size}")
                            Gap(8.dp)
                            LinearProgressIndicator(
                                progress = { (index + 1f) / list.size },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = p.accent, trackColor = p.button,
                            )
                            Gap(14.dp)
                            Text(current?.name ?: "", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            val brands = current?.brands.orEmpty()
                            if (brands.isNotBlank()) {
                                Gap(4.dp)
                                Text(brands.replace(",", ", "), color = p.textMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Gap(16.dp)
                            PrimaryButton(if (sentCurrent) "Send again" else "Send test signal", onClick = { current?.let { sendTest(it) } }, enabled = !auto)
                        }
                    }
                    Gap(12.dp)
                    PrimaryButton("It responded", onClick = {
                        auto = false
                        if (recent.size > 1) phase = Phase.PICK_RECENT
                        else { chosen = current; phase = Phase.CONFIRM_OFF }
                    }, enabled = sentCurrent || recent.isNotEmpty())
                    Gap(10.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("◀ Back", onClick = { if (index > 0) { index--; sentCurrent = false } },
                            modifier = Modifier.weight(1f), enabled = index > 0 && !auto)
                        SecondaryButton("No response ▶", onClick = { if (index < list.lastIndex) { index++; sentCurrent = false } },
                            modifier = Modifier.weight(1f), enabled = index < list.lastIndex && !auto)
                    }
                    Gap(10.dp)
                    SecondaryButton(if (auto) "Stop auto-scan" else "Auto-scan (one every 3 s)", onClick = { auto = !auto })
                    Gap(14.dp)
                    Text(
                        "Tip: Midea units use one of the first two. If nothing responds, the AC may be off at the wall, " +
                            "or out of range (IR reaches about 5–8 m).",
                        color = p.textMuted, fontSize = 12.sp, lineHeight = 16.sp,
                    )
                }

                Phase.PICK_RECENT -> {
                    Text("Which one made it respond?", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Gap(6.dp)
                    Text(
                        "The AC can react a moment after the signal, so test the last few again and pick the one that works.",
                        color = p.textMuted, fontSize = 14.sp, lineHeight = 19.sp,
                    )
                    Gap(14.dp)
                    recent.forEach { v ->
                        Panel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                            Column {
                                Text(v.name, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Gap(10.dp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SecondaryButton("Test", onClick = { onSend(v, v.testOn()) }, modifier = Modifier.weight(1f))
                                    PrimaryButton("This one", onClick = { chosen = v; phase = Phase.CONFIRM_OFF }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    SecondaryButton("Keep scanning", onClick = { phase = Phase.TESTING })
                }

                Phase.CONFIRM_OFF -> {
                    val v = chosen ?: current
                    Text("Confirm: ${v?.name}", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Gap(6.dp)
                    Text(
                        "Tap Turn OFF. If the AC switches off, this is your protocol and you'll get full control: mode, temperature, fan and swing.",
                        color = p.textMuted, fontSize = 14.sp, lineHeight = 19.sp,
                    )
                    Gap(16.dp)
                    SecondaryButton("Turn OFF", onClick = { v?.let { onSend(it, it.powerOff()) } })
                    Gap(10.dp)
                    SecondaryButton("Turn ON (Cool 24°C)", onClick = { v?.let { onSend(it, it.testOn()) } })
                    Gap(16.dp)
                    PrimaryButton("It works. Save", onClick = { v?.let(onFound) })
                    Gap(10.dp)
                    SecondaryButton("Didn't work, keep scanning", onClick = {
                        phase = Phase.TESTING
                        val i = v?.let { list.indexOf(it) } ?: -1
                        if (i >= 0 && i < list.lastIndex) index = i + 1
                        sentCurrent = false
                    })
                }
            }
        }
    }
}

/** Pick a protocol by brand or name, test it, use it. */
@Composable
fun BrandScreen(
    codebook: Codebook?,
    currentId: String?,
    irOk: Boolean,
    onSend: (Codebook.Variant, IntArray) -> Unit,
    onPick: (Codebook.Variant) -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    val list = remember(codebook, query) { codebook?.search(query) ?: emptyList() }

    Column(Modifier.fillMaxSize()) {
        ScreenBar("Choose brand", onBack) { StatusDot(irOk) }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            SearchField(query, { query = it }, "Search brand or protocol")
        }
        if (codebook == null) {
            Text("Loading AC codes…", color = p.textMuted, modifier = Modifier.padding(20.dp))
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.id }) { v ->
                val expanded = open == v.id
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(p.surface)
                        .border(1.dp, if (expanded) p.accent else p.surfaceBorder, shape)
                        .clickable { open = if (expanded) null else v.id }
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            if (v.brands.isNotBlank()) {
                                Text(v.brands.replace(",", ", "), color = p.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (v.id == currentId) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Current", tint = p.accent, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = p.textMuted)
                        }
                    }
                    if (expanded) {
                        Gap(12.dp)
                        Text(v.id, color = p.textMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Gap(10.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("Test ON", onClick = { onSend(v, v.testOn()) }, modifier = Modifier.weight(1f))
                            SecondaryButton("Test OFF", onClick = { onSend(v, v.powerOff()) }, modifier = Modifier.weight(1f))
                        }
                        Gap(8.dp)
                        PrimaryButton("Use this", onClick = { onPick(v) })
                    }
                }
            }
        }
    }
}
