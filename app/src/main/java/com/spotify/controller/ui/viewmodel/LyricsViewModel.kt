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

    private var currentTrack: Track? = null
    private var fetchJob: Job? = null

    fun onTrackChanged(track: Track?) {
        if (track == null || track.id.isBlank()) {
            _lyrics.value = Lyrics.EMPTY
            currentTrack = null
            return
        }

        if (track.id == currentTrack?.id && _lyrics.value != Lyrics.EMPTY) {
            return
        }

        currentTrack = track
        fetchLyrics(track)
    }

    fun retry() {
        if (currentTrack != null) {
            fetchLyrics(currentTrack!!)
        } else {
            loadDemoLyrics()
        }
    }

    fun loadDemoLyrics() {
        val demoLrc = """
            [00:00.00] ♪ Instrumental Intro ♪
            [00:09.65] The club isn't the best place to find a lover
            [00:12.10] So the bar is where I go
            [00:14.81] Me and my friends at the table doing shots
            [00:17.04] Drinking fast, and then we talk slow
            [00:19.70] You come over and start up a conversation with just me
            [00:22.24] And trust me, I'll give it a chance now
            [00:24.42] Take my hand, stop, put "Van the Man" on the jukebox
            [00:27.27] And then we start to dance
            [00:29.83] Girl, you know I want your love
            [00:32.22] Your love was handmade for somebody like me
            [00:35.23] Come on now, follow my lead
            [00:37.31] I may be crazy, don't mind me
            [00:39.64] Say, "Boy, let's not talk too much"
            [00:42.28] Grab on my waist and put that body on me
            [00:45.30] Come on now, follow my lead
            [00:50.75] I'm in love with the shape of you
            [00:53.14] We push and pull like a magnet do
            [00:55.69] Although my heart is falling too
            [00:58.14] I'm in love with your body
            [01:00.61] Last night you were in my room
            [01:03.19] And now my bed sheets smell like you
            [01:05.35] Every day discovering something brand new
            [01:08.02] Oh, I'm in love with your body
        """.trimIndent()
        val parsed = com.spotify.controller.data.repository.LrcParser.parse(demoLrc)
        _lyrics.value = Lyrics(
            trackId = "demo_track",
            title = "Shape of You (Demo)",
            artist = "Ed Sheeran",
            lines = parsed,
            isSynced = true,
            provider = "Spicy Lyrics (Demo Mode)"
        )
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
