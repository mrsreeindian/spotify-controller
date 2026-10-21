package com.spotify.controller.data.model

enum class RepeatMode(val apiValue: String) {
    OFF("off"),
    CONTEXT("context"), // Repeat Playlist / Album
    TRACK("track");    // Repeat Current Track

    fun next(): RepeatMode = when (this) {
        OFF -> CONTEXT
        CONTEXT -> TRACK
        TRACK -> OFF
    }

    companion object {
        fun fromApiValue(value: String?): RepeatMode = when (value?.lowercase()) {
            "track" -> TRACK
            "context" -> CONTEXT
            else -> OFF
        }
    }
}
