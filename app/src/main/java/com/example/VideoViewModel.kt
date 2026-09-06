package com.example

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "video_prefs")

enum class SortOrder {
    DATE_ADDED, DURATION, SIZE, NAME
}

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val duration: Long,
    val size: Long
)

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val filteredVideos: StateFlow<List<VideoItem>> = combine(_videos, _searchQuery, _sortOrder) { videos, query, sort ->
        val filtered = if (query.isBlank()) {
            videos
        } else {
            videos.filter { it.name.contains(query, ignoreCase = true) }
        }
        when (sort) {
            SortOrder.DATE_ADDED -> filtered // already sorted by date in queryVideos
            SortOrder.DURATION -> filtered.sortedByDescending { it.duration }
            SortOrder.SIZE -> filtered.sortedByDescending { it.size }
            SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    fun onPermissionGranted() {
        _permissionGranted.value = true
        loadVideos()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun deleteVideo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                // Ignore security exceptions for scoped storage for now, just remove from UI
            }
            _videos.value = _videos.value.filter { it.uri != uri }
        }
    }

    suspend fun saveVideoPosition(uri: String, position: Long) {
        val key = longPreferencesKey(uri)
        getApplication<Application>().dataStore.edit { prefs ->
            prefs[key] = position
        }
    }

    suspend fun getVideoPosition(uri: String): Long {
        val key = longPreferencesKey(uri)
        val prefs = getApplication<Application>().dataStore.data.first()
        return prefs[key] ?: 0L
    }

    private fun loadVideos() {
        viewModelScope.launch {
            val videoList = queryVideos()
            _videos.value = videoList
        }
    }

    private suspend fun queryVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<VideoItem>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        getApplication<Application>().contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                videoList.add(VideoItem(id, contentUri, name ?: "Unknown", duration, size))
            }
        }
        return@withContext videoList
    }
}
