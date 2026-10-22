package com.spotify.controller

import com.spotify.controller.data.model.DeviceIconType
import com.spotify.controller.data.model.QuickPlayItem
import com.spotify.controller.data.model.QuickPlayType
import com.spotify.controller.data.model.SpotifyDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyDeviceAndQuickPlayTest {

    @Test
    fun testDeviceIconTypeMapping() {
        val phone = SpotifyDevice(id = "1", name = "Phone", type = "Smartphone", isActive = false)
        assertEquals(DeviceIconType.SMARTPHONE, phone.iconType)

        val pc = SpotifyDevice(id = "2", name = "Desktop", type = "Computer", isActive = false)
        assertEquals(DeviceIconType.COMPUTER, pc.iconType)

        val speaker = SpotifyDevice(id = "3", name = "Echo", type = "Speaker", isActive = false)
        assertEquals(DeviceIconType.SPEAKER, speaker.iconType)

        val tv = SpotifyDevice(id = "4", name = "Living Room TV", type = "TV", isActive = false)
        assertEquals(DeviceIconType.TV, tv.iconType)

        val cast = SpotifyDevice(id = "5", name = "Chromecast", type = "CastAudio", isActive = false)
        assertEquals(DeviceIconType.TV, cast.iconType)

        val other = SpotifyDevice(id = "6", name = "Other", type = "Automobile", isActive = false)
        assertEquals(DeviceIconType.SPEAKER, other.iconType)
    }

    @Test
    fun testSpotifyDeviceModel() {
        val device = SpotifyDevice(
            id = "dev_123",
            name = "Living Room Speaker",
            type = "Speaker",
            isActive = true,
            isRestricted = false,
            volumePercent = 80
        )

        assertEquals("dev_123", device.id)
        assertEquals("Living Room Speaker", device.name)
        assertTrue(device.isActive)
        assertFalse(device.isRestricted)
        assertEquals(80, device.volumePercent)
        assertEquals(DeviceIconType.SPEAKER, device.iconType)
    }

    @Test
    fun testQuickPlayItemModel() {
        val onRepeat = QuickPlayItem(
            id = "on_repeat",
            title = "On Repeat",
            subtitle = "Spotify Playlist • Made for You",
            imageUrl = null,
            uri = "spotify:playlist:37i9dQZF1EP6YuccBxUcC1",
            type = QuickPlayType.ON_REPEAT
        )

        assertEquals("On Repeat", onRepeat.title)
        assertEquals(QuickPlayType.ON_REPEAT, onRepeat.type)
        assertEquals("spotify:playlist:37i9dQZF1EP6YuccBxUcC1", onRepeat.uri)

        val topTracksItem = QuickPlayItem(
            id = "top_tracks",
            title = "On Repeat",
            subtitle = "Your top repeated songs • 2 tracks",
            imageUrl = null,
            uri = "spotify:track:abc",
            type = QuickPlayType.ON_REPEAT,
            trackUris = listOf("spotify:track:abc", "spotify:track:def")
        )
        assertEquals(2, topTracksItem.trackUris?.size)
        assertEquals("spotify:track:abc", topTracksItem.trackUris?.get(0))
    }
}
