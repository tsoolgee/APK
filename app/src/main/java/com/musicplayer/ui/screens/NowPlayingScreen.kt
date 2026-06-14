package com.musicplayer.ui.screens

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.musicplayer.data.MediaScanner
import com.musicplayer.ui.components.AlbumArt
import com.musicplayer.ui.theme.*
import com.musicplayer.viewmodel.PlayerState
import androidx.media3.common.Player

@Composable
fun NowPlayingScreen(
    state: PlayerState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
    onAddToQueue: () -> Unit
) {
    val song = state.currentSong

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Glowing background circle from album art
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-50).dp, y = (-80).dp)
                .blur(120.dp)
                .background(AccentViolet.copy(alpha = 0.18f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 40.dp)
                .blur(100.dp)
                .background(AccentCyan.copy(alpha = 0.08f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Back", tint = TextSecondary, modifier = Modifier.size(28.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("מתנגן כעת", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = song?.album ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onAddToQueue) {
                    Icon(Icons.Filled.QueueMusic, "Queue", tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.weight(0.5f))

            // ── Album Art ─────────────────────────────────────────────────
            val scale by animateFloatAsState(
                targetValue = if (state.isPlaying) 1f else 0.88f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(280.dp)
                    .scale(scale)
                    .shadow(
                        elevation = 32.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = AccentViolet.copy(alpha = 0.4f),
                        spotColor = AccentViolet.copy(alpha = 0.6f)
                    )
            ) {
                AlbumArt(
                    uri = song?.albumArtUri,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 24.dp
                )
            }

            Spacer(Modifier.weight(0.5f))

            // ── Song Info ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "—",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 22.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(song?.artist ?: "")
                            if (song?.genre?.isNotBlank() == true) append("  ·  ${song.genre}")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        imageVector = if (song?.isFavorite == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song?.isFavorite == true) FavoriteRed else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Progress Slider ───────────────────────────────────────────
            val progressFraction = if (state.duration > 0)
                (state.progress.toFloat() / state.duration.toFloat()).coerceIn(0f, 1f) else 0f

            Column {
                Slider(
                    value = progressFraction,
                    onValueChange = { frac -> onSeek((frac * state.duration).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentViolet,
                        activeTrackColor = AccentViolet,
                        inactiveTrackColor = SurfaceBorder
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(MediaScanner.formatDuration(state.progress), style = MaterialTheme.typography.labelSmall)
                    Text(MediaScanner.formatDuration(state.duration), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Controls ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Filled.Shuffle,
                        "Shuffle",
                        tint = if (state.shuffleEnabled) AccentViolet else TextTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Previous
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }

                // Play/Pause
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(16.dp, CircleShape, spotColor = AccentViolet)
                        .background(
                            Brush.radialGradient(listOf(AccentViolet, AccentVioletDim)),
                            CircleShape
                        )
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Filled.SkipNext, "Next", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }

                // Repeat
                IconButton(onClick = onRepeat) {
                    val (icon, tint) = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Pair(Icons.Filled.RepeatOne, AccentViolet)
                        Player.REPEAT_MODE_ALL -> Pair(Icons.Filled.Repeat, AccentViolet)
                        else -> Pair(Icons.Filled.Repeat, TextTertiary)
                    }
                    Icon(icon, "Repeat", tint = tint, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
