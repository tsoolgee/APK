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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.musicplayer.data.Song
import com.musicplayer.ui.components.*
import com.musicplayer.ui.theme.*
import com.musicplayer.viewmodel.LibraryState
import com.musicplayer.viewmodel.PlayerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryState: LibraryState,
    playerState: PlayerState,
    genres: List<String>,
    artists: List<String>,
    onSearchQuery: (String) -> Unit,
    onFilterGenre: (String?) -> Unit,
    onFilterArtist: (String?) -> Unit,
    onPlaySong: (Song) -> Unit,
    onFavorite: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onEditSong: (Song) -> Unit,
    onSync: () -> Unit,
    onBmeSettings: () -> Unit,
    onPickFolder: () -> Unit
) {
    val displayedSongs = remember(libraryState) {
        when {
            libraryState.selectedGenre != null ->
                libraryState.filteredSongs.filter { it.genre == libraryState.selectedGenre }
            libraryState.selectedArtist != null ->
                libraryState.filteredSongs.filter { it.artist == libraryState.selectedArtist }
            else -> libraryState.filteredSongs
        }
    }

    var filterMode by remember { mutableStateOf(FilterMode.GENRE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        // ג”€ג”€ Header ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("׳¡׳₪׳¨׳™׳”", style = MaterialTheme.typography.displayLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (libraryState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AccentViolet,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(4.dp))
                }
                // Folder picker
                IconButton(onClick = onPickFolder) {
                    Icon(Icons.Filled.FolderOpen, "׳‘׳—׳¨ ׳×׳™׳§׳™׳™׳”", tint = AccentViolet)
                }
                // Sync
                IconButton(onClick = onSync) {
                    Icon(Icons.Filled.Refresh, "׳¨׳¢׳ ׳•׳", tint = TextSecondary)
                }
                // Settings (no label ג€” only insiders know)
                IconButton(onClick = onBmeSettings) {
                    Icon(Icons.Filled.Settings, null, tint = TextSecondary)
                }
            }
        }

        // ג”€ג”€ Search Bar ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
        OutlinedTextField(
            value = libraryState.searchQuery,
            onValueChange = onSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("׳—׳™׳₪׳•׳© ׳©׳™׳¨׳™׳, ׳׳׳ ׳™׳, ׳–'׳׳ ׳¨׳™׳...", color = TextTertiary) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextTertiary) },
            trailingIcon = {
                if (libraryState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQuery("") }) {
                        Icon(Icons.Filled.Close, null, tint = TextTertiary)
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard,
                focusedBorderColor = AccentViolet,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        // ג”€ג”€ Filter Mode Toggle ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .background(SurfaceCard, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterMode.values().forEach { mode ->
                val selected = filterMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) AccentViolet else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { filterMode = mode }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        mode.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) TextPrimary else TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ג”€ג”€ Filter Chips ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
        when (filterMode) {
            FilterMode.GENRE -> FilterChipRow(
                items = genres,
                selected = libraryState.selectedGenre,
                onSelect = onFilterGenre
            )
            FilterMode.ARTIST -> FilterChipRow(
                items = artists,
                selected = libraryState.selectedArtist,
                onSelect = onFilterArtist
            )
            FilterMode.ALL -> { /* no chips */ }
        }

        Spacer(Modifier.height(4.dp))

        // ג”€ג”€ Count ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
        Text(
            "${displayedSongs.size} ׳©׳™׳¨׳™׳",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // ג”€ג”€ Song List ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€ג”€
        if (displayedSongs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.MusicOff,
                title = if (libraryState.searchQuery.isNotBlank()) "׳׳ ׳ ׳׳¦׳׳• ׳×׳•׳¦׳׳•׳×"
                else "׳”׳¡׳₪׳¨׳™׳™׳” ׳¨׳™׳§׳”",
                subtitle = if (libraryState.searchQuery.isNotBlank()) "׳ ׳¡׳” ׳—׳™׳₪׳•׳© ׳׳—׳¨"
                else "׳׳—׳¥ נ“ ׳׳‘׳—׳™׳¨׳× ׳×׳™׳§׳™׳™׳” ׳׳• ׳¨׳¢׳ ׳•׳ ׳׳¡׳¨׳™׳§׳× ׳”׳׳›׳©׳™׳¨"
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(displayedSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                        onPlay = { onPlaySong(song) },
                        onFavorite = { onFavorite(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onEdit = { onEditSong(song) }
                    )
                }
            }
        }
    }
}

enum class FilterMode(val label: String) {
    ALL("׳”׳›׳"), GENRE("׳–'׳׳ ׳¨"), ARTIST("׳׳׳")
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge)
    }
}

