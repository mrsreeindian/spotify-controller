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
    private var currentToken: String? = null
    private var fetchJob: Job? = null

    fun onTrackChanged(track: Track?, spotifyToken: String? = null) {
        if (track == null || track.id.isBlank()) {
            _lyrics.value = Lyrics.EMPTY
            currentTrack = null
            currentToken = null
            return
        }

        if (track.id == currentTrack?.id) {
            return
        }

        currentTrack = track
        currentToken = spotifyToken
        fetchLyrics(track, spotifyToken)
    }

    fun retry() {
        if (currentTrack != null) {
            fetchLyrics(currentTrack!!, currentToken)
        } else {
            loadDemoLyrics()
        }
    }

    fun fetchLyrics(track: Track, spotifyToken: String? = null) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            val result = lyricsRepository.getLyrics(track, spotifyToken)
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

    fun loadDemoLyrics() {
        val demoLrc = """
            [00:00.00] ♪ Instrumental Intro ♪
            [00:06.00] <00:06.00>The <00:06.40>club <00:06.80>isn't <00:07.10>the <00:07.40>best <00:07.80>place <00:08.20>to <00:08.50>find <00:08.80>a <00:09.20>lover
            [00:09.65] <00:09.65>The <00:10.10>club <00:10.40>isn't <00:10.80>the <00:11.10>best <00:11.40>place <00:11.80>to <00:12.00>find <00:12.20>a <00:12.40>lover
            [00:12.50] <00:12.50>So <00:12.80>the <00:13.10>bar <00:13.40>is <00:13.70>where <00:14.00>I <00:14.30>go
            [00:14.81] <00:14.81>Me <00:15.10>and <00:15.40>my <00:15.70>friends <00:16.00>at <00:16.30>the <00:16.60>table <00:16.90>doing <00:17.20>shots
            [00:17.50] <00:17.50>Drinking <00:18.00>fast <00:18.30>and <00:18.60>then <00:18.90>we <00:19.20>talk <00:19.50>slow
            [00:19.70] <00:19.70>You <00:20.00>come <00:20.30>over <00:20.70>and <00:21.00>start <00:21.30>up <00:21.60>a <00:21.90>conversation <00:22.40>with <00:22.70>just <00:23.00>me
            [00:23.30] <00:23.30>And <00:23.60>trust <00:23.90>me <00:24.20>I'll <00:24.50>give <00:24.80>it <00:25.10>a <00:25.40>chance <00:25.70>now
            [00:26.00] • • •
            [00:29.83] <00:29.83>Girl, <00:30.30>you <00:30.60>know <00:30.90>I <00:31.20>want <00:31.50>your <00:31.80>love
            [00:32.22] <00:32.22>Your <00:32.60>love <00:33.00>was <00:33.40>handmade <00:33.90>for <00:34.30>somebody <00:34.70>like <00:35.00>me
            [00:35.23] <00:35.23>Come <00:35.60>on <00:35.90>now, <00:36.30>follow <00:36.70>my <00:37.00>lead
            [00:37.31] <00:37.31>I <00:37.60>may <00:37.90>be <00:38.20>crazy, <00:38.60>don't <00:39.00>mind <00:39.30>me
            [00:39.64] <00:39.64>Say, <00:40.00>"Boy, <00:40.40>let's <00:40.70>not <00:41.00>talk <00:41.40>too <00:41.80>much"
            [00:42.28] <00:42.28>Grab <00:42.60>on <00:42.90>my <00:43.20>waist <00:43.60>and <00:44.00>put <00:44.30>that <00:44.70>body <00:45.00>on <00:45.30>me
            [00:45.50] <00:45.50>Come <00:46.00>on <00:46.40>now, <00:46.80>follow <00:47.20>my <00:47.60>lead
            [00:48.00] • • •
            [00:50.75] <00:50.75>I'm <00:51.10>in <00:51.40>love <00:51.80>with <00:52.20>the <00:52.60>shape <00:53.00>of <00:53.30>you
            [00:53.50] <00:53.50>We <00:53.90>push <00:54.30>and <00:54.70>pull <00:55.10>like <00:55.40>a <00:55.70>magnet <00:56.10>do
            [00:56.50] <00:56.50>Although <00:57.00>my <00:57.40>heart <00:57.80>is <00:58.20>falling <00:58.60>too
            [00:58.80] <00:58.80>I'm <00:59.20>in <00:59.60>love <01:00.00>with <01:00.40>your <01:00.80>body
        """.trimIndent()
        val parsed = com.spotify.controller.data.repository.LrcParser.parse(demoLrc)
        _lyrics.value = Lyrics(
            trackId = "demo_track",
            title = "Shape of You (Spicy Karaoke Demo)",
            artist = "Ed Sheeran",
            lines = parsed,
            isSynced = true,
            provider = "Spicy Lyrics Engine",
            hasWordSync = true,
            hasExactWordSync = true
        )
    }
}
