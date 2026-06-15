package com.musicplayer

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.musicplayer.ui.components.MiniPlayerBar
import com.musicplayer.ui.screens.*
import com.musicplayer.ui.theme.*
import com.musicplayer.viewmodel.PlayerViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Library     : Screen("library",      "ספריה",   Icons.Filled.LibraryMusic)
    object Favorites   : Screen("favorites",    "מועדפים",  Icons.Filled.Favorite)
    object Queue       : Screen("queue",        "תור",      Icons.Filled.QueueMusic)
    object BmeSettings : Screen("bme_settings", "",         Icons.Filled.Settings)
}

val bottomNavItems = listOf(Screen.Library, Screen.Favorites, Screen.Queue)

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> if (grants.values.any { it }) viewModel.syncLibrary() }

    private var folderPickerCallback: ((Uri) -> Unit)? = null
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            folderPickerCallback?.invoke(uri)
        }
    }

    fun launchFolderPicker(onResult: (Uri) -> Unit) {
        folderPickerCallback = onResult
        folderPickerLauncher.launch(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        setContent { MusicPlayerTheme { MusicPlayerApp(viewModel = viewModel, activity = this) } }
    }

    private fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissionLauncher.launch(perms)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(viewModel: PlayerViewModel, activity: MainActivity) {
    val navController = rememberNavController()
    val playerState   by viewModel.playerState.collectAsState()
    val libraryState  by viewModel.libraryState.collectAsState()
    val favorites     by viewModel.favorites.collectAsState()
    val queue         by viewModel.queue.collectAsState()
    val genres        by viewModel.genres.collectAsState()
    val artists       by viewModel.artists.collectAsState()
    val editSong      by viewModel.editSong.collectAsState()

    var showNowPlaying by remember { mutableStateOf(false) }

    if (showNowPlaying && playerState.currentSong != null) {
        NowPlayingScreen(
            state        = playerState,
            onBack       = { showNowPlaying = false },
            onPlayPause  = viewModel::togglePlayPause,
            onNext       = viewModel::skipNext,
            onPrevious   = viewModel::skipPrevious,
            onSeek       = viewModel::seekTo,
            onShuffle    = viewModel::toggleShuffle,
            onRepeat     = viewModel::cycleRepeat,
            onFavorite   = { playerState.currentSong?.let { viewModel.toggleFavorite(it) } },
            onAddToQueue = { playerState.currentSong?.let { viewModel.addToQueue(it) } }
        )
        return
    }

    editSong?.let { song ->
        EditSongDialog(song = song, onSave = viewModel::saveSong, onDismiss = viewModel::closeEditSong)
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            val entry               by navController.currentBackStackEntryAsState()
            val currentDestination  = entry?.destination
            val isOnBmeSettings     = currentDestination?.route == Screen.BmeSettings.route

            if (!isOnBmeSettings) {
                Column {
                    AnimatedVisibility(
                        visible = playerState.currentSong != null,
                        enter   = slideInVertically { it },
                        exit    = slideOutVertically { it }
                    ) {
                        MiniPlayerBar(
                            state       = playerState,
                            onExpand    = { showNowPlaying = true },
                            onPlayPause = viewModel::togglePlayPause,
                            onNext      = viewModel::skipNext
                        )
                    }
                    NavigationBar(containerColor = SurfaceDeep, tonalElevation = 0.dp) {
                        bottomNavItems.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick  = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true; restoreState = true
                                    }
                                },
                                icon = {
                                    BadgedBox(badge = {
                                        if (screen == Screen.Queue && queue.isNotEmpty())
                                            Badge(containerColor = AccentViolet) { Text("${queue.size}") }
                                    }) { Icon(screen.icon, screen.label, modifier = Modifier.size(22.dp)) }
                                },
                                label  = { Text(screen.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor   = AccentViolet,
                                    selectedTextColor   = AccentViolet,
                                    unselectedIconColor = TextTertiary,
                                    unselectedTextColor = TextTertiary,
                                    indicatorColor      = AccentViolet.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Library.route,
            modifier         = Modifier.padding(innerPadding),
            enterTransition  = { fadeIn() + slideInHorizontally() },
            exitTransition   = { fadeOut() }
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    libraryState   = libraryState,
                    playerState    = playerState,
                    genres         = genres,
                    artists        = artists,
                    onSearchQuery  = viewModel::setSearchQuery,
                    onFilterGenre  = viewModel::filterByGenre,
                    onFilterArtist = viewModel::filterByArtist,
                    onPlaySong     = { song -> viewModel.playSong(song, libraryState.filteredSongs) },
                    onFavorite     = viewModel::toggleFavorite,
                    onAddToQueue   = viewModel::addToQueue,
                    onEditSong     = viewModel::openEditSong,
                    onSync         = viewModel::syncLibrary,
                    onBmeSettings  = { navController.navigate(Screen.BmeSettings.route) },
                    onPickFolder   = {
                        activity.launchFolderPicker { uri -> viewModel.syncFolder(activity, uri) }
                    },
                    onRemoveFolder = { uri -> viewModel.removeFolder(activity, uri) }
                )
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    favorites    = favorites,
                    playerState  = playerState,
                    onPlaySong   = { song, list -> viewModel.playSong(song, list) },
                    onFavorite   = viewModel::toggleFavorite,
                    onAddToQueue = viewModel::addToQueue,
                    onEdit       = viewModel::openEditSong
                )
            }
            composable(Screen.Queue.route) {
                QueueScreen(
                    queue       = queue,
                    playerState = playerState,
                    onPlaySong  = { song -> viewModel.playSong(song, queue) },
                    onRemove    = viewModel::removeFromQueue,
                    onClear     = viewModel::clearQueue,
                    onFavorite  = viewModel::toggleFavorite,
                    onEdit      = viewModel::openEditSong
                )
            }
            composable(Screen.BmeSettings.route) {
                BmeSettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
