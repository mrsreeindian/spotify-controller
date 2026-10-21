package com.spotify.controller.data.model

data class PlaybackState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val lastSyncTimestamp: Long = 0L,
    val volumePercent: Int = 50,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val deviceName: String? = null,
    val deviceId: String? = null,
    val isDeviceActive: Boolean = false
) {
    /**
     * Calculates the estimated real-time progress using client-side monotonic clock
     * to ensure smooth second-by-second progress updates between remote polling cycles.
     */
    fun getInterpolatedProgress(nowElapsed: Long = android.os.SystemClock.elapsedRealtime()): Long {
        if (!isPlaying || lastSyncTimestamp == 0L || track == null) {
            return progressMs
        }
        val elapsedSinceSync = nowElapsed - lastSyncTimestamp
        val computed = progressMs + elapsedSinceSync
        return computed.coerceIn(0L, track.durationMs)
    }

    companion object {
        val IDLE = PlaybackState(
            track = null,
            isPlaying = false,
            progressMs = 0L,
            lastSyncTimestamp = 0L,
            volumePercent = 50,
            isShuffle = false,
            repeatMode = RepeatMode.OFF
        )
    }
}
