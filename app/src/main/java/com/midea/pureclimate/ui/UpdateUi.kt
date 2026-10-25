package com.midea.pureclimate.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midea.pureclimate.update.ReleaseInfo
import com.midea.pureclimate.update.UpdateManager
import com.midea.pureclimate.update.UpdateState

/** Starts the install, sending the user to "Install unknown apps" first if needed. */
fun startInstall(context: Context, updates: UpdateManager, state: UpdateState.ReadyToInstall) {
    if (updates.canInstall()) updates.install(context, state.file) else updates.openInstallPermission(context)
}

private fun sizeMb(bytes: Long) = if (bytes > 0) String.format("%.1f MB", bytes / 1_048_576.0) else ""

/**
 * Popup shown when a newer version exists: Install now / Later. While it
 * downloads it shows progress, then opens Android's installer by itself.
 */
@Composable
fun UpdatePrompt(
    state: UpdateState,
    updates: UpdateManager,
    context: Context,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    val release: ReleaseInfo = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.ReadyToInstall -> state.release
        else -> return
    }
    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) onDismiss() },
        containerColor = p.surface,
        titleContentColor = p.text,
        title = { Text("New version available") },
        text = {
            Column {
                Text(
                    "${release.label} is ready. You have ${updates.currentLabel}.",
                    color = p.text, fontSize = 15.sp, lineHeight = 20.sp,
                )
                if (release.notes.isNotBlank()) {
                    Gap(12.dp)
                    SectionLabel("What's new")
                    Gap(4.dp)
                    Text(
                        release.notes,
                        color = p.textMuted, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when (state) {
                    is UpdateState.Downloading -> {
                        Gap(14.dp)
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = p.accent, trackColor = p.button,
                        )
                        Gap(6.dp)
                        Text("Downloading… ${(state.progress * 100).toInt()}%", color = p.textMuted, fontSize = 12.sp)
                    }
                    is UpdateState.ReadyToInstall -> if (!updates.canInstall()) {
                        Gap(12.dp)
                        Text(
                            "Android will ask you to allow PureClimate to install updates. Turn it on, come back, and tap Install again.",
                            color = p.textMuted, fontSize = 12.sp, lineHeight = 16.sp,
                        )
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { updates.download(release) }) {
                    Text("Install now", color = p.accent, fontWeight = FontWeight.SemiBold)
                }
                is UpdateState.ReadyToInstall -> TextButton(onClick = { startInstall(context, updates, state) }) {
                    Text("Install", color = p.accent, fontWeight = FontWeight.SemiBold)
                }
                else -> Unit
            }
        },
        dismissButton = {
            if (state !is UpdateState.Downloading) {
                TextButton(onClick = onDismiss) { Text("Later", color = p.textMuted) }
            }
        },
    )
}

/** The "Updates" section in Settings. Written for everyday users: no build numbers. */
@Composable
fun UpdatesSection(state: UpdateState, updates: UpdateManager, context: Context) {
    val p = LocalPalette.current
    var auto by remember { mutableStateOf(updates.autoCheck) }
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text("PureClimate", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(updates.currentLabel, color = p.textMuted, fontSize = 14.sp)
            Gap(14.dp)

            // Status line
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    is UpdateState.UpToDate -> {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = p.ok, modifier = Modifier.size(20.dp))
                        HGap(8.dp)
                        Text("You're up to date", color = p.text, fontSize = 15.sp)
                    }
                    is UpdateState.Available -> {
                        Text("${state.release.label} is available", color = p.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    is UpdateState.ReadyToInstall -> {
                        Text("${state.release.label} is ready to install", color = p.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    is UpdateState.Downloading -> {
                        Text("Downloading ${state.release.label}… ${(state.progress * 100).toInt()}%", color = p.text, fontSize = 15.sp)
                    }
                    is UpdateState.Checking -> Text("Checking for updates…", color = p.textMuted, fontSize = 15.sp)
                    is UpdateState.Failed -> {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = p.textMuted, modifier = Modifier.size(20.dp))
                        HGap(8.dp)
                        Text(state.message, color = p.textMuted, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                    }
                    UpdateState.Idle -> Text("Tap below to check for a new version.", color = p.textMuted, fontSize = 15.sp)
                }
            }

            if (state is UpdateState.Downloading) {
                Gap(10.dp)
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = p.accent, trackColor = p.button,
                )
            }

            val notes = when (state) {
                is UpdateState.Available -> state.release.notes
                is UpdateState.ReadyToInstall -> state.release.notes
                else -> ""
            }
            if (notes.isNotBlank()) {
                Gap(12.dp)
                SectionLabel("What's new")
                Gap(4.dp)
                Text(notes, color = p.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
            }

            Gap(14.dp)
            when (state) {
                is UpdateState.Available -> PrimaryButton(
                    "Update to ${state.release.label}",
                    onClick = { updates.download(state.release) },
                )
                is UpdateState.ReadyToInstall -> PrimaryButton(
                    "Install ${state.release.label}",
                    onClick = { startInstall(context, updates, state) },
                )
                else -> SecondaryButton(
                    if (state is UpdateState.Checking) "Checking…" else "Check for updates",
                    onClick = { updates.check() },
                    enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                )
            }

            // Secondary details: small and quiet.
            Gap(12.dp)
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.surfaceBorder))
            Gap(8.dp)
            if (updates.lastCheckMillis > 0) {
                val whenText = DateUtils.formatDateTime(
                    context, updates.lastCheckMillis,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                )
                Text("Last checked: $whenText", color = p.textMuted, fontSize = 12.sp)
            } else {
                Text("Not checked yet", color = p.textMuted, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Check automatically", color = p.textMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = auto,
                    onCheckedChange = { auto = it; updates.autoCheck = it },
                    modifier = Modifier.scale(0.75f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = p.onAccent,
                        checkedTrackColor = p.accent,
                        uncheckedThumbColor = p.textMuted,
                        uncheckedTrackColor = p.button,
                        uncheckedBorderColor = p.buttonBorder,
                    ),
                )
            }
        }
    }
}
