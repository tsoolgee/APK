package com.musicplayer.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import com.musicplayer.data.Song
import com.musicplayer.ui.components.*
import com.musicplayer.ui.theme.*
import com.musicplayer.viewmodel.PlayerState

// ─── Queue Screen ─────────────────────────────────────────────────────────────

@Composable
fun QueueScreen(
    queue: List<Song>,
    playerState: PlayerState,
    onPlaySong: (Song) -> Unit,
    onRemove: (Song) -> Unit,
    onClear: () -> Unit,
    onFavorite: (Song) -> Unit,
    onEdit: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("תור השמעה", style = MaterialTheme.typography.displayLarge)
            if (queue.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("נקה הכל", color = FavoriteRed)
                }
            }
        }

        if (queue.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.QueueMusic,
                title = "התור ריק",
                subtitle = "הוסף שירים מהספרייה"
            )
        } else {
            // Currently playing indicator
            playerState.currentSong?.let { current ->
                val idx = queue.indexOfFirst { it.id == current.id }
                if (idx >= 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.VolumeUp, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        Text(
                            "מתנגן: ${current.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentCyan
                        )
                        Spacer(Modifier.weight(1f))
                        Text("${idx + 1}/${queue.size}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(queue, key = { _, s -> s.id }) { index, song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(32.dp).padding(start = 12.dp),
                            color = TextTertiary
                        )
                        SongRow(
                            song = song,
                            isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                            onPlay = { onPlaySong(song) },
                            onFavorite = { onFavorite(song) },
                            onAddToQueue = { /* already in queue */ },
                            onEdit = { onEdit(song) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onRemove(song) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Filled.RemoveCircleOutline, "Remove", tint = TextTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─── Favorites Screen ─────────────────────────────────────────────────────────

@Composable
fun FavoritesScreen(
    favorites: List<Song>,
    playerState: PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onFavorite: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onEdit: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("מועדפים", style = MaterialTheme.typography.displayLarge)
            if (favorites.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { if (favorites.isNotEmpty()) onPlaySong(favorites.first(), favorites) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AccentVioletDim
                    )
                ) {
                    Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("השמע הכל")
                }
            }
        }

        if (favorites.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.FavoriteBorder,
                title = "אין מועדפים עדיין",
                subtitle = "לחץ על ❤ כדי להוסיף שירים"
            )
        } else {
            Text(
                "${favorites.size} שירים",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(favorites, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                        onPlay = { onPlaySong(song, favorites) },
                        onFavorite = { onFavorite(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onEdit = { onEdit(song) }
                    )
                }
            }
        }
    }
}
