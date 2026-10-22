package com.spotify.controller.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class SpotifyDevice(
    val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean,
    val isPrivateSession: Boolean = false,
    val isRestricted: Boolean = false,
    val volumePercent: Int = 50,
    val supportsVolume: Boolean = true
) {
    val iconType: DeviceIconType
        get() = when (type.lowercase()) {
            "computer" -> DeviceIconType.COMPUTER
            "smartphone" -> DeviceIconType.SMARTPHONE
            "speaker" -> DeviceIconType.SPEAKER
            "castaudio", "castvideo", "tv" -> DeviceIconType.TV
            else -> DeviceIconType.SPEAKER
        }
}

enum class DeviceIconType {
    SMARTPHONE,
    COMPUTER,
    SPEAKER,
    TV
}
