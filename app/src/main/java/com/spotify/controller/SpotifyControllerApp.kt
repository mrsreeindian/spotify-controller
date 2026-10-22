package com.spotify.controller

import android.app.Application
import com.spotify.controller.data.remote.LrclibApiService
import com.spotify.controller.data.remote.SpotifyApiService
import com.spotify.controller.data.remote.SpotifyAuthManager
import com.spotify.controller.data.repository.CredentialsRepository
import com.spotify.controller.data.repository.LyricsRepository
import com.spotify.controller.data.repository.SpotifyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SpotifyControllerApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var okHttpClient: OkHttpClient
        private set

    lateinit var credentialsRepository: CredentialsRepository
        private set

    lateinit var spotifyApiService: SpotifyApiService
        private set

    lateinit var spotifyAuthManager: SpotifyAuthManager
        private set

    lateinit var spotifyRepository: SpotifyRepository
        private set

    lateinit var lrclibApiService: LrclibApiService
        private set

    lateinit var lyricsRepository: LyricsRepository
        private set

    override fun onCreate() {
        super.onCreate()

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        credentialsRepository = CredentialsRepository(this)
        spotifyApiService = SpotifyApiService(okHttpClient)
        spotifyAuthManager = SpotifyAuthManager(this, credentialsRepository, okHttpClient)
        spotifyRepository = SpotifyRepository(
            spotifyApiService = spotifyApiService,
            spotifyAuthManager = spotifyAuthManager,
            credentialsRepository = credentialsRepository,
            appScope = appScope
        )

        lrclibApiService = LrclibApiService(okHttpClient)
        lyricsRepository = LyricsRepository(lrclibApiService, okHttpClient)

        // Initialize and start real-time home screen widget sync
        com.spotify.controller.widget.LyricsWidgetProvider.startWidgetSync(this)

        // Reactively push playback state changes to all widgets immediately
        appScope.launch {
            spotifyRepository.playbackState.collect {
                com.spotify.controller.widget.LyricsWidgetProvider.updateAllWidgets(this@SpotifyControllerApp)
            }
        }
    }
}
