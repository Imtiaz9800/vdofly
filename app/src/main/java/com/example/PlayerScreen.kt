package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Language
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.media3.common.PlaybackParameters
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.abs

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: VideoViewModel,
    initialIndex: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? ComponentActivity
    
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    var currentMediaIndex by remember { mutableIntStateOf(initialIndex) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItems = videos.map { 
                MediaItem.Builder()
                    .setUri(it.uri)
                    .setMediaId(it.uri.toString())
                    .build()
            }
            setMediaItems(mediaItems, initialIndex, 0L)
            prepare()
            playWhenReady = true
        }
    }
    
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isControlsVisible by remember { mutableStateOf(true) }
    
    var sleepTimerMinutes by remember { mutableStateOf(0) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableStateOf(1f) }

    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            delay(sleepTimerMinutes * 60 * 1000L)
            exoPlayer.pause()
            sleepTimerMinutes = 0
        }
    }

    LaunchedEffect(isPlaying, isSeeking) {
        if (isPlaying && !isSeeking) {
            while(true) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                
                // Save position periodically
                val currentMediaItem = exoPlayer.currentMediaItem
                if (currentMediaItem != null && currentPosition > 0) {
                    viewModel.saveVideoPosition(currentMediaItem.mediaId, currentPosition)
                }

                delay(1000L)
            }
        } else if (!isSeeking) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            val currentMediaItem = exoPlayer.currentMediaItem
            if (currentMediaItem != null && currentPosition > 0) {
                viewModel.saveVideoPosition(currentMediaItem.mediaId, currentPosition)
            }
        }
    }

    LaunchedEffect(Unit) {
        // Load initial position
        val initialUriStr = videos[initialIndex].uri.toString()
        val savedPos = viewModel.getVideoPosition(initialUriStr)
        if (savedPos > 0) {
            exoPlayer.seekTo(initialIndex, savedPos)
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying && !isSeeking) {
            delay(4500L)
            isControlsVisible = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentMediaIndex = exoPlayer.currentMediaItemIndex
            }
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                currentSpeed = playbackParameters.speed
            }
        }
        exoPlayer.addListener(listener)
        
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            exoPlayer.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
            
            // Restore system UI & Orientation
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay for gestures
        GestureOverlay(exoPlayer, onTap = { isControlsVisible = !isControlsVisible })
        
        if (isControlsVisible) {
            val currentVideo = videos.getOrNull(currentMediaIndex)
            val currentTitle = currentVideo?.name ?: (exoPlayer.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Playing Video")
            val currentSubtitle = buildString {
                if (currentVideo != null) {
                    append(currentVideo.resolution)
                    if (currentVideo.bucketName.isNotBlank()) {
                        append(" • ")
                        append(currentVideo.bucketName)
                    }
                }
            }

            ControlsOverlay(
                videoTitle = currentTitle,
                videoSubtitle = currentSubtitle,
                isPlaying = isPlaying,
                sleepTimerMinutes = sleepTimerMinutes,
                currentPosition = currentPosition,
                duration = duration,
                currentSpeed = currentSpeed,
                onSeek = { pos -> 
                    currentPosition = pos
                    isSeeking = true
                },
                onSeekFinished = { pos -> 
                    exoPlayer.seekTo(pos)
                    isSeeking = false
                },
                onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onNext = { exoPlayer.seekToNextMediaItem() },
                onPrev = { exoPlayer.seekToPreviousMediaItem() },
                onRotate = {
                    val currentOrientation = context.resources.configuration.orientation
                    if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                },
                onSpeed = {
                    val newSpeed = when (currentSpeed) {
                        1f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2f
                        2f -> 0.5f
                        else -> 1f
                    }
                    exoPlayer.setPlaybackSpeed(newSpeed)
                },
                onAudioTrack = { showAudioTrackDialog = true },
                onSleepTimer = { showSleepTimerDialog = true },
                onPip = {
                    isControlsVisible = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val pipParams = PictureInPictureParams.Builder()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            pipParams.setAutoEnterEnabled(true)
                        }
                        val format = exoPlayer.videoFormat
                        if (format != null && format.width > 0 && format.height > 0) {
                            pipParams.setAspectRatio(Rational(format.width, format.height))
                        } else {
                            pipParams.setAspectRatio(Rational(16, 9))
                        }
                        activity?.enterPictureInPictureMode(pipParams.build())
                    }
                },
                onBack = {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    onBack()
                }
            )
        }
        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                title = { Text("Sleep Timer", color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Column {
                        listOf(0, 15, 30, 45, 60).forEach { mins ->
                            val label = if (mins == 0) "Off" else "$mins minutes"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sleepTimerMinutes = mins
                                        showSleepTimerDialog = false
                                        isControlsVisible = false
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = sleepTimerMinutes == mins,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(text = label, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Close", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
        if (showAudioTrackDialog) {
            val audioGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            AlertDialog(
                onDismissRequest = { showAudioTrackDialog = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                title = { Text("Select Audio Track", color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    LazyColumn {
                        if (audioGroups.isEmpty()) {
                            item {
                                Text(
                                    text = "No additional audio tracks found.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        items(audioGroups.size) { groupIndex ->
                            val group = audioGroups[groupIndex]
                            val format = group.mediaTrackGroup.getFormat(0)
                            val language = format.language ?: "Unknown"
                            val isSelected = group.isSelected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(
                                                TrackSelectionOverride(group.mediaTrackGroup, listOf(0))
                                            )
                                            .build()
                                        showAudioTrackDialog = false
                                        isControlsVisible = false
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Track ${groupIndex + 1}: $language", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioTrackDialog = false }) { Text("Close", color = MaterialTheme.colorScheme.primary) }
                }
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun ControlsOverlay(
    videoTitle: String,
    videoSubtitle: String,
    isPlaying: Boolean,
    sleepTimerMinutes: Int,
    currentPosition: Long,
    duration: Long,
    currentSpeed: Float,
    onSeek: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onRotate: () -> Unit,
    onSpeed: () -> Unit,
    onAudioTrack: () -> Unit,
    onSleepTimer: () -> Unit,
    onPip: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
        // Top Header Bar with File Name and Action Controls
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = videoTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (videoSubtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "HW+",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = videoSubtitle,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onAudioTrack) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = "Audio Track",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onSleepTimer) {
                        Box {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Sleep Timer",
                                tint = if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.primary else Color.White
                            )
                            if (sleepTimerMinutes > 0) {
                                Text(
                                    text = "$sleepTimerMinutes",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onSpeed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = "Playback Speed",
                                tint = Color.White
                            )
                            Text(
                                text = "${currentSpeed}x",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(onClick = onPip) {
                            Icon(
                                imageVector = Icons.Filled.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(64.dp)) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.width(32.dp))
            IconButton(onClick = onPlayPause, modifier = Modifier.size(80.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.width(32.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp, start = 16.dp, end = 16.dp)
                .navigationBarsPadding()
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = formatTime(currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f,
                onValueChange = { onSeek((it * duration).toLong()) },
                onValueChangeFinished = { onSeekFinished(currentPosition) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.5f)
                )
            )
            Text(text = formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }

        IconButton(
            onClick = onRotate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).navigationBarsPadding()
        ) {
            Icon(
                imageVector = Icons.Filled.ScreenRotation,
                contentDescription = "Rotate",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

enum class DragType { NONE, HORIZONTAL_SEEK, VERTICAL_BRIGHTNESS, VERTICAL_VOLUME }

@Composable
fun GestureOverlay(exoPlayer: ExoPlayer, onTap: () -> Unit) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var volumeLevel by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    
    val activity = context as? ComponentActivity
    var brightnessLevel by remember { 
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness.let { if (it == null || it < 0) 0.5f else it }
        ) 
    }

    var showOverlay by remember { mutableStateOf(false) }
    var dragType by remember { mutableStateOf(DragType.NONE) }
    var startPositionMs by remember { mutableLongStateOf(0L) }
    var seekPositionMs by remember { mutableLongStateOf(0L) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var overlaySeekDelta by remember { mutableStateOf("") }

    // Double tap ripple animation state
    var doubleTapLeft by remember { mutableStateOf(false) }
    var doubleTapRight by remember { mutableStateOf(false) }
    
    val rippleAlpha by animateFloatAsState(
        targetValue = if (doubleTapLeft || doubleTapRight) 0.3f else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "rippleAlpha"
    )

    LaunchedEffect(doubleTapLeft, doubleTapRight) {
        if (doubleTapLeft || doubleTapRight) {
            delay(500)
            doubleTapLeft = false
            doubleTapRight = false
        }
    }

    LaunchedEffect(showOverlay, dragType) {
        if (showOverlay && dragType == DragType.NONE) {
            delay(1000)
            showOverlay = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) {
                            val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                            exoPlayer.seekTo(newPos)
                            doubleTapLeft = true
                        } else {
                            val duration = exoPlayer.duration.coerceAtLeast(0)
                            val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(duration)
                            exoPlayer.seekTo(newPos)
                            doubleTapRight = true
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { _ ->
                        dragType = DragType.NONE
                        totalDragX = 0f
                        startPositionMs = exoPlayer.currentPosition
                        seekPositionMs = exoPlayer.currentPosition
                    },
                    onDragEnd = {
                        if (dragType == DragType.HORIZONTAL_SEEK) {
                            exoPlayer.seekTo(seekPositionMs)
                        }
                        dragType = DragType.NONE
                    },
                    onDragCancel = {
                        dragType = DragType.NONE
                        showOverlay = false
                    }
                ) { change, dragAmount ->
                    change.consume()
                    if (dragType == DragType.NONE) {
                        if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y) && kotlin.math.abs(dragAmount.x) > 5f) {
                            dragType = DragType.HORIZONTAL_SEEK
                            startPositionMs = exoPlayer.currentPosition
                            seekPositionMs = startPositionMs
                            totalDragX = 0f
                        } else if (kotlin.math.abs(dragAmount.y) > 5f) {
                            if (change.position.x < size.width / 2) {
                                dragType = DragType.VERTICAL_BRIGHTNESS
                            } else {
                                dragType = DragType.VERTICAL_VOLUME
                            }
                        }
                    }

                    when (dragType) {
                        DragType.HORIZONTAL_SEEK -> {
                            totalDragX += dragAmount.x
                            // Fine, smooth sensitivity: 35ms per pixel (100px = 3.5s)
                            val seekDeltaMs = (totalDragX * 35f).toLong()
                            val duration = exoPlayer.duration.coerceAtLeast(0)
                            seekPositionMs = (startPositionMs + seekDeltaMs).coerceIn(0, duration)
                            
                            val deltaSec = (seekPositionMs - startPositionMs) / 1000
                            val sign = if (deltaSec >= 0) "+" else ""
                            overlaySeekDelta = "[$sign${deltaSec}s]"
                            showOverlay = true
                        }
                        DragType.VERTICAL_BRIGHTNESS -> {
                            val diff = dragAmount.y / 600f
                            brightnessLevel = (brightnessLevel - diff).coerceIn(0.01f, 1f)
                            activity?.window?.attributes = activity?.window?.attributes?.apply {
                                screenBrightness = brightnessLevel
                            }
                            showOverlay = true
                        }
                        DragType.VERTICAL_VOLUME -> {
                            val diff = (dragAmount.y / 400f) * maxVolume
                            volumeLevel = (volumeLevel - diff).coerceIn(0f, maxVolume.toFloat())
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                volumeLevel.toInt(),
                                0
                            )
                            showOverlay = true
                        }
                        else -> {}
                    }
                }
            }
    )

    // Double Tap Overlay Animations
    if (rippleAlpha > 0f) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (doubleTapLeft) Color.White.copy(alpha = rippleAlpha) else Color.Transparent, shape = CircleShape.copy(topStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp))),
                contentAlignment = Alignment.Center
            ) {
                if (doubleTapLeft) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FastRewind, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                        Text("-10 Seconds", color = Color.White)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (doubleTapRight) Color.White.copy(alpha = rippleAlpha) else Color.Transparent, shape = CircleShape.copy(topEnd = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp))),
                contentAlignment = Alignment.Center
            ) {
                if (doubleTapRight) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                        Text("+10 Seconds", color = Color.White)
                    }
                }
            }
        }
    }

    if (showOverlay) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (dragType) {
                        DragType.HORIZONTAL_SEEK -> {
                            val isForward = seekPositionMs >= startPositionMs
                            Icon(
                                imageVector = if (isForward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatTime(seekPositionMs),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = " / ${formatTime(exoPlayer.duration.coerceAtLeast(0))}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            if (overlaySeekDelta.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = overlaySeekDelta,
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        DragType.VERTICAL_BRIGHTNESS -> {
                            Icon(
                                imageVector = Icons.Filled.BrightnessHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${(brightnessLevel * 100).toInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { brightnessLevel },
                                modifier = Modifier.width(100.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                        }
                        DragType.VERTICAL_VOLUME -> {
                            val volPct = (volumeLevel / maxVolume).coerceIn(0f, 1f)
                            Icon(
                                imageVector = if (volPct > 0f) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${(volPct * 100).toInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { volPct },
                                modifier = Modifier.width(100.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
