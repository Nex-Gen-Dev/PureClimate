package com.midea.pureclimate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Top bar used on every inner screen: back, centered uppercase title, optional trailing slot. */
@Composable
fun ScreenBar(
    title: String,
    onBack: (() -> Unit)?,
    trailing: @Composable () -> Unit = { Spacer(Modifier.size(48.dp)) },
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = p.text)
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Text(
            title.uppercase(),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = p.text,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing() }
    }
}

/** Small status dot: green when IR hardware is present, red when not. */
@Composable
fun StatusDot(ok: Boolean, size: Dp = 10.dp) {
    val p = LocalPalette.current
    val c = if (ok) p.ok else p.bad
    Box(
        Modifier.size(size + 8.dp).clip(CircleShape).background(c.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).clip(CircleShape).background(c))
    }
}

/**
 * Physical-feeling key: slight press-in scale, haptic tick, themed border.
 * Content gets the full size so callers can scale their text to the key.
 */
@Composable
fun RemoteKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    color: Color? = null,
    borderColor: Color? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = color ?: if (pressed) p.buttonPressed else p.button
    Box(
        modifier
            .scale(if (pressed) 0.96f else 1f)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor ?: p.buttonBorder, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A themed rectangular card. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.clip(shape).background(p.surface).border(1.dp, p.surfaceBorder, shape).padding(16.dp),
    ) { content() }
}

/** Full-width primary action button. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalPalette.current
    RemoteKey(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) p.accent else p.button,
        borderColor = if (enabled) p.accent else p.buttonBorder,
        enabled = enabled,
    ) {
        Text(
            text,
            color = if (enabled) p.onAccent else p.textMuted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
}

/** Full-width secondary action button. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalPalette.current
    RemoteKey(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        enabled = enabled,
    ) {
        Text(text, color = if (enabled) p.text else p.textMuted, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(
        text.uppercase(),
        modifier = modifier,
        color = p.textMuted,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun StepList(steps: List<String>) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(p.button)
                        .border(1.dp, p.buttonBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                HGap(10.dp)
                Text(s, color = p.text, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
    }
}
