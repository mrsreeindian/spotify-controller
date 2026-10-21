package com.spotify.controller

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.spotify.controller.ui.screens.MainPlayerScreen
import com.spotify.controller.ui.viewmodel.LyricsViewModel
import com.spotify.controller.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var app: SpotifyControllerApp
    private lateinit var playerViewModel: PlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        app = application as SpotifyControllerApp

        val lyricsViewModel = LyricsViewModel(app.lyricsRepository)
        playerViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(
                        application = app,
                        spotifyRepository = app.spotifyRepository,
                        credentialsRepository = app.credentialsRepository,
                        spotifyAuthManager = app.spotifyAuthManager,
                        lyricsViewModel = lyricsViewModel
                    ) as T
                }
            }
        )[PlayerViewModel::class.java]

        // Handle initial orientation from persisted preference
        lifecycleScope.launch {
            val mode = app.credentialsRepository.orientationModeFlow.first()
            setOrientation(mode == "landscape")
        }

        // Hide top notification/status bar for full immersion
        hideSystemBars()

        // Handle possible OAuth redirect on initial launch
        handleIntent(intent)

        setContent {
            MainPlayerScreen(
                viewModel = playerViewModel,
                onToggleOrientation = { isLandscape ->
                    setOrientation(isLandscape)
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "spotifycontroller" && uri.host == "callback") {
            lifecycleScope.launch {
                val creds = app.credentialsRepository.credentialsFlow.first()
                val result = app.spotifyAuthManager.handleRedirectUri(uri, creds.clientId, creds.clientSecret)
                result.onSuccess {
                    Toast.makeText(this@MainActivity, "Connected to Spotify!", Toast.LENGTH_SHORT).show()
                    app.spotifyRepository.pollPlaybackState()
                }.onFailure { err ->
                    Toast.makeText(this@MainActivity, "Login Failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setOrientation(isLandscape: Boolean) {
        requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Hide both top status bar (notification bar) and bottom navigation bar for full immersive mode
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        app.spotifyRepository.startPolling()
    }

    override fun onPause() {
        super.onPause()
        app.spotifyRepository.stopPolling()
    }
}
