package dev.storozhenko.music.services

import com.adamratzman.spotify.SpotifyClientApi
import com.adamratzman.spotify.models.PagingObject
import com.adamratzman.spotify.models.PlaylistTrack
import com.adamratzman.spotify.models.SpotifyTrackUri
import dev.storozhenko.music.OdesilResponse
import dev.storozhenko.music.SpotifyPlaylist
import dev.storozhenko.music.getLogger
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class SpotifyService(
    var client: SpotifyClientApi? = null
) {
    private val logger = getLogger()
    fun isReady(): Boolean {
        return client != null
    }

    suspend fun getCurrentPlaylist(prefix: String): SpotifyPlaylist {
        val playlistName = getCurrentPlaylistName(prefix)
        val spotifyClient = getSpotifyClient()

        val playlist = spotifyClient.playlists.getClientPlaylists().find { it?.name == playlistName }?.id
            ?: spotifyClient.playlists.createClientPlaylist(playlistName, public = true).id
        val playlistData = spotifyClient.playlists.getPlaylist(playlist)
            ?: throw IllegalStateException("Playlist must be there")
        return SpotifyPlaylist(
            playlistData.id,
            playlistData.name,
            getProperPlaylistUrl(playlistData.id),
            mapTracks(playlistData.tracks)
        )
    }

    suspend fun getAllPlaylists(prefix: String): List<SpotifyPlaylist> {
        return getSpotifyClient()
            .playlists
            .getClientPlaylists()
            .toList()
            .filterNotNull()
            .filter { it.name.startsWith(prefix) }
            .map {
                SpotifyPlaylist(
                    it.id,
                    it.name,
                    getProperPlaylistUrl(it.id),
                    null
                )
            }
    }

    suspend fun updatePlaylist(prefix: String, odesilResponse: OdesilResponse, addEntireAlbum: Boolean) {
        val spotifyUrl = odesilResponse.linksByPlatform["spotify"]?.url ?: return
        val playableId = spotifyUrl.split("/").last()
        val currentPlaylist = getCurrentPlaylist(prefix)
        if (spotifyUrl.contains("/track/")) {
            addTracks(playableId, currentPlaylist = currentPlaylist)
        } else if (spotifyUrl.contains("/album/")) {
            addAlbum(playableId, addEntireAlbum, currentPlaylist)
        }
    }

    private suspend fun addAlbum(albumId: String, addEntireAlbum: Boolean, currentPlaylist: SpotifyPlaylist) {
        val trackIds = getSpotifyClient()
            .albums
            .getAlbumTracks(albumId)
            .mapNotNull { track -> track?.id }
            .toTypedArray()
        if (addEntireAlbum) {
            logger.info("Adding entire album")
            addTracks(*trackIds, currentPlaylist = currentPlaylist)
        } else {
            logger.info("Adding most popular song from the album")
            val mostPopular = getSpotifyClient()
                .tracks
                .getTracks(*trackIds)
                .maxByOrNull { it?.popularity ?: 0 }

            if (mostPopular != null) {
                addTracks(mostPopular.id, currentPlaylist = currentPlaylist)
            }
        }
    }

    private suspend fun addTracks(vararg trackIds: String, currentPlaylist: SpotifyPlaylist) {
        val existingTracks = currentPlaylist.tracks ?: throw IllegalStateException("Tracks must be present")

        logger.info("${trackIds.size} track(s) to be added")

        val newTracks = trackIds
            .filterNot { trackId -> existingTracks.contains(trackId) }
            .map(::SpotifyTrackUri)
            .toTypedArray()

        logger.info("${trackIds.size - newTracks.size} duplicates found")

        if (newTracks.isNotEmpty()) {
            getSpotifyClient()
                .playlists
                .addPlayablesToClientPlaylist(currentPlaylist.id, *newTracks)
            logger.info("Added ${newTracks.size} track(s)")
        } else {
            logger.info("No tracks will be added")
        }
    }

    private fun getProperPlaylistUrl(playlistId: String?): String {
        return "https://open.spotify.com/playlist/$playlistId"
    }

    private fun mapTracks(tracks: PagingObject<PlaylistTrack>): Set<String> {
        return tracks.mapNotNull { it?.track?.id }.toSet()
    }

    private fun getCurrentPlaylistName(prefix: String): String {
        val now = LocalDate.now()
        val currentMonth = now.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val currentYear = now.year
        return "$prefix, $currentMonth $currentYear"
    }

    private fun getSpotifyClient(): SpotifyClientApi {
        return client ?: throw IllegalStateException("Should be checked before")
    }
}