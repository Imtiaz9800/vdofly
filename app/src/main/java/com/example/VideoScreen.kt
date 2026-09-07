package com.example

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.ui.theme.*

enum class BottomNavTab {
    LOCAL, RECENT, NETWORK, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
    viewModel: VideoViewModel,
    onVideoSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val permissionGranted by viewModel.permissionGranted.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val filteredVideos by viewModel.filteredVideos.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatchingVideos.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val decoderMode by viewModel.decoderMode.collectAsStateWithLifecycle()
    val recentStreams by viewModel.recentStreams.collectAsStateWithLifecycle()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val vaultPin by viewModel.vaultPin.collectAsStateWithLifecycle()
    val hiddenVideosCount by viewModel.hiddenVideosCount.collectAsStateWithLifecycle()
    val hiddenVideoUris by viewModel.hiddenVideoUris.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(BottomNavTab.LOCAL) }
    var showSearchField by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }
    var showStreamDialog by remember { mutableStateOf(false) }
    var showPrivateVaultDialog by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    var streamUrlInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var pinErrorText by remember { mutableStateOf<String?>(null) }
    var isChangingPin by remember { mutableStateOf(false) }
    var videoPendingHide by remember { mutableStateOf<VideoItem?>(null) }
    var folderPendingHide by remember { mutableStateOf<String?>(null) }
    var videoToDelete by remember { mutableStateOf<VideoItem?>(null) }
    var videoDetailsToShow by remember { mutableStateOf<VideoItem?>(null) }

    val onToggleLockVideo: (VideoItem) -> Unit = { video ->
        if (hiddenVideoUris.contains(video.uri.toString())) {
            viewModel.unhideVideo(video.uri)
        } else {
            if (vaultPin.isNullOrEmpty()) {
                videoPendingHide = video
                folderPendingHide = null
                pinInput = ""
                pinErrorText = null
                isChangingPin = false
                showPrivateVaultDialog = true
            } else {
                viewModel.hideVideo(video.uri)
            }
        }
    }

    val onToggleLockFolder: (String) -> Unit = { folderName ->
        if (vaultPin.isNullOrEmpty()) {
            folderPendingHide = folderName
            videoPendingHide = null
            pinInput = ""
            pinErrorText = null
            isChangingPin = false
            showPrivateVaultDialog = true
        } else {
            viewModel.hideFolder(folderName)
        }
    }

    // Handle back navigation:
    // 1. If search is active, back dismisses the search bar
    BackHandler(enabled = showSearchField) {
        if (searchQuery.isNotEmpty()) {
            viewModel.updateSearchQuery("")
        }
        showSearchField = false
    }

    // 2. If inside any folder, back exits the folder back to the folder list
    BackHandler(enabled = !showSearchField && selectedFolder != null) {
        viewModel.selectFolder(null)
    }

    // 3. If inside Hidden/Private Safe folder, back returns to all folders
    BackHandler(enabled = !showSearchField && selectedFolder == null && selectedFilter == FilterCategory.HIDDEN) {
        viewModel.setFilterCategory(FilterCategory.ALL_FOLDERS)
    }

    // 4. If a non-default filter category is selected on LOCAL tab, back returns to ALL_FOLDERS
    BackHandler(enabled = !showSearchField && selectedFolder == null && selectedFilter != FilterCategory.ALL_FOLDERS && selectedFilter != FilterCategory.HIDDEN && currentTab == BottomNavTab.LOCAL) {
        viewModel.setFilterCategory(FilterCategory.ALL_FOLDERS)
    }

    // 5. If on another bottom navigation tab, back returns to the LOCAL tab
    BackHandler(enabled = !showSearchField && selectedFolder == null && currentTab != BottomNavTab.LOCAL) {
        currentTab = BottomNavTab.LOCAL
    }

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionGranted()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionToRequest)
    }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    // Infinite rotation for scan icon
    val infiniteTransition = rememberInfiniteTransition(label = "scan_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchField) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search videos...", color = OnSurfaceVariantDark) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = OnSurfaceDark,
                                unfocusedTextColor = OnSurfaceDark,
                                focusedIndicatorColor = PrimaryCyan,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (searchQuery.isNotEmpty()) viewModel.updateSearchQuery("")
                                    else showSearchField = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = OnSurfaceVariantDark)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PrimaryContainerCyan),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "VDOFLY Logo",
                                    tint = OnPrimaryCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "VDOFLY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = OnSurfaceDark
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Local",
                                fontSize = 12.sp,
                                color = OnSurfaceVariantDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    if (!showSearchField) {
                        IconButton(onClick = { showSearchField = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = OnSurfaceVariantDark)
                        }
                        IconButton(onClick = {
                            if (isVaultUnlocked) {
                                if (selectedFilter == FilterCategory.HIDDEN) {
                                    viewModel.lockVault()
                                    viewModel.setFilterCategory(FilterCategory.ALL_FOLDERS)
                                } else {
                                    viewModel.setFilterCategory(FilterCategory.HIDDEN)
                                }
                            } else {
                                pinInput = ""
                                pinErrorText = null
                                isChangingPin = false
                                showPrivateVaultDialog = true
                            }
                        }) {
                            BadgedBox(
                                badge = {
                                    if (hiddenVideosCount > 0 && !isVaultUnlocked) {
                                        Badge(containerColor = TertiaryAmber) {
                                            Text("$hiddenVideosCount", color = OnTertiaryAmber)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = "Private Safe Folder",
                                    tint = if (isVaultUnlocked) TertiaryAmber else OnSurfaceVariantDark
                                )
                            }
                        }
                        IconButton(onClick = { showCastDialog = true }) {
                            Icon(Icons.Default.Cast, contentDescription = "Cast", tint = OnSurfaceVariantDark)
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = OnSurfaceVariantDark)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(SurfaceContainerDark)
                            ) {
                                Text(
                                    "SORT BY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCyan,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text("Date Added", color = OnSurfaceDark) },
                                    onClick = { viewModel.setSortOrder(SortOrder.DATE_ADDED); showSortMenu = false },
                                    leadingIcon = { if (sortOrder == SortOrder.DATE_ADDED) Icon(Icons.Default.Check, null, tint = PrimaryCyan) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duration", color = OnSurfaceDark) },
                                    onClick = { viewModel.setSortOrder(SortOrder.DURATION); showSortMenu = false },
                                    leadingIcon = { if (sortOrder == SortOrder.DURATION) Icon(Icons.Default.Check, null, tint = PrimaryCyan) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size", color = OnSurfaceDark) },
                                    onClick = { viewModel.setSortOrder(SortOrder.SIZE); showSortMenu = false },
                                    leadingIcon = { if (sortOrder == SortOrder.SIZE) Icon(Icons.Default.Check, null, tint = PrimaryCyan) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name", color = OnSurfaceDark) },
                                    onClick = { viewModel.setSortOrder(SortOrder.NAME); showSortMenu = false },
                                    leadingIcon = { if (sortOrder == SortOrder.NAME) Icon(Icons.Default.Check, null, tint = PrimaryCyan) }
                                )
                                HorizontalDivider(color = OutlineVariantDark)
                                DropdownMenuItem(
                                    text = { Text("Rescan Library", color = OnSurfaceDark) },
                                    onClick = { viewModel.refreshLibrary(); showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Sync, null, tint = PrimaryCyan) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark.copy(alpha = 0.95f),
                    titleContentColor = OnSurfaceDark
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceContainerDark.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == BottomNavTab.LOCAL,
                    onClick = { currentTab = BottomNavTab.LOCAL },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Local") },
                    label = { Text("Local", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryCyan,
                        selectedTextColor = PrimaryCyan,
                        unselectedIconColor = OnSurfaceVariantDark,
                        unselectedTextColor = OnSurfaceVariantDark,
                        indicatorColor = PrimaryContainerCyan.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == BottomNavTab.RECENT,
                    onClick = { currentTab = BottomNavTab.RECENT },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = "Recent") },
                    label = { Text("Recent", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryCyan,
                        selectedTextColor = PrimaryCyan,
                        unselectedIconColor = OnSurfaceVariantDark,
                        unselectedTextColor = OnSurfaceVariantDark,
                        indicatorColor = PrimaryContainerCyan.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == BottomNavTab.NETWORK,
                    onClick = { currentTab = BottomNavTab.NETWORK },
                    icon = { Icon(Icons.Default.Language, contentDescription = "Network") },
                    label = { Text("Network", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryCyan,
                        selectedTextColor = PrimaryCyan,
                        unselectedIconColor = OnSurfaceVariantDark,
                        unselectedTextColor = OnSurfaceVariantDark,
                        indicatorColor = PrimaryContainerCyan.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == BottomNavTab.SETTINGS,
                    onClick = { currentTab = BottomNavTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryCyan,
                        selectedTextColor = PrimaryCyan,
                        unselectedIconColor = OnSurfaceVariantDark,
                        unselectedTextColor = OnSurfaceVariantDark,
                        indicatorColor = PrimaryContainerCyan.copy(alpha = 0.2f)
                    )
                )
            }
        },
        floatingActionButton = {
            if (currentTab == BottomNavTab.LOCAL) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = { showStreamDialog = true },
                        shape = CircleShape,
                        color = SurfaceContainerHighDark,
                        shadowElevation = 6.dp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stream URL", color = OnSurfaceDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            if (videos.isNotEmpty()) {
                                val randomIndex = (0 until videos.size).random()
                                onVideoSelected(randomIndex)
                            }
                        },
                        containerColor = PrimaryCyan,
                        contentColor = OnPrimaryCyan,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Play", modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (currentTab) {
                BottomNavTab.LOCAL -> {
                    LocalLibraryContent(
                        viewModel = viewModel,
                        videos = videos,
                        filteredVideos = filteredVideos,
                        continueWatching = continueWatching,
                        folders = folders,
                        selectedFilter = selectedFilter,
                        selectedFolder = selectedFolder,
                        isGridView = isGridView,
                        imageLoader = imageLoader,
                        isVaultUnlocked = isVaultUnlocked,
                        hiddenVideosCount = hiddenVideosCount,
                        hiddenVideoUris = hiddenVideoUris,
                        onUnlockVaultRequested = {
                            pinInput = ""
                            pinErrorText = null
                            isChangingPin = false
                            showPrivateVaultDialog = true
                        },
                        onChangePinRequested = {
                            pinInput = ""
                            pinErrorText = null
                            isChangingPin = true
                            showPrivateVaultDialog = true
                        },
                        onLockToggle = onToggleLockVideo,
                        onLockToggleFolder = onToggleLockFolder,
                        onToggleGridView = { isGridView = !isGridView },
                        onVideoSelected = { video ->
                            val index = videos.indexOfFirst { it.id == video.id }
                            if (index != -1) onVideoSelected(index)
                        },
                        onVideoLongClick = { video ->
                            videoToDelete = video
                        },
                        onDeleteRequested = { video ->
                            videoToDelete = video
                        },
                        onInfoRequested = { video ->
                            videoDetailsToShow = video
                        },
                        onFolderSelected = { folderName ->
                            viewModel.selectFolder(if (selectedFolder == folderName) null else folderName)
                        },
                        onNavigateToRecent = { currentTab = BottomNavTab.RECENT }
                    )
                }
                BottomNavTab.RECENT -> {
                    RecentWatchHistoryContent(
                        continueWatching = continueWatching,
                        videos = videos,
                        imageLoader = imageLoader,
                        hiddenVideoUris = hiddenVideoUris,
                        onClearHistory = { viewModel.clearPlaybackHistory() },
                        onVideoSelected = { video ->
                            val index = videos.indexOfFirst { it.id == video.id }
                            if (index != -1) onVideoSelected(index)
                        },
                        onVideoLongClick = { video ->
                            videoToDelete = video
                        },
                        onDeleteRequested = { video ->
                            videoToDelete = video
                        },
                        onInfoRequested = { video ->
                            videoDetailsToShow = video
                        },
                        onLockToggle = onToggleLockVideo
                    )
                }
                BottomNavTab.NETWORK -> {
                    NetworkStreamContent(
                        recentStreams = recentStreams,
                        onPlayStream = { url ->
                            viewModel.addNetworkStream(url)
                            viewModel.showToast("Streaming $url")
                            val index = videos.indexOfFirst { it.uri.toString() == url }
                            if (index != -1) onVideoSelected(index)
                            else if (videos.isNotEmpty()) onVideoSelected(0)
                        }
                    )
                }
                BottomNavTab.SETTINGS -> {
                    SettingsContent(
                        decoderMode = decoderMode,
                        storageStats = storageStats,
                        totalVideosCount = videos.size,
                        onSelectDecoder = { viewModel.setDecoderMode(it) },
                        onRescan = { viewModel.refreshLibrary() },
                        onClearHistory = { viewModel.clearPlaybackHistory() }
                    )
                }
            }

            AnimatedVisibility(
                visible = toastMessage != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceContainerHighestDark,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(toastMessage ?: "", color = OnSurfaceDark, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showStreamDialog) {
        AlertDialog(
            onDismissRequest = { showStreamDialog = false },
            containerColor = SurfaceContainerDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = PrimaryCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Network Stream", color = OnSurfaceDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Enter any HTTP, HTTPS, HLS, or RTSP video link:", color = OnSurfaceVariantDark, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = streamUrlInput,
                        onValueChange = { streamUrlInput = it },
                        placeholder = { Text("https://example.com/video.mp4", color = OutlineDark) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = OutlineVariantDark,
                            focusedTextColor = OnSurfaceDark,
                            unfocusedTextColor = OnSurfaceDark
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (streamUrlInput.isNotBlank()) {
                            viewModel.addNetworkStream(streamUrlInput)
                            showStreamDialog = false
                            viewModel.showToast("Opening stream...")
                            if (videos.isNotEmpty()) onVideoSelected(0)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = OnPrimaryCyan)
                ) {
                    Text("Play Stream")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStreamDialog = false }) {
                    Text("Cancel", color = OnSurfaceVariantDark)
                }
            }
        )
    }

    if (showPrivateVaultDialog) {
        val isSettingPin = vaultPin.isNullOrEmpty() || isChangingPin
        val pendingTargetDesc = when {
            folderPendingHide != null -> "folder '$folderPendingHide'"
            videoPendingHide != null -> "video '${videoPendingHide!!.name}'"
            else -> null
        }
        AlertDialog(
            onDismissRequest = {
                showPrivateVaultDialog = false
                videoPendingHide = null
                folderPendingHide = null
                pinErrorText = null
                isChangingPin = false
                pinInput = ""
            },
            containerColor = SurfaceContainerDark,
            shape = RoundedCornerShape(24.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(TertiaryAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isSettingPin) Icons.Default.Key else Icons.Default.Lock,
                        contentDescription = null,
                        tint = TertiaryAmber,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = when {
                        isChangingPin -> "Change Security PIN"
                        isSettingPin -> "Create Safe Folder PIN"
                        else -> "Unlock Private Safe"
                    },
                    color = OnSurfaceDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = when {
                            isSettingPin && pendingTargetDesc != null ->
                                "Please set a 4-digit PIN first to hide and secure $pendingTargetDesc in your Private Safe."
                            isSettingPin ->
                                "Set a 4-digit PIN to encrypt and hide videos and folders in your Private Safe."
                            pendingTargetDesc != null ->
                                "Enter your 4-digit PIN to confirm moving $pendingTargetDesc to Private Safe:"
                            else ->
                                "Enter your 4-digit security PIN to access protected video files:"
                        },
                        color = OnSurfaceVariantDark,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            val digitsOnly = it.filter { ch -> ch.isDigit() }
                            if (digitsOnly.length <= 4) {
                                pinInput = digitsOnly
                                pinErrorText = null
                            }
                        },
                        placeholder = { Text("••••", color = OutlineDark) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pinErrorText != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TertiaryAmber,
                            unfocusedBorderColor = OutlineVariantDark,
                            focusedTextColor = OnSurfaceDark,
                            unfocusedTextColor = OnSurfaceDark,
                            errorBorderColor = MaterialTheme.colorScheme.error,
                            errorTextColor = MaterialTheme.colorScheme.error
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (pinErrorText != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pinErrorText!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length != 4) {
                            pinErrorText = "PIN must be exactly 4 digits"
                            return@Button
                        }
                        if (isSettingPin) {
                            viewModel.setVaultPin(pinInput)
                            isChangingPin = false
                            if (folderPendingHide != null) {
                                viewModel.hideFolder(folderPendingHide!!)
                                folderPendingHide = null
                            }
                            if (videoPendingHide != null) {
                                viewModel.hideVideo(videoPendingHide!!.uri)
                                videoPendingHide = null
                            }
                            showPrivateVaultDialog = false
                            pinInput = ""
                            pinErrorText = null
                        } else {
                            if (viewModel.verifyVaultPin(pinInput)) {
                                if (folderPendingHide != null) {
                                    viewModel.hideFolder(folderPendingHide!!)
                                    folderPendingHide = null
                                }
                                if (videoPendingHide != null) {
                                    viewModel.hideVideo(videoPendingHide!!.uri)
                                    videoPendingHide = null
                                } else {
                                    viewModel.setFilterCategory(FilterCategory.HIDDEN)
                                }
                                showPrivateVaultDialog = false
                                pinInput = ""
                                pinErrorText = null
                            } else {
                                pinErrorText = "Incorrect PIN. Please try again."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TertiaryAmber,
                        contentColor = OnTertiaryAmber
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        when {
                            isSettingPin && pendingTargetDesc != null -> "Save PIN & Hide"
                            isSettingPin -> "Save PIN"
                            pendingTargetDesc != null -> "Confirm & Hide"
                            else -> "Unlock"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPrivateVaultDialog = false
                        videoPendingHide = null
                        folderPendingHide = null
                        pinErrorText = null
                        isChangingPin = false
                        pinInput = ""
                    }
                ) {
                    Text("Cancel", color = OnSurfaceVariantDark)
                }
            }
        )
    }

    if (showCastDialog) {
        AlertDialog(
            onDismissRequest = { showCastDialog = false },
            containerColor = SurfaceContainerDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cast, contentDescription = null, tint = PrimaryCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect to Cast Device", color = OnSurfaceDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    CircularProgressIndicator(color = PrimaryCyan, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Searching for Chromecast and Smart TVs on Wi-Fi...", color = OnSurfaceVariantDark, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showCastDialog = false }) {
                    Text("Close", color = PrimaryCyan)
                }
            }
        )
    }

    if (videoToDelete != null) {
        DeleteVideoDialog(
            video = videoToDelete!!,
            imageLoader = imageLoader,
            onDismiss = { videoToDelete = null },
            onConfirm = {
                val toDelete = videoToDelete
                if (toDelete != null) {
                    viewModel.deleteVideo(toDelete.uri)
                }
                videoToDelete = null
            }
        )
    }

    if (videoDetailsToShow != null) {
        val detailsVideo = videoDetailsToShow!!
        VideoDetailsDialog(
            video = detailsVideo,
            isLocked = hiddenVideoUris.contains(detailsVideo.uri.toString()),
            onLockToggle = { onToggleLockVideo(detailsVideo) },
            onDismiss = { videoDetailsToShow = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalLibraryContent(
    viewModel: VideoViewModel,
    videos: List<VideoItem>,
    filteredVideos: List<VideoItem>,
    continueWatching: List<VideoItem>,
    folders: List<VideoFolder>,
    selectedFilter: FilterCategory,
    selectedFolder: String?,
    isGridView: Boolean,
    imageLoader: ImageLoader,
    isVaultUnlocked: Boolean = false,
    hiddenVideosCount: Int = 0,
    hiddenVideoUris: Set<String> = emptySet(),
    onUnlockVaultRequested: () -> Unit = {},
    onChangePinRequested: () -> Unit = {},
    onLockToggle: (VideoItem) -> Unit = {},
    onLockToggleFolder: (String) -> Unit = {},
    onToggleGridView: () -> Unit,
    onVideoSelected: (VideoItem) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit,
    onDeleteRequested: (VideoItem) -> Unit,
    onInfoRequested: (VideoItem) -> Unit,
    onFolderSelected: (String) -> Unit,
    onNavigateToRecent: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChipItem(
                        icon = Icons.Default.FolderOpen,
                        label = "All Folders",
                        isSelected = selectedFilter == FilterCategory.ALL_FOLDERS,
                        onClick = { viewModel.setFilterCategory(FilterCategory.ALL_FOLDERS) }
                    )
                }
                item {
                    FilterChipItem(
                        icon = Icons.Default.PlayCircle,
                        label = "Videos",
                        countBadge = videos.size.toString(),
                        isSelected = selectedFilter == FilterCategory.VIDEOS,
                        onClick = { viewModel.setFilterCategory(FilterCategory.VIDEOS) }
                    )
                }
                item {
                    FilterChipItem(
                        icon = Icons.Default.Done,
                        label = "Downloaded",
                        isSelected = selectedFilter == FilterCategory.DOWNLOADED,
                        onClick = { viewModel.setFilterCategory(FilterCategory.DOWNLOADED) }
                    )
                }
                item {
                    FilterChipItem(
                        icon = Icons.Default.Chat,
                        label = "WhatsApp Status",
                        isSelected = selectedFilter == FilterCategory.WHATSAPP,
                        onClick = { viewModel.setFilterCategory(FilterCategory.WHATSAPP) }
                    )
                }
                item {
                    FilterChipItem(
                        icon = Icons.Default.Videocam,
                        label = "Camera",
                        isSelected = selectedFilter == FilterCategory.CAMERA,
                        onClick = { viewModel.setFilterCategory(FilterCategory.CAMERA) }
                    )
                }
                item {
                    FilterChipItem(
                        icon = if (isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        label = if (isVaultUnlocked) "Private Safe" else "Hidden Folder",
                        countBadge = if (hiddenVideosCount > 0) "$hiddenVideosCount" else null,
                        isSelected = selectedFilter == FilterCategory.HIDDEN,
                        onClick = {
                            if (isVaultUnlocked) {
                                viewModel.setFilterCategory(FilterCategory.HIDDEN)
                            } else {
                                onUnlockVaultRequested()
                            }
                        }
                    )
                }
            }
        }

        if (continueWatching.isNotEmpty() && selectedFolder == null && selectedFilter == FilterCategory.ALL_FOLDERS) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Continue Watching",
                                color = OnSurfaceDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        TextButton(
                            onClick = onNavigateToRecent,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("History", color = PrimaryCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(14.dp))
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(continueWatching, key = { it.id }) { video ->
                            ContinueWatchingCard(
                                video = video,
                                imageLoader = imageLoader,
                                isLocked = hiddenVideoUris.contains(video.uri.toString()),
                                onClick = { onVideoSelected(video) },
                                onLongClick = { onVideoLongClick(video) },
                                onDeleteClick = { onDeleteRequested(video) },
                                onInfoClick = { onInfoRequested(video) },
                                onLockToggle = { onLockToggle(video) }
                            )
                        }
                    }
                }
            }
        }

        if (selectedFilter == FilterCategory.HIDDEN) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                    border = BorderStroke(1.dp, TertiaryAmber.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(TertiaryAmber.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = TertiaryAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Private Safe Active",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = OnSurfaceDark
                                )
                                Text(
                                    "${filteredVideos.size} files hidden from gallery",
                                    fontSize = 11.sp,
                                    color = OnSurfaceVariantDark
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = onChangePinRequested,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Change PIN", color = TertiaryAmber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    viewModel.lockVault()
                                    viewModel.setFilterCategory(FilterCategory.ALL_FOLDERS)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TertiaryAmber,
                                    contentColor = OnTertiaryAmber
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedFolder != null) {
                        IconButton(onClick = { viewModel.selectFolder(null) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryCyan)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedFolder, color = OnSurfaceDark, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    } else {
                        Text(
                            when (selectedFilter) {
                                FilterCategory.ALL_FOLDERS -> "Folders"
                                FilterCategory.HIDDEN -> "Protected Files"
                                else -> "Videos"
                            },
                            color = if (selectedFilter == FilterCategory.HIDDEN) TertiaryAmber else OnSurfaceDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = SurfaceContainerHighDark
                        ) {
                            Text(
                                if (selectedFilter == FilterCategory.ALL_FOLDERS) "${folders.size}" else "${filteredVideos.size}",
                                color = OnSurfaceVariantDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedFolder != null) {
                        Surface(
                            onClick = { onLockToggleFolder(selectedFolder) },
                            shape = RoundedCornerShape(8.dp),
                            color = TertiaryContainerAmber.copy(alpha = 0.85f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Hide entire folder",
                                    tint = OnTertiaryAmber,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Hide Folder",
                                    color = OnTertiaryAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    IconButton(onClick = onToggleGridView, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                            contentDescription = "Toggle Grid",
                            tint = OnSurfaceVariantDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (selectedFolder == null && selectedFilter == FilterCategory.ALL_FOLDERS) {
            if (isGridView) {
                val chunkedFolders = folders.chunked(2)
                items(chunkedFolders, key = { chunk -> chunk.joinToString { it.name } }) { rowFolders ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (folder in rowFolders) {
                            Box(modifier = Modifier.weight(1f)) {
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = {
                                        if (it == SwipeToDismissBoxValue.EndToStart) {
                                            onLockToggleFolder(folder.name)
                                            false
                                        } else false
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    enableDismissFromEndToStart = true,
                                    backgroundContent = {
                                        val isDismissing = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                        if (isDismissing) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(TertiaryContainerAmber)
                                                    .padding(horizontal = 14.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Lock,
                                                        contentDescription = "Hide folder",
                                                        tint = OnTertiaryAmber,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        "Hide",
                                                        color = OnTertiaryAmber,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    content = {
                                        FolderGridCard(
                                            folder = folder,
                                            imageLoader = imageLoader,
                                            onClick = { onFolderSelected(folder.name) },
                                            onLockFolder = { onLockToggleFolder(folder.name) }
                                        )
                                    }
                                )
                            }
                        }
                        if (rowFolders.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(folders, key = { it.name }) { folder ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                onLockToggleFolder(folder.name)
                                false
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        backgroundContent = {
                            val isDismissing = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                            if (isDismissing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TertiaryContainerAmber)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "Hide Folder",
                                            color = OnTertiaryAmber,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Hide folder to safe",
                                            tint = OnTertiaryAmber,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        },
                        content = {
                            FolderListItem(
                                folder = folder,
                                imageLoader = imageLoader,
                                onClick = { onFolderSelected(folder.name) },
                                onLockFolder = { onLockToggleFolder(folder.name) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }
            }
        } else {
            if (filteredVideos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedFilter == FilterCategory.HIDDEN) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(TertiaryAmber.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = TertiaryAmber,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No hidden videos in vault",
                                    color = OnSurfaceDark,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Swipe left or tap the lock button on any video or folder to move it into your Private Safe.",
                                    color = OnSurfaceVariantDark,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FolderOff, contentDescription = null, tint = OutlineDark, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No videos found", color = OnSurfaceVariantDark, fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else if (isGridView) {
                val chunkedVideos = filteredVideos.chunked(2)
                items(chunkedVideos, key = { chunk -> chunk.joinToString { it.id.toString() } }) { rowVideos ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (video in rowVideos) {
                            Box(modifier = Modifier.weight(1f)) {
                                val isLocked = hiddenVideoUris.contains(video.uri.toString())
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = {
                                        if (it == SwipeToDismissBoxValue.EndToStart) {
                                            onLockToggle(video)
                                            false
                                        } else false
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    enableDismissFromEndToStart = true,
                                    backgroundContent = {
                                        val isDismissing = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                        if (isDismissing) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isLocked) SurfaceContainerHighestDark else TertiaryContainerAmber
                                                    )
                                                    .padding(horizontal = 14.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                                        contentDescription = if (isLocked) "Unhide" else "Hide",
                                                        tint = if (isLocked) PrimaryCyan else OnTertiaryAmber,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = if (isLocked) "Unhide" else "Hide",
                                                        color = if (isLocked) PrimaryCyan else OnTertiaryAmber,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    content = {
                                        VideoGridCard(
                                            video = video,
                                            imageLoader = imageLoader,
                                            isLocked = isLocked,
                                            onClick = { onVideoSelected(video) },
                                            onLongClick = { onVideoLongClick(video) },
                                            onDeleteClick = { onDeleteRequested(video) },
                                            onInfoClick = { onInfoRequested(video) },
                                            onLockToggle = { onLockToggle(video) }
                                        )
                                    }
                                )
                            }
                        }
                        if (rowVideos.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(filteredVideos, key = { it.id }) { video ->
                    val isLocked = hiddenVideoUris.contains(video.uri.toString())
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                onLockToggle(video)
                                false
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        backgroundContent = {
                            val isDismissing = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                            if (isDismissing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isLocked) SurfaceContainerHighestDark else TertiaryContainerAmber
                                        )
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            if (isLocked) "Unhide Video" else "Hide Video",
                                            color = if (isLocked) PrimaryCyan else OnTertiaryAmber,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Icon(
                                            if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                            contentDescription = if (isLocked) "Unhide video" else "Hide video to safe",
                                            tint = if (isLocked) PrimaryCyan else OnTertiaryAmber,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        },
                        content = {
                            VideoListItemCard(
                                video = video,
                                imageLoader = imageLoader,
                                isLocked = isLocked,
                                onClick = { onVideoSelected(video) },
                                onLongClick = { onVideoLongClick(video) },
                                onDeleteClick = { onDeleteRequested(video) },
                                onInfoClick = { onInfoRequested(video) },
                                onLockToggle = { onLockToggle(video) }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChipItem(
    icon: ImageVector,
    label: String,
    countBadge: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) PrimaryContainerCyan else SurfaceContainerHighDark,
        contentColor = if (isSelected) OnPrimaryCyan else OnSurfaceDark,
        shadowElevation = if (isSelected) 3.dp else 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) OnPrimaryCyan else PrimaryCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (countBadge != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) OnPrimaryCyan.copy(alpha = 0.2f) else SurfaceContainerHighestDark
                ) {
                    Text(
                        countBadge,
                        color = if (isSelected) OnPrimaryCyan else PrimaryCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    video: VideoItem,
    imageLoader: ImageLoader,
    isLocked: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onLockToggle: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
        modifier = Modifier
            .width(260.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(SurfaceContainerHighestDark)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.uri)
                        .videoFrameMillis(1000)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SurfaceContainerLowestDark.copy(alpha = 0.85f)
                    ) {
                        Text(
                            "HW+",
                            color = PrimaryCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SurfaceContainerLowestDark.copy(alpha = 0.85f)
                    ) {
                        Text(
                            video.resolution,
                            color = TertiaryAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isLocked) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SurfaceContainerLowestDark.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = TertiaryAmber, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Safe", color = TertiaryAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PrimaryCyan.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = OnPrimaryCyan, modifier = Modifier.size(24.dp))
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = SurfaceContainerLowestDark.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        formatDuration(video.duration),
                        color = OnSurfaceDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                val progress = if (video.duration > 0) (video.resumePosition.toFloat() / video.duration).coerceIn(0f, 1f) else 0.5f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = PrimaryCyan,
                    trackColor = OutlineVariantDark.copy(alpha = 0.6f)
                )
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        video.name,
                        color = OnSurfaceDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = OnSurfaceVariantDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(SurfaceContainerDark)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play Video", color = OnSurfaceDark) },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryCyan) },
                                onClick = {
                                    showMenu = false
                                    onClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isLocked) "Unhide Video" else "Lock & Hide Video", color = TertiaryAmber) },
                                leadingIcon = {
                                    Icon(
                                        if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = TertiaryAmber
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onLockToggle()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Details", color = OnSurfaceDark) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = OnSurfaceVariantDark) },
                                onClick = {
                                    showMenu = false
                                    onInfoClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Video", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Replay, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "Resume at ${formatDuration(video.resumePosition)}",
                            color = PrimaryCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        formatFileSize(video.size),
                        color = OnSurfaceVariantDark,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FolderListItem(
    folder: VideoFolder,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onLockFolder: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(72.dp, 50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerHighestDark)
                ) {
                    if (folder.latestVideoUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(folder.latestVideoUri)
                                .videoFrameMillis(1000)
                                .crossfade(true)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Folder thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = handleFolderTint(folder.name),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Folder badge overlay
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SurfaceContainerLowestDark.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = PrimaryCyan,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "${folder.videoCount}",
                                color = OnSurfaceDark,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            folder.name,
                            color = OnSurfaceDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (folder.isNew) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = TertiaryContainerAmber
                            ) {
                                Text(
                                    "NEW",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnTertiaryAmber,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${folder.videoCount} videos", color = OnSurfaceVariantDark, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(OutlineVariantDark))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(formatFileSize(folder.totalSizeBytes), color = OnSurfaceVariantDark, fontSize = 12.sp)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onLockFolder,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Hide folder to safe",
                        tint = TertiaryAmber,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open",
                    tint = OnSurfaceVariantDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun FolderGridCard(
    folder: VideoFolder,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onLockFolder: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(SurfaceContainerHighestDark)
            ) {
                if (folder.latestVideoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(folder.latestVideoUri)
                            .videoFrameMillis(1000)
                            .crossfade(true)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = "Folder video thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = handleFolderTint(folder.name),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = SurfaceContainerLowestDark.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = PrimaryCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${folder.videoCount}",
                            color = OnSurfaceDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (folder.isNew) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = TertiaryContainerAmber,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                "NEW",
                                color = OnTertiaryAmber,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Surface(
                        onClick = onLockFolder,
                        shape = CircleShape,
                        color = SurfaceContainerLowestDark.copy(alpha = 0.85f),
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Hide folder to safe",
                                tint = TertiaryAmber,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    folder.name,
                    color = OnSurfaceDark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${folder.videoCount} videos • ${formatFileSize(folder.totalSizeBytes)}",
                    color = OnSurfaceVariantDark,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun handleFolderTint(name: String): Color {
    return when {
        name.contains("Camera", ignoreCase = true) -> PrimaryCyan
        name.contains("Movie", ignoreCase = true) -> SecondaryBlue
        name.contains("Download", ignoreCase = true) -> TertiaryAmber
        else -> OnSurfaceDark
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItemCard(
    video: VideoItem,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onLockToggle: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp, 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContainerHighestDark)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.uri)
                        .videoFrameMillis(1000)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isLocked) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = SurfaceContainerLowestDark.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.TopStart).padding(3.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Hidden in Vault",
                            tint = TertiaryAmber,
                            modifier = Modifier.size(12.dp).padding(1.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = SurfaceContainerLowestDark.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
                ) {
                    Text(
                        formatDuration(video.duration),
                        color = OnSurfaceDark,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.name,
                    color = OnSurfaceDark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = SurfaceContainerHighestDark
                    ) {
                        Text(
                            video.resolution,
                            color = PrimaryCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (isLocked) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = TertiaryContainerAmber
                        ) {
                            Text(
                                "SAFE",
                                color = OnTertiaryAmber,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(formatFileSize(video.size), color = OnSurfaceVariantDark, fontSize = 11.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClick) {
                    Icon(Icons.Default.PlayCircle, contentDescription = "Play", tint = PrimaryCyan, modifier = Modifier.size(28.dp))
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = OnSurfaceVariantDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(SurfaceContainerDark)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play", color = OnSurfaceDark) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryCyan) },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isLocked) "Unhide Video" else "Lock & Hide Video", color = TertiaryAmber) },
                            leadingIcon = {
                                Icon(
                                    if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = TertiaryAmber
                                )
                            },
                            onClick = {
                                showMenu = false
                                onLockToggle()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Details", color = OnSurfaceDark) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = OnSurfaceVariantDark) },
                            onClick = {
                                showMenu = false
                                onInfoClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Video", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridCard(
    video: VideoItem,
    imageLoader: ImageLoader,
    isLocked: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onLockToggle: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(SurfaceContainerHighestDark)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.uri)
                        .videoFrameMillis(1000)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = SurfaceContainerLowestDark.copy(alpha = 0.85f)
                    ) {
                        Text(
                            video.resolution,
                            color = PrimaryCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                    if (isLocked) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = TertiaryContainerAmber
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = OnTertiaryAmber, modifier = Modifier.size(9.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    "SAFE",
                                    color = OnTertiaryAmber,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = SurfaceContainerLowestDark.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        formatDuration(video.duration),
                        color = OnSurfaceDark,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        video.name,
                        color = OnSurfaceDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = OnSurfaceVariantDark,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(SurfaceContainerDark)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play", color = OnSurfaceDark) },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryCyan) },
                                onClick = {
                                    showMenu = false
                                    onClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isLocked) "Unhide Video" else "Lock & Hide Video", color = TertiaryAmber) },
                                leadingIcon = {
                                    Icon(
                                        if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = TertiaryAmber
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onLockToggle()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Details", color = OnSurfaceDark) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = OnSurfaceVariantDark) },
                                onClick = {
                                    showMenu = false
                                    onInfoClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Video", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    formatFileSize(video.size),
                    color = OnSurfaceVariantDark,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun RecentWatchHistoryContent(
    continueWatching: List<VideoItem>,
    videos: List<VideoItem>,
    imageLoader: ImageLoader,
    hiddenVideoUris: Set<String> = emptySet(),
    onClearHistory: () -> Unit,
    onVideoSelected: (VideoItem) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit = {},
    onDeleteRequested: (VideoItem) -> Unit = {},
    onInfoRequested: (VideoItem) -> Unit = {},
    onLockToggle: (VideoItem) -> Unit = {}
) {
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = SurfaceContainerDark,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    "Clear Playback History?",
                    color = OnSurfaceDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            },
            text = {
                Text(
                    "This will clear all saved resume points and remove all items from your watch history. Your video files will not be deleted.",
                    color = OnSurfaceVariantDark,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = OnSurfaceVariantDark)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Playback History", color = OnSurfaceDark, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Resume your recently played media files", color = OnSurfaceVariantDark, fontSize = 13.sp)
                }
                if (continueWatching.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { showClearConfirmDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear History", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (continueWatching.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = SurfaceContainerHighestDark,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = OutlineDark,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No playback history",
                            color = OnSurfaceDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Videos you watch will appear here with your saved resume positions.",
                            color = OnSurfaceVariantDark,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        } else {
            items(continueWatching, key = { it.id }) { video ->
                val isLocked = hiddenVideoUris.contains(video.uri.toString())
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.EndToStart) {
                            onLockToggle(video)
                            false
                        } else false
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundContent = {
                        val isDismissing = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                        if (isDismissing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isLocked) SurfaceContainerHighestDark else TertiaryContainerAmber
                                    )
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        if (isLocked) "Unhide Video" else "Hide Video",
                                        color = if (isLocked) PrimaryCyan else OnTertiaryAmber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Icon(
                                        if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = if (isLocked) "Unhide video" else "Hide video to safe",
                                        tint = if (isLocked) PrimaryCyan else OnTertiaryAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    },
                    content = {
                        VideoListItemCard(
                            video = video,
                            imageLoader = imageLoader,
                            isLocked = isLocked,
                            onClick = { onVideoSelected(video) },
                            onLongClick = { onVideoLongClick(video) },
                            onDeleteClick = { onDeleteRequested(video) },
                            onInfoClick = { onInfoRequested(video) },
                            onLockToggle = { onLockToggle(video) }
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun DeleteVideoDialog(
    video: VideoItem,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerDark,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                "Delete Video?",
                color = OnSurfaceDark,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighestDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp, 44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceContainerLowestDark)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(video.uri)
                                    .videoFrameMillis(1000)
                                    .crossfade(true)
                                    .build(),
                                imageLoader = imageLoader,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.name,
                                color = OnSurfaceDark,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatFileSize(video.size),
                                    color = OnSurfaceVariantDark,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "•",
                                    color = OutlineDark,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatDuration(video.duration),
                                    color = OnSurfaceVariantDark,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Are you sure you want to delete this video file from your device? This action cannot be undone.",
                    color = OnSurfaceVariantDark,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel", color = OnSurfaceVariantDark)
            }
        }
    )
}

@Composable
fun VideoDetailsDialog(
    video: VideoItem,
    isLocked: Boolean = false,
    onLockToggle: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Video Details", color = OnSurfaceDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow(label = "Title", value = video.name)
                DetailRow(label = "Resolution", value = video.resolution)
                DetailRow(label = "Duration", value = formatDuration(video.duration))
                DetailRow(label = "File Size", value = formatFileSize(video.size))
                DetailRow(label = "Folder", value = video.bucketName)
                DetailRow(label = "Decoder", value = "HW+ / HW / SW Accelerated")
                DetailRow(
                    label = "Vault Status",
                    value = if (isLocked) "Hidden in Private Safe" else "Public in Media Library"
                )
            }
        },
        dismissButton = {
            FilledTonalButton(
                onClick = {
                    onLockToggle()
                    onDismiss()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isLocked) SurfaceContainerHighestDark else TertiaryContainerAmber,
                    contentColor = if (isLocked) PrimaryCyan else OnTertiaryAmber
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isLocked) "Unhide Video" else "Lock & Hide", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PrimaryCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = OnSurfaceVariantDark, fontSize = 12.sp, modifier = Modifier.weight(0.35f))
        Text(value, color = OnSurfaceDark, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.65f))
    }
}

@Composable
fun NetworkStreamContent(
    recentStreams: List<String>,
    onPlayStream: (String) -> Unit
) {
    var inputUrl by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Network Streaming", color = OnSurfaceDark, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Stream remote videos directly with hardware acceleration", color = OnSurfaceVariantDark, fontSize = 13.sp)
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Direct Stream URL", color = PrimaryCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        placeholder = { Text("https://example.com/live/stream.m3u8", color = OutlineDark, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = OutlineVariantDark,
                            focusedTextColor = OnSurfaceDark,
                            unfocusedTextColor = OnSurfaceDark
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { if (inputUrl.isNotBlank()) onPlayStream(inputUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = OnPrimaryCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stream Video", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("Demo & Test Streams", color = OnSurfaceDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        items(recentStreams) { url ->
            Card(
                onClick = { onPlayStream(url) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            url.substringAfterLast("/"),
                            color = OnSurfaceDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            url,
                            color = OnSurfaceVariantDark,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onPlayStream(url) }) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = PrimaryCyan)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsContent(
    decoderMode: DecoderMode,
    storageStats: StorageStats,
    totalVideosCount: Int,
    onSelectDecoder: (DecoderMode) -> Unit,
    onRescan: () -> Unit,
    onClearHistory: () -> Unit
) {
    var showTutorialPage by remember { mutableStateOf(false) }
    var showAboutPage by remember { mutableStateOf(false) }
    var showDeveloperPage by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    if (showTutorialPage) {
        BackHandler { showTutorialPage = false }
        TutorialScreen(onBack = { showTutorialPage = false })
        return
    }

    if (showAboutPage) {
        BackHandler { showAboutPage = false }
        AboutScreen(
            storageStats = storageStats,
            totalVideosCount = totalVideosCount,
            onRescan = onRescan,
            onBack = { showAboutPage = false }
        )
        return
    }

    if (showDeveloperPage) {
        BackHandler { showDeveloperPage = false }
        DeveloperScreen(onBack = { showDeveloperPage = false })
        return
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = SurfaceContainerDark,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    "Clear Playback History?",
                    color = OnSurfaceDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            },
            text = {
                Text(
                    "This will clear all saved resume points and remove all items from your watch history. Your video files will not be deleted.",
                    color = OnSurfaceVariantDark,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearHistoryDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = OnSurfaceVariantDark)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", color = OnSurfaceDark, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Configure player decoder, gestures, and preferences", color = OnSurfaceVariantDark, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
        }

        item {
            Text("PLAYBACK ENGINE", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Hardware Decoder", color = OnSurfaceDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Choose rendering engine for optimal battery and performance", color = OnSurfaceVariantDark, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecoderButton("HW+", isSelected = decoderMode == DecoderMode.HW_PLUS) { onSelectDecoder(DecoderMode.HW_PLUS) }
                        DecoderButton("HW", isSelected = decoderMode == DecoderMode.HW) { onSelectDecoder(DecoderMode.HW) }
                        DecoderButton("SW", isSelected = decoderMode == DecoderMode.SW) { onSelectDecoder(DecoderMode.SW) }
                    }
                }
            }
        }

        item {
            Text("TUTORIAL", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                onClick = { showTutorialPage = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainerCyan.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = PrimaryCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "App Tutorial & User Guide",
                                color = OnSurfaceDark,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Detailed instructions on gestures, subtitles, streaming & features",
                                color = OnSurfaceVariantDark,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Tutorial",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        item {
            Text("DATA & PRIVACY", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                onClick = { showClearHistoryDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Clear Playback History", color = OnSurfaceDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Reset video resume positions and watch history", color = OnSurfaceVariantDark, fontSize = 12.sp)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = OnSurfaceVariantDark, modifier = Modifier.size(16.dp))
                }
            }
        }

        item {
            Text("ABOUT", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                onClick = { showAboutPage = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainerCyan.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = PrimaryCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "About VDOFLY",
                                color = OnSurfaceDark,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Version 1.0.0 Pro • Engine specs, privacy & info",
                                color = OnSurfaceVariantDark,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open About",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        item {
            Card(
                onClick = { showDeveloperPage = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainerCyan.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = PrimaryCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "About Developer",
                                color = OnSurfaceDark,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Developer bio, skills, contact & social links",
                                color = OnSurfaceVariantDark,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Developer Profile",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TutorialScreen(onBack: () -> Unit) {
    var expandedTopicIndex by remember { mutableIntStateOf(0) }

    val topics = remember {
        listOf(
            TutorialTopic(
                title = "Video Player & Touch Gestures",
                icon = Icons.Default.PlayCircle,
                items = listOf(
                    "Screen Brightness" to "Swipe vertically up or down on the left side of the screen to adjust brightness (0% – 100%).",
                    "Media Volume" to "Swipe vertically up or down on the right side of the screen to change volume (0% – 100%).",
                    "Quick Seek (±10s)" to "Double-tap on the left side to rewind 10 seconds, or double-tap on the right side to fast forward 10 seconds.",
                    "Precise Scrubbing" to "Drag your finger horizontally across the screen to scrub to any frame in the video with real-time target timestamp feedback.",
                    "Screen Lock" to "Tap the Lock button on the left edge of the player to disable touches and prevent accidental interruptions.",
                    "Aspect Ratio Modes" to "Tap the Aspect Ratio button to cycle through Fit (original ratio), Zoom / Crop (fill whole screen), and Stretch."
                )
            ),
            TutorialTopic(
                title = "Subtitles, Audio & Playback Speed",
                icon = Icons.Default.Subtitles,
                items = listOf(
                    "Subtitles & Closed Captions" to "Tap the Subtitle (CC) icon in the top player bar to switch tracks or toggle subtitles On/Off.",
                    "Import Custom Subtitles" to "Tap 'Add Subtitle File' in the subtitle menu to import external .srt, .vtt, or .ass subtitle files from storage.",
                    "Dual Audio Tracks" to "Tap the Audio Track icon to switch between audio streams for multilingual movies and video files.",
                    "Playback Speed" to "Tap the Speed icon (1.0x) to adjust playback speed from 0.5x up to 2.0x for rapid learning or slow-motion.",
                    "Sleep Timer" to "Tap the Timer icon to schedule auto-pause (5 – 120 mins). A countdown badge appears when active.",
                    "Picture-in-Picture (PiP)" to "Tap the PiP icon or swipe to Home to keep watching your video in a floating overlay window."
                )
            ),
            TutorialTopic(
                title = "Browsing, Folders & Search",
                icon = Icons.Default.Folder,
                items = listOf(
                    "Real Video Frame Thumbnails" to "Folders and video files automatically extract and display real video frame preview thumbnails.",
                    "List & Grid Layouts" to "Toggle between the compact list and 2-column visual grid view using the layout button in the header.",
                    "Category Filters" to "Filter videos instantly by All Folders, Videos, Downloaded, WhatsApp Status, or Camera clips.",
                    "Search & Instant Sort" to "Search any video by file title, or sort by Date Added, Duration, File Size, or Alphabetical Name.",
                    "Swipe-to-Delete & Info" to "Swipe any video left to delete it from storage, or tap the 3-dot menu to view technical resolution and codec details."
                )
            ),
            TutorialTopic(
                title = "Network Streaming & History",
                icon = Icons.Default.Link,
                items = listOf(
                    "Network Stream URL" to "Stream remote videos by entering HTTP, HTTPS, HLS (.m3u8), or RTSP links in the Network tab or Stream URL button.",
                    "Continue Watching" to "Every video automatically saves its exact playback position so you can resume anytime from the home carousel or History tab.",
                    "Clear History" to "Clear your saved resume progress anytime via the 'Clear History' button in the History tab or under Settings."
                )
            ),
            TutorialTopic(
                title = "Hardware Decoder Engines",
                icon = Icons.Default.Speed,
                items = listOf(
                    "HW+ (Hardware Plus)" to "Best performance and battery efficiency with advanced hardware rendering for high-resolution 4K/60fps media.",
                    "HW (Standard Hardware)" to "Standard Android MediaCodec hardware decoding compatible with all devices.",
                    "SW (Software Decoding)" to "CPU-based software fallback for uncommon or older video codecs."
                )
            )
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = SurfaceContainerLowestDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = PrimaryCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            "App Tutorial & User Guide",
                            color = OnSurfaceDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Complete guide to gestures, streaming & player features",
                            color = OnSurfaceVariantDark,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        containerColor = SurfaceContainerLowestDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = PrimaryContainerCyan.copy(alpha = 0.25f)
                    ),
                    border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainerCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = OnPrimaryCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "Welcome to VDOFLY",
                                color = OnSurfaceDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "Hardware-accelerated HD player with intuitive gesture controls, subtitle support, and network streaming.",
                                color = OnSurfaceDark.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "INSTRUCTIONS & FEATURES",
                    color = PrimaryCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            itemsIndexed(topics) { index, topic ->
                val isExpanded = expandedTopicIndex == index

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExpanded) SurfaceContainerHighDark else SurfaceContainerDark
                    ),
                    border = if (isExpanded) BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.5f)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedTopicIndex = if (isExpanded) -1 else index
                                }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isExpanded) PrimaryContainerCyan else SurfaceContainerHighestDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        topic.icon,
                                        contentDescription = null,
                                        tint = if (isExpanded) OnPrimaryCyan else PrimaryCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    topic.title,
                                    color = OnSurfaceDark,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }

                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = if (isExpanded) PrimaryCyan else OnSurfaceVariantDark,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider(color = OutlineVariantDark.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(2.dp))
                                topic.items.forEach { (heading, instruction) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 5.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(PrimaryCyan)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                heading,
                                                color = PrimaryCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                instruction,
                                                color = OnSurfaceDark.copy(alpha = 0.9f),
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryCyan,
                        contentColor = OnPrimaryCyan
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class TutorialTopic(
    val title: String,
    val icon: ImageVector,
    val items: List<Pair<String, String>>
)

@Composable
fun AboutScreen(
    storageStats: StorageStats,
    totalVideosCount: Int,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                color = SurfaceContainerLowestDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = PrimaryCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            "About VDOFLY",
                            color = OnSurfaceDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Player specs, engine architecture & library statistics",
                            color = OnSurfaceVariantDark,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        containerColor = SurfaceContainerLowestDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App Branding Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = PrimaryContainerCyan.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainerCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = OnPrimaryCyan,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "VDOFLY Video Player",
                            color = OnSurfaceDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Version 1.0.0 (Pro Edition)",
                            color = PrimaryCyan,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceContainerHighestDark
                            ) {
                                Text(
                                    "RELEASE BUILD",
                                    color = OnSurfaceDark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceContainerHighestDark
                            ) {
                                Text(
                                    "64-BIT KOTLIN",
                                    color = OnSurfaceDark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "A next-generation, hardware-accelerated media player engineered for ultra-smooth 4K playback, comprehensive gesture controls, and effortless local & network streaming.",
                            color = OnSurfaceDark.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Library & Storage Stats
            item {
                Text("LIBRARY & STORAGE", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Indexed Videos", color = OnSurfaceVariantDark, fontSize = 13.sp)
                            Text("$totalVideosCount videos", color = OnSurfaceDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = OutlineVariantDark.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Media Size on Disk", color = OnSurfaceVariantDark, fontSize = 13.sp)
                            Text(String.format("%.2f GB", storageStats.videoMediaGb), color = PrimaryCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = OutlineVariantDark.copy(alpha = 0.5f))
                        Button(
                            onClick = onRescan,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceContainerHighestDark,
                                contentColor = PrimaryCyan
                            )
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rescan Media Library", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Core Capabilities
            item {
                Text("CORE CAPABILITIES", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AboutFeatureRow(
                            icon = Icons.Default.Speed,
                            title = "Multi-Engine Hardware Decoder",
                            desc = "HW+, HW, and SW decoders for smooth 4K/60fps playback with minimal battery drain."
                        )
                        AboutFeatureRow(
                            icon = Icons.Default.TouchApp,
                            title = "Precision Gesture Controls",
                            desc = "Vertical swipes for brightness & volume, double-tap seek, and horizontal frame scrubbing."
                        )
                        AboutFeatureRow(
                            icon = Icons.Default.Subtitles,
                            title = "Subtitles & Multi-Audio",
                            desc = "Internal track switching and external subtitle importing (.srt, .vtt, .ass)."
                        )
                        AboutFeatureRow(
                            icon = Icons.Default.PictureInPictureAlt,
                            title = "Picture-in-Picture & Speed Control",
                            desc = "Floating multitasking window, sleep timer (5-120 min), and speed control (0.5x-2.0x)."
                        )
                        AboutFeatureRow(
                            icon = Icons.Default.Link,
                            title = "Network Live & Stream Player",
                            desc = "Full support for HTTP/HTTPS video links, HLS (.m3u8), and RTSP streams."
                        )
                    }
                }
            }

            // Tech Stack & Architecture
            item {
                Text("ENGINE & ARCHITECTURE", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TechSpecRow("Playback Engine", "AndroidX Media3 / ExoPlayer 1.4.1")
                        TechSpecRow("UI Framework", "Jetpack Compose & Material Design 3")
                        TechSpecRow("Video Decoders", "MediaCodec NDK & Coil Frame Pipeline")
                        TechSpecRow("Local Storage", "AndroidX DataStore Preferences")
                        TechSpecRow("Language & Runtime", "Kotlin 2.0 with Coroutines & StateFlow")
                    }
                }
            }

            // Privacy & Security
            item {
                Text("PRIVACY & DATA POLICY", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = PrimaryCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "100% Offline-First & Private",
                                color = OnSurfaceDark,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "All media scanning, playback positions, and cache are stored strictly on your local device. VDOFLY collects zero analytics, zero telemetry, and never uploads personal data.",
                                color = OnSurfaceVariantDark,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryCyan,
                        contentColor = OnPrimaryCyan
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AboutFeatureRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(PrimaryContainerCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = OnSurfaceDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, color = OnSurfaceVariantDark, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
fun TechSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = OnSurfaceVariantDark, fontSize = 12.sp)
        Text(value, color = OnSurfaceDark, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    }
}

@Composable
fun DeveloperScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            Surface(
                color = SurfaceContainerLowestDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = PrimaryCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            "About Developer",
                            color = OnSurfaceDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Creator Profile & Engineering Background",
                            color = OnSurfaceVariantDark,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        containerColor = SurfaceContainerLowestDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profile Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = PrimaryContainerCyan.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainerCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Developer Avatar",
                                tint = OnPrimaryCyan,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Lead Android Developer",
                            color = OnSurfaceDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Full-Stack Mobile & Systems Engineer",
                            color = PrimaryCyan,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceContainerHighestDark
                            ) {
                                Text(
                                    "KOTLIN / COMPOSE",
                                    color = OnSurfaceDark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceContainerHighestDark
                            ) {
                                Text(
                                    "EXOPLAYER / MEDIA3",
                                    color = OnSurfaceDark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Passionate software craftsman specializing in high-performance Android media applications, hardware-accelerated playback pipelines, and modern reactive user interfaces.",
                            color = OnSurfaceDark.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Developer Overview & Bio
            item {
                Text("DEVELOPER BIOGRAPHY", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Profile Overview", color = OnSurfaceDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = OutlineVariantDark.copy(alpha = 0.5f))
                        Text(
                            "Hey there! This profile section is dedicated to the creator of VDOFLY. You can customize this bio with your personal story, developer journey, preferred languages, and project vision whenever you are ready.",
                            color = OnSurfaceVariantDark,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // Core Technical Skills
            item {
                Text("TECHNICAL EXPERTISE", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TechSkillItem(
                            title = "Android Native & Jetpack Compose",
                            level = "Advanced",
                            description = "Declarative UI, custom state management, WindowInsets, and Material 3 design systems."
                        )
                        HorizontalDivider(color = OutlineVariantDark.copy(alpha = 0.5f))
                        TechSkillItem(
                            title = "Media Architecture & Streaming",
                            level = "Specialist",
                            description = "Media3 / ExoPlayer integration, HLS/DASH/RTSP live protocols, hardware decoders (HW+)."
                        )
                        HorizontalDivider(color = OutlineVariantDark.copy(alpha = 0.5f))
                        TechSkillItem(
                            title = "Kotlin Asynchronous Coroutines & Flow",
                            level = "Proficient",
                            description = "Structured concurrency, reactive StateFlow patterns, and high-performance threading."
                        )
                    }
                }
            }

            // Contact & Social Links
            item {
                Text("CONTACT & CHANNELS", color = PrimaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeveloperContactRow(
                            icon = Icons.Default.Email,
                            title = "Email Support",
                            value = "imtiazhaque413@gmail.com"
                        )
                        DeveloperContactRow(
                            icon = Icons.Default.Code,
                            title = "GitHub / Open Source",
                            value = "github.com/developer"
                        )
                        DeveloperContactRow(
                            icon = Icons.Default.Language,
                            title = "Portfolio Website",
                            value = "https://developer.portfolio.io"
                        )
                    }
                }
            }

            // Back button
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryCyan,
                        contentColor = OnPrimaryCyan
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TechSkillItem(title: String, level: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = OnSurfaceDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = PrimaryContainerCyan.copy(alpha = 0.35f)
            ) {
                Text(
                    level,
                    color = PrimaryCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(description, color = OnSurfaceVariantDark, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
fun DeveloperContactRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SurfaceContainerHighestDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, color = OnSurfaceDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(value, color = OnSurfaceVariantDark, fontSize = 12.sp)
        }
    }
}

@Composable
fun DecoderButton(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) PrimaryContainerCyan else SurfaceContainerHighestDark,
        contentColor = if (isSelected) OnPrimaryCyan else OnSurfaceDark
    ) {
        Text(
            name,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1024f) {
        String.format("%.1f GB", mb / 1024f)
    } else {
        String.format("%.1f MB", mb)
    }
}
