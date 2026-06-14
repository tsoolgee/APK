package com.musicplayer.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicplayer.data.Song
import com.musicplayer.ui.components.AlbumArt
import com.musicplayer.ui.theme.*

@Composable
fun EditSongDialog(
    song: Song,
    onSave: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var genre by remember { mutableStateOf(song.genre) }
    var year by remember { mutableStateOf(song.year.takeIf { it > 0 }?.toString() ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(SurfaceCard, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArt(uri = song.albumArtUri, modifier = Modifier.size(56.dp))
                    Column {
                        Text("עריכת שיר", style = MaterialTheme.typography.headlineMedium)
                        Text(song.path.substringAfterLast("/"), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    }
                }

                Divider(color = SurfaceBorder)

                EditField(
                    value = title,
                    onValueChange = { title = it },
                    label = "כותרת",
                    icon = Icons.Filled.MusicNote
                )

                EditField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = "אמן",
                    icon = Icons.Filled.Person
                )

                EditField(
                    value = album,
                    onValueChange = { album = it },
                    label = "אלבום",
                    icon = Icons.Filled.Album
                )

                EditField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = "ז'אנר",
                    icon = Icons.Filled.Category
                )

                EditField(
                    value = year,
                    onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) },
                    label = "שנה",
                    icon = Icons.Filled.CalendarToday
                )

                Divider(color = SurfaceBorder)

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        Text("ביטול")
                    }

                    Button(
                        onClick = {
                            onSave(
                                song.copy(
                                    title = title.trim().ifBlank { song.title },
                                    artist = artist.trim().ifBlank { song.artist },
                                    album = album.trim().ifBlank { song.album },
                                    genre = genre.trim(),
                                    year = year.toIntOrNull() ?: song.year
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                    ) {
                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("שמור")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextTertiary) },
        leadingIcon = { Icon(icon, null, tint = AccentViolet, modifier = Modifier.size(20.dp)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceElevated,
            unfocusedContainerColor = SurfaceElevated,
            focusedBorderColor = AccentViolet,
            unfocusedBorderColor = SurfaceBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = AccentViolet
        ),
        singleLine = true
    )
}
