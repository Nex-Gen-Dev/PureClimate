package com.midea.pureclimate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midea.pureclimate.data.Room

enum class SetupMethod(val title: String, val subtitle: String) {
    MIDEA("Midea", "Ready to use. Change it later from the remote."),
    SCAN("Find my AC", "The phone tests every AC brand until yours beeps."),
    BRAND("Choose brand", "Pick the brand or protocol from a list."),
    LEARN("Learn from remote", "Record the original remote with the ESP32 receiver."),
}

@Composable
fun HomeScreen(
    rooms: List<Room>,
    irOk: Boolean,
    onOpen: (Room) -> Unit,
    onDelete: (Room) -> Unit,
    onRename: (Room, String) -> Unit,
    onAdd: (name: String, method: SetupMethod) -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Room?>(null) }
    var renaming by remember { mutableStateOf<Room?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("PureClimate", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(irOk, 6.dp)
                    HGap(4.dp)
                    Text(
                        if (irOk) "IR emitter ready" else "No IR emitter on this phone",
                        color = p.textMuted, fontSize = 13.sp,
                    )
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = p.text)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rooms, key = { it.id }) { room ->
                RoomCard(room, onClick = { onOpen(room) }, onRename = { renaming = room }, onDelete = { confirmDelete = room })
            }
            item(key = "add") { AddCard { showAdd = true } }
        }
    }

    if (showAdd) {
        AddRoomDialog(
            onDismiss = { showAdd = false },
            onCreate = { name, method -> showAdd = false; onAdd(name, method) },
        )
    }
    renaming?.let { room ->
        var text by remember(room.id) { mutableStateOf(room.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = p.surface,
            titleContentColor = p.text,
            title = { Text("Rename room") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(32) },
                    singleLine = true,
                    label = { Text("Room name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = p.text, unfocusedTextColor = p.text,
                        focusedBorderColor = p.accent, unfocusedBorderColor = p.buttonBorder,
                        focusedLabelColor = p.accent, unfocusedLabelColor = p.textMuted, cursorColor = p.accent,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(room, text); renaming = null }, enabled = text.isNotBlank()) {
                    Text("Save", color = if (text.isNotBlank()) p.accent else p.textMuted)
                }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = p.textMuted) } },
        )
    }
    confirmDelete?.let { room ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove ${room.name}?") },
            text = { Text("Its remote settings and any learned buttons will be deleted.") },
            confirmButton = {
                TextButton(onClick = { onDelete(room); confirmDelete = null }) { Text("Remove", color = p.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
            containerColor = p.surface,
            titleContentColor = p.text,
            textContentColor = p.textMuted,
        )
    }
}

@Composable
private fun RoomCard(room: Room, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    val active = room.power || room.isLearnedOnly
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.aspectRatio(0.95f).clip(shape).background(p.surface)
            .border(1.dp, if (room.power) p.accent.copy(alpha = 0.6f) else p.surfaceBorder, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Top) {
                val glow = if (room.power) p.accent else p.textMuted
                Box(
                    Modifier
                        .then(if (room.power) Modifier.shadow(14.dp, CircleShape, ambientColor = glow, spotColor = glow) else Modifier)
                        .size(46.dp).clip(CircleShape)
                        .background(if (room.power) p.accent.copy(alpha = 0.18f) else p.button),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (room.isLearnedOnly) Icons.Rounded.SettingsRemote else Icons.Rounded.AcUnit,
                        contentDescription = null,
                        tint = if (active && room.power) p.accent else p.textMuted,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Box(Modifier.weight(1f))
                IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Rename ${room.name}", tint = p.textMuted, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${room.name}", tint = p.textMuted, modifier = Modifier.size(18.dp))
                }
            }
            Box(Modifier.weight(1f))
            Text(room.name, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                room.statusLine(),
                color = if (room.power) p.accent else p.textMuted,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AddCard(onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.aspectRatio(0.95f).clip(shape)
            .border(1.5.dp, p.buttonBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = p.textMuted, modifier = Modifier.size(32.dp))
            Gap(6.dp)
            Text("Add room", color = p.textMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AddRoomDialog(onDismiss: () -> Unit, onCreate: (String, SetupMethod) -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(SetupMethod.MIDEA) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        titleContentColor = p.text,
        title = { Text("Add room") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    singleLine = true,
                    label = { Text("Room name") },
                    placeholder = { Text("e.g. Master Bedroom") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = p.text, unfocusedTextColor = p.text,
                        focusedBorderColor = p.accent, unfocusedBorderColor = p.buttonBorder,
                        focusedLabelColor = p.accent, unfocusedLabelColor = p.textMuted, cursorColor = p.accent,
                    ),
                )
                Gap(14.dp)
                SectionLabel("How should it talk to the AC?")
                Gap(4.dp)
                SetupMethod.values().forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { method = m }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = method == m,
                            onClick = { method = m },
                            colors = RadioButtonDefaults.colors(selectedColor = p.accent, unselectedColor = p.textMuted),
                        )
                        Column {
                            Text(m.title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(m.subtitle, color = p.textMuted, fontSize = 12.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, method) }, enabled = name.isNotBlank()) {
                Text("Create", color = if (name.isNotBlank()) p.accent else p.textMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.textMuted) } },
    )
}
