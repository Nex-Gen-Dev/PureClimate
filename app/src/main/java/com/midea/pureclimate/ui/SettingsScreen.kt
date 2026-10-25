package com.midea.pureclimate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midea.pureclimate.data.AppTheme

@Composable
fun SettingsScreen(
    theme: AppTheme,
    irOk: Boolean,
    protocolCount: Int,
    versionLabel: String,
    onTheme: (AppTheme) -> Unit,
    onOpenInfo: (InfoPage) -> Unit,
    onBack: () -> Unit,
    updates: @Composable () -> Unit = {},
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        ScreenBar("Settings", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            SectionLabel("Updates")
            Gap(10.dp)
            updates()

            Gap(24.dp)
            SectionLabel("Theme")
            Gap(10.dp)
            AppTheme.values().forEach { t ->
                val tp = paletteFor(t)
                val selected = t == theme
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape).background(p.surface)
                        .border(1.5.dp, if (selected) p.accent else p.surfaceBorder, shape)
                        .clickable { onTheme(t) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Mini preview: background, LCD and power button colours.
                    Box(
                        Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).background(tp.background)
                            .border(1.dp, tp.surfaceBorder, RoundedCornerShape(10.dp)).padding(6.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(tp.lcd).border(1.dp, tp.lcdBorder, RoundedCornerShape(4.dp))) {
                            Text("24", color = tp.lcdText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center))
                        }
                        Box(Modifier.align(Alignment.BottomStart).size(12.dp).clip(CircleShape).background(tp.powerOn))
                        Box(Modifier.align(Alignment.BottomEnd).size(width = 26.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(tp.button))
                    }
                    HGap(14.dp)
                    Text(t.label, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = p.accent)
                }
            }

            Gap(14.dp)
            SectionLabel("Your phone")
            Gap(10.dp)
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(irOk)
                        HGap(8.dp)
                        Text(if (irOk) "IR blaster found" else "No IR blaster found", color = p.text, fontSize = 15.sp)
                    }
                    Gap(6.dp)
                    Text(
                        if (irOk) "PureClimate sends its signals through your phone's built-in IR blaster."
                        else "This phone can't send IR signals. You can set up rooms, but the remote won't control anything.",
                        color = p.textMuted, fontSize = 13.sp, lineHeight = 17.sp,
                    )
                }
            }

            // About lives at the very bottom of the list.
            Gap(24.dp)
            SectionLabel("About")
            Gap(10.dp)
            val shape = RoundedCornerShape(16.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.surfaceBorder, shape)) {
                InfoPage.values().forEachIndexed { i, page ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(p.surfaceBorder))
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenInfo(page) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(page.title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(page.subtitle, color = p.textMuted, fontSize = 12.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = p.textMuted)
                    }
                }
            }
            Gap(16.dp)
            Text(
                "PureClimate · $versionLabel" + if (protocolCount > 0) " · $protocolCount AC protocols" else "",
                color = p.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Gap(24.dp)
        }
    }
}
