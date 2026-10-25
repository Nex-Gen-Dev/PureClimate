package com.midea.pureclimate.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midea.pureclimate.update.ReleaseInfo
import com.midea.pureclimate.update.UpdateManager
import com.midea.pureclimate.update.UpdateState
import java.text.DateFormat
import java.util.Date

/** Starts the install, sending the user to "Install unknown apps" first if needed. */
fun startInstall(context: Context, updates: UpdateManager, state: UpdateState.ReadyToInstall) {
    if (updates.canInstall()) updates.install(context, state.file) else updates.openInstallPermission(context)
}

private fun sizeMb(bytes: Long) = if (bytes > 0) String.format("%.1f MB", bytes / 1_048_576.0) else ""

/**
 * Popup shown when a newer build exists: Install now / Later. While it
 * downloads it shows progress, then opens Android's installer.
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
        title = { Text("Update available") },
        text = {
            Column {
                Text(
                    "${release.versionName} is ready (you have ${updates.currentVersionName}).",
                    color = p.text, fontSize = 15.sp,
                )
                if (release.notes.isNotBlank()) {
                    Gap(10.dp)
                    Text(
                        release.notes,
                        color = p.textMuted, fontSize = 13.sp, lineHeight = 17.sp,
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
                            "Android will ask you to allow PureClimate to install apps. Turn it on, come back, and tap Install again.",
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
                    Text("Install now ${sizeMb(release.apkSize)}", color = p.accent, fontWeight = FontWeight.SemiBold)
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

/** The "Updates" section in Settings. */
@Composable
fun UpdatesSection(state: UpdateState, updates: UpdateManager, context: Context) {
    val p = LocalPalette.current
    var auto by remember { mutableStateOf(updates.autoCheck) }
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Installed: ${updates.currentVersionName}", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Build ${updates.currentBuild}", color = p.textMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Gap(12.dp)
            val status = when (state) {
                UpdateState.Idle -> "Not checked yet."
                UpdateState.Checking -> "Checking GitHub for a new build…"
                is UpdateState.UpToDate -> "You're on the latest build."
                is UpdateState.Available -> "New: ${state.release.versionName}"
                is UpdateState.Downloading -> "Downloading ${state.release.versionName}… ${(state.progress * 100).toInt()}%"
                is UpdateState.ReadyToInstall -> "${state.release.versionName} downloaded, ready to install."
                is UpdateState.Failed -> state.message
            }
            Text(
                status,
                color = when (state) {
                    is UpdateState.Failed -> p.bad
                    is UpdateState.Available, is UpdateState.ReadyToInstall -> p.accent
                    else -> p.textMuted
                },
                fontSize = 14.sp, lineHeight = 18.sp,
            )
            if (updates.lastCheckMillis > 0) {
                Text(
                    "Last checked ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(updates.lastCheckMillis))}",
                    color = p.textMuted, fontSize = 12.sp,
                )
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
                Gap(10.dp)
                Text(notes, color = p.textMuted, fontSize = 13.sp, lineHeight = 17.sp)
            }
            Gap(14.dp)
            when (state) {
                is UpdateState.Available -> PrimaryButton("Download and install ${sizeMb(state.release.apkSize)}", onClick = { updates.download(state.release) })
                is UpdateState.ReadyToInstall -> PrimaryButton("Install now", onClick = { startInstall(context, updates, state) })
                else -> SecondaryButton(
                    if (state is UpdateState.Checking) "Checking…" else "Check for updates",
                    onClick = { updates.check() },
                    enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                )
            }
            Gap(12.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check automatically", color = p.text, fontSize = 15.sp)
                    Text("Look for a new build each time the app opens", color = p.textMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = auto,
                    onCheckedChange = { auto = it; updates.autoCheck = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = p.onAccent, checkedTrackColor = p.accent),
                )
            }
        }
    }
}
