package com.musicplayer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.musicplayer.data.MediaScanner
import com.musicplayer.data.Song
import com.musicplayer.ui.theme.*
import com.musicplayer.viewmodel.PlayerState

// ─── Album Art ───────────────────────────────────────────────────────────────

@Composable
fun AlbumArt(
    uri: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(SurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

// ─── Song Row ─────────────────────────────────────────────────────────────────

@Composable
fun SongRow(
    song: Song,
    isPlaying: Boolean = false,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onAddToQueue: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .background(
                if (isPlaying) AccentGlow else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Album art with playing indicator overlay
        Box(modifier = Modifier.size(48.dp)) {
            AlbumArt(uri = song.albumArtUri, modifier = Modifier.fillMaxSize())
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayingBars()
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) AccentCyan else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (song.genre.isNotBlank()) {
                    Text(text = "·", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = song.genre,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentViolet.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Text(
            text = MediaScanner.formatDuration(song.duration),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )

        IconButton(onClick = onFavorite, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (song.isFavorite) FavoriteRed else TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SurfaceCard)
            ) {
                DropdownMenuItem(
                    text = { Text("הוסף לתור", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Filled.QueueMusic, null, tint = AccentViolet) },
                    onClick = { onAddToQueue(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("עריכת פרטים", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = AccentViolet) },
                    onClick = { onEdit(); showMenu = false }
                )
            }
        }
    }
}

// ─── Playing Bars Animation ───────────────────────────────────────────────────

@Composable
fun PlayingBars(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "bars")
    val bar1 by infiniteTransition.animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        0.6f, 0.2f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        0.8f, 0.4f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "b3"
    )
    Row(
        modifier = modifier.size(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(bar1, bar2, bar3).forEach { frac ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(frac)
                    .background(AccentCyan, RoundedCornerShape(2.dp))
            )
        }
    }
}

// ─── Mini Player Bar ─────────────────────────────────────────────────────────

@Composable
fun MiniPlayerBar(
    state: PlayerState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = state.currentSong ?: return

    val progressFraction = if (state.duration > 0)
        (state.progress.toFloat() / state.duration.toFloat()).coerceIn(0f, 1f)
    else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard)
            .clickable(onClick = onExpand)
    ) {
        // Thin progress line at top
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = AccentViolet,
            trackColor = SurfaceBorder
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AlbumArt(uri = song.albumArtUri, modifier = Modifier.size(42.dp), cornerRadius = 8.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = AccentViolet,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action, color = AccentViolet, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ─── Chip Row ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipRow(
    items: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier.width(8.dp))
        // "All" chip
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("הכל") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentViolet,
                selectedLabelColor = TextPrimary,
                containerColor = SurfaceElevated,
                labelColor = TextSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected == null,
                borderColor = SurfaceBorder,
                selectedBorderColor = Color.Transparent
            )
        )
        items.forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelect(if (selected == item) null else item) },
                label = { Text(item) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentViolet,
                    selectedLabelColor = TextPrimary,
                    containerColor = SurfaceElevated,
                    labelColor = TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == item,
                    borderColor = SurfaceBorder,
                    selectedBorderColor = Color.Transparent
                )
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}
