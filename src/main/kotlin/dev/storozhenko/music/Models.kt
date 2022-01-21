package dev.storozhenko.music

import com.fasterxml.jackson.annotation.JsonProperty

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val url: String,
    val tracks: Set<String>?
)

class OdesilResponse(
    @JsonProperty("entityUniqueId") val entityUniqueId: String,
    @JsonProperty("linksByPlatform") val linksByPlatform: Map<String, OdesilPlatformData>,
    @JsonProperty("entitiesByUniqueId") val entitiesByUniqueId: Map<String, OdesilEntityData>
)

class OdesilPlatformData(
    @JsonProperty("url") val url: String
)

class OdesilEntityData(
    @JsonProperty("title") val title: String?,
    @JsonProperty("artistName") val artistName: String?
)

class SpotifyCredentials(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String
)

