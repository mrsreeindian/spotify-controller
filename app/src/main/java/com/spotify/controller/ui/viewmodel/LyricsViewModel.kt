package com.spotify.controller.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.controller.data.model.Lyrics
import com.spotify.controller.data.model.Track
import com.spotify.controller.data.repository.LyricsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LyricsViewModel(
    private val lyricsRepository: LyricsRepository
) : ViewModel() {

    private val _lyrics = MutableStateFlow(Lyrics.EMPTY)
    val lyrics: StateFlow<Lyrics> = _lyrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentTrackId: String? = null
    private var fetchJob: Job? = null

    fun onTrackChanged(track: Track?) {
        if (track == null || track.id.isBlank()) {
            _lyrics.value = Lyrics.EMPTY
            currentTrackId = null
            return
        }

        if (track.id == currentTrackId) {
            return
        }

        currentTrackId = track.id
        fetchLyrics(track)
    }

    fun fetchLyrics(track: Track) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            val result = lyricsRepository.getLyrics(track)
            result.onSuccess {
                _lyrics.value = it
            }.onFailure {
                _lyrics.value = Lyrics(
                    trackId = track.id,
                    title = track.name,
                    artist = track.artistNames,
                    lines = emptyList(),
                    isSynced = false,
                    plainLyrics = null
                )
            }
            _isLoading.value = false
        }
    }
}
