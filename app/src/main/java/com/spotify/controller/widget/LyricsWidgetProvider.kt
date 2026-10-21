package com.spotify.controller.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import coil.imageLoader
import coil.request.ImageRequest
import com.spotify.controller.MainActivity
import com.spotify.controller.R
import com.spotify.controller.SpotifyControllerApp
import com.spotify.controller.data.model.Lyrics
import com.spotify.controller.data.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resizable Home Screen Widget that displays real-time synchronized lyrics,
 * responsive multi-line lyrics stream, album art, and Spotify playback controls.
 */
class LyricsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val app = context.applicationContext as? SpotifyControllerApp ?: return
        app.appScope.launch {
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val app = context.applicationContext as? SpotifyControllerApp ?: return
        app.appScope.launch {
            updateAppWidget(context, appWidgetManager, appWidgetId, newOptions)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext as? SpotifyControllerApp ?: return

        when (intent.action) {
            ACTION_TOGGLE_PLAY -> {
                app.appScope.launch {
                    app.spotifyRepository.togglePlayPause()
                    updateAllWidgets(context)
                }
            }
            ACTION_NEXT -> {
                app.appScope.launch {
                    app.spotifyRepository.nextTrack()
                    updateAllWidgets(context)
                }
            }
            ACTION_PREV -> {
                app.appScope.launch {
                    app.spotifyRepository.previousTrack()
                    updateAllWidgets(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_PLAY = "com.spotify.controller.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.spotify.controller.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.spotify.controller.widget.ACTION_PREV"

        private var tickerJob: Job? = null
        private var lastLoadedTrackId: String? = null
        private var cachedLyrics: Lyrics = Lyrics.EMPTY
        private var cachedAlbumArt: Bitmap? = null

        /**
         * Starts the background widget ticker that keeps the active lyric line in sync
         * while music is playing.
         */
        fun startWidgetSync(app: SpotifyControllerApp) {
            if (tickerJob?.isActive == true) return
            tickerJob = app.appScope.launch(Dispatchers.Default) {
                while (isActive) {
                    val state = app.spotifyRepository.playbackState.value
                    if (state.isPlaying && state.track != null) {
                        ensureLyricsAndArt(app, state)
                        updateAllWidgets(app)
                        delay(1000L) // Resilient 1s tick for home screen widget
                    } else {
                        delay(2500L) // Relaxed polling when idle
                    }
                }
            }
        }

        private suspend fun ensureLyricsAndArt(app: SpotifyControllerApp, state: PlaybackState) {
            val track = state.track ?: return
            if (track.id != lastLoadedTrackId) {
                lastLoadedTrackId = track.id
                // Load lyrics
                val token = app.credentialsRepository.credentialsFlow.first().accessToken
                val lyricsResult = app.lyricsRepository.getLyrics(track, token)
                cachedLyrics = lyricsResult.getOrElse { Lyrics.EMPTY }

                // Load album art bitmap
                if (!track.albumArtUrl.isNullOrBlank()) {
                    try {
                        val req = ImageRequest.Builder(app)
                            .data(track.albumArtUrl)
                            .allowHardware(false)
                            .build()
                        val res = app.imageLoader.execute(req)
                        cachedAlbumArt = (res.drawable as? BitmapDrawable)?.bitmap
                    } catch (e: Exception) {
                        cachedAlbumArt = null
                    }
                } else {
                    cachedAlbumArt = null
                }
            }
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, LyricsWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(component)
            if (appWidgetIds.isEmpty()) return

            val app = context.applicationContext as? SpotifyControllerApp ?: return
            app.appScope.launch {
                for (id in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
        }

        suspend fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle? = null
        ) = withContext(Dispatchers.Default) {
            val app = context.applicationContext as? SpotifyControllerApp ?: return@withContext
            val playbackState = app.spotifyRepository.playbackState.value
            val track = playbackState.track

            // Determine widget dimensions
            val widgetOptions = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeightDp = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)

            // Calculate current live position
            val currentPos = if (playbackState.isPlaying && playbackState.lastSyncTimestamp > 0L) {
                playbackState.progressMs + (SystemClock.elapsedRealtime() - playbackState.lastSyncTimestamp)
            } else {
                playbackState.progressMs
            }

            val lyrics = cachedLyrics
            val activeIndex = if (lyrics.lines.isNotEmpty()) {
                lyrics.getActiveLineIndex(currentPos)
            } else -1

            // Choose layout based on height
            val remoteViews = when {
                minHeightDp < 100 -> buildCompactViews(context, playbackState, lyrics, activeIndex)
                minHeightDp in 100..185 -> buildStandardViews(context, playbackState, lyrics, activeIndex)
                else -> buildExpandedViews(context, playbackState, lyrics, activeIndex)
            }

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }

        private fun buildCompactViews(
            context: Context,
            playbackState: PlaybackState,
            lyrics: Lyrics,
            activeIndex: Int
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lyrics_compact)
            val track = playbackState.track

            views.setTextViewText(R.id.widget_compact_title, track?.name ?: "No Track Playing")
            views.setTextViewText(R.id.widget_compact_artist, track?.artistNames ?: "Spotify Controller")

            val playIcon = if (playbackState.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.widget_compact_btn_play_pause, playIcon)

            val activeLineText = when {
                track == null -> "Connect Spotify to start"
                lyrics.lines.isEmpty() -> if (!lyrics.plainLyrics.isNullOrBlank()) lyrics.plainLyrics else "No lyrics available"
                activeIndex in lyrics.lines.indices -> lyrics.lines[activeIndex].text
                else -> "• • •"
            }
            views.setTextViewText(R.id.widget_compact_active_lyric, activeLineText)

            if (cachedAlbumArt != null) {
                views.setImageViewBitmap(R.id.widget_compact_album_art, cachedAlbumArt)
            } else {
                views.setImageViewResource(R.id.widget_compact_album_art, R.drawable.widget_album_art_placeholder)
            }

            // Pending Intents
            attachControlIntents(context, views, R.id.widget_compact_btn_play_pause)
            attachLyricsIntent(context, views, R.id.widget_compact_lyrics_container)
            attachAppIntent(context, views, R.id.widget_root)

            return views
        }

        private fun buildStandardViews(
            context: Context,
            playbackState: PlaybackState,
            lyrics: Lyrics,
            activeIndex: Int
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lyrics_standard)
            val track = playbackState.track

            views.setTextViewText(R.id.widget_track_title, track?.name ?: "No Track Playing")
            views.setTextViewText(R.id.widget_artist_name, track?.artistNames ?: "Spotify Controller")

            val playIcon = if (playbackState.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.widget_btn_play_pause, playIcon)

            // Lyrics
            val prevLineText = if (activeIndex > 0 && activeIndex - 1 in lyrics.lines.indices) {
                lyrics.lines[activeIndex - 1].text
            } else ""

            val activeLineText = when {
                track == null -> "Connect Spotify to start"
                lyrics.lines.isEmpty() -> if (!lyrics.plainLyrics.isNullOrBlank()) lyrics.plainLyrics else "Searching lyrics..."
                activeIndex in lyrics.lines.indices -> lyrics.lines[activeIndex].text
                else -> "• • •"
            }

            val nextLineText = if (activeIndex >= 0 && activeIndex + 1 in lyrics.lines.indices) {
                lyrics.lines[activeIndex + 1].text
            } else ""

            views.setTextViewText(R.id.widget_lyric_prev, prevLineText)
            views.setTextViewText(R.id.widget_lyric_active, activeLineText)
            views.setTextViewText(R.id.widget_lyric_next, nextLineText)

            if (cachedAlbumArt != null) {
                views.setImageViewBitmap(R.id.widget_album_art, cachedAlbumArt)
            } else {
                views.setImageViewResource(R.id.widget_album_art, R.drawable.widget_album_art_placeholder)
            }

            // Pending Intents
            attachControlIntents(context, views, R.id.widget_btn_play_pause, R.id.widget_btn_next, R.id.widget_btn_prev)
            attachLyricsIntent(context, views, R.id.widget_lyrics_container)
            attachAppIntent(context, views, R.id.widget_track_info_container)

            return views
        }

        private fun buildExpandedViews(
            context: Context,
            playbackState: PlaybackState,
            lyrics: Lyrics,
            activeIndex: Int
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lyrics_expanded)
            val track = playbackState.track

            views.setTextViewText(R.id.widget_exp_track_title, track?.name ?: "No Track Playing")
            views.setTextViewText(R.id.widget_exp_artist_name, track?.artistNames ?: "Spotify Controller")

            val playIcon = if (playbackState.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.widget_exp_btn_play_pause, playIcon)

            val p2 = if (activeIndex >= 2 && activeIndex - 2 in lyrics.lines.indices) lyrics.lines[activeIndex - 2].text else ""
            val p1 = if (activeIndex >= 1 && activeIndex - 1 in lyrics.lines.indices) lyrics.lines[activeIndex - 1].text else ""
            val act = when {
                track == null -> "Connect Spotify to start"
                lyrics.lines.isEmpty() -> if (!lyrics.plainLyrics.isNullOrBlank()) lyrics.plainLyrics else "Searching lyrics..."
                activeIndex in lyrics.lines.indices -> lyrics.lines[activeIndex].text
                else -> "• • •"
            }
            val n1 = if (activeIndex >= 0 && activeIndex + 1 in lyrics.lines.indices) lyrics.lines[activeIndex + 1].text else ""
            val n2 = if (activeIndex >= 0 && activeIndex + 2 in lyrics.lines.indices) lyrics.lines[activeIndex + 2].text else ""

            views.setTextViewText(R.id.widget_exp_lyric_prev_2, p2)
            views.setTextViewText(R.id.widget_exp_lyric_prev_1, p1)
            views.setTextViewText(R.id.widget_exp_lyric_active, act)
            views.setTextViewText(R.id.widget_exp_lyric_next_1, n1)
            views.setTextViewText(R.id.widget_exp_lyric_next_2, n2)

            if (cachedAlbumArt != null) {
                views.setImageViewBitmap(R.id.widget_exp_album_art, cachedAlbumArt)
            } else {
                views.setImageViewResource(R.id.widget_exp_album_art, R.drawable.widget_album_art_placeholder)
            }

            attachControlIntents(context, views, R.id.widget_exp_btn_play_pause, R.id.widget_exp_btn_next, R.id.widget_exp_btn_prev)
            attachLyricsIntent(context, views, R.id.widget_exp_lyrics_container)
            attachAppIntent(context, views, R.id.widget_exp_track_info)

            return views
        }

        private fun attachControlIntents(
            context: Context,
            views: RemoteViews,
            playPauseId: Int,
            nextId: Int? = null,
            prevId: Int? = null
        ) {
            val playIntent = Intent(context, LyricsWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_PLAY
            }
            val playPendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(playPauseId, playPendingIntent)

            if (nextId != null) {
                val nextIntent = Intent(context, LyricsWidgetProvider::class.java).apply {
                    action = ACTION_NEXT
                }
                val nextPendingIntent = PendingIntent.getBroadcast(
                    context,
                    102,
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(nextId, nextPendingIntent)
            }

            if (prevId != null) {
                val prevIntent = Intent(context, LyricsWidgetProvider::class.java).apply {
                    action = ACTION_PREV
                }
                val prevPendingIntent = PendingIntent.getBroadcast(
                    context,
                    103,
                    prevIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(prevId, prevPendingIntent)
            }
        }

        private fun attachLyricsIntent(context: Context, views: RemoteViews, viewId: Int) {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("OPEN_LYRICS", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(viewId, pendingIntent)
        }

        private fun attachAppIntent(context: Context, views: RemoteViews, viewId: Int) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                202,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(viewId, pendingIntent)
        }
    }
}
