package com.spotify.controller.data.remote

import java.io.IOException

class SpotifyRateLimitException(message: String) : IOException(message)

object SpotifyRateLimiter {
    @Volatile
    var rateLimitUntil: Long = 0L
        private set

    val isRateLimited: Boolean
        get() = System.currentTimeMillis() < rateLimitUntil

    val remainingSeconds: Long
        get() = ((rateLimitUntil - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0L)

    fun onRateLimit(retryAfterSeconds: Long?) {
        // Spotify sends Retry-After header in seconds.
        // Default to a safe 20 seconds if header is absent or non-positive.
        val delaySec = if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            retryAfterSeconds
        } else {
            20L
        }
        rateLimitUntil = System.currentTimeMillis() + (delaySec * 1000L)
    }

    fun reset() {
        rateLimitUntil = 0L
    }
}
