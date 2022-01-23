package dev.storozhenko.music

import com.fasterxml.jackson.annotation.JsonProperty
import org.telegram.telegrambots.meta.api.objects.MessageEntity

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
    @JsonProperty("artistName") val artistName: String?,
    @JsonProperty("thumbnailUrl") val thumbnailUrl: String?,
    @JsonProperty("thumbnailWidth") val thumbnailWidth: Int?,
    @JsonProperty("thumbnailHeight") val thumbnailHeight: Int?
)

class SpotifyCredentials(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String
)

class OdesilEntity(
    val odesilResponse: OdesilResponse,
    val messageEntity: MessageEntity
)

data class AllowedList(
    @JsonProperty("allowed_list") val allowedEntities: List<AllowedEntity>
)

data class AllowedEntity(
    @JsonProperty("chat_id") val chatId: Long,
    @JsonProperty("playlist_prefix") val playlistPrefix: String
)

