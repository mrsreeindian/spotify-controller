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
        val playlist = QuickPlayItem(
            id = "playlist_1",
            title = "Chill Vibes",
            subtitle = "User • 24 tracks",
            imageUrl = null,
            uri = "spotify:playlist:123",
            type = QuickPlayType.PLAYLIST
        )
        assertEquals("Chill Vibes", playlist.title)
        assertEquals(QuickPlayType.PLAYLIST, playlist.type)
        assertEquals("spotify:playlist:123", playlist.uri)

        val album = QuickPlayItem(
            id = "album_1",
            title = "Abbey Road",
            subtitle = "The Beatles • Album",
            imageUrl = null,
            uri = "spotify:album:456",
            type = QuickPlayType.ALBUM
        )
        assertEquals("Abbey Road", album.title)
        assertEquals(QuickPlayType.ALBUM, album.type)

        val artist = QuickPlayItem(
            id = "artist_1",
            title = "Coldplay",
            subtitle = "Artist",
            imageUrl = null,
            uri = "spotify:artist:789",
            type = QuickPlayType.ARTIST
        )
        assertEquals("Coldplay", artist.title)
        assertEquals(QuickPlayType.ARTIST, artist.type)
    }
}
