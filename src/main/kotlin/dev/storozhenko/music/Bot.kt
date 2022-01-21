package dev.storozhenko.music

import com.adamratzman.spotify.SpotifyClientApi
import com.adamratzman.spotify.models.Playlist
import com.adamratzman.spotify.models.SpotifyTrackUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.Update
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class Bot(
    private val token: String,
    private val botName: String,
    private val spotifyClientHolder: SpotifyClientHolder,
    private val chatsAndPlaylistNames: Map<Long, String>
) : TelegramLongPollingBot() {
    private val odesil = Odesil()
    private val coroutine = CoroutineScope(Dispatchers.Default)
    private val logger = getLogger()

    override fun getBotToken() = token

    override fun getBotUsername() = botName

    override fun onUpdateReceived(update: Update) {
        if (!update.hasMessage() || !update.message.hasText()) {
            return
        }

        val chatId = update.message.chatId
        if (chatId !in chatsAndPlaylistNames) {
            return
        }

        if (!update.message.hasEntities()) {
            return
        }

        if (spotifyClientHolder.client == null) {
            execute(
                SendMessage(
                    chatId.toString(),
                    "Нет авторизации в spotify, пните разраба"
                )
            )
        }

        val commandEntity = update.message.entities.firstOrNull { entity -> entity.type == "bot_command" }?.text

        if (commandEntity != null && (!commandEntity.contains("@") || commandEntity.contains(botName))
        ) {
            coroutine.launch {
                runCatching { processCommands(update, commandEntity.split("@").first()) }
                    .onFailure {
                        logger.error("Can't process command for $update", it)
                    }
            }
            return
        }

        var initialMessage = update.message.text
        val urlEntities = update.message.entities
            .filter { entity -> entity.type == "url" }
        if (urlEntities.isEmpty()) {
            return
        }
        var i = 1
        val links = mutableListOf<String>()
        for (entity in urlEntities) {
            val odesilResponse = odesil.detect(entity.text) ?: continue
            if (entity.offset == 0 && entity.length == initialMessage.length) {
                initialMessage = ""
            } else {
                initialMessage = initialMessage.replace(entity.text, "[$i]")
                i++
            }
            links.add(mapOdesilResponse(odesilResponse))
            coroutine.launch {
                runCatching { updatePlaylist(chatId, odesilResponse) }
                    .onFailure { logger.error("Failed to update playlist:", it) }
            }
        }
        if (links.isEmpty()) {
            return
        }
        val from = update.message.from.userName
        val fromId = update.message.from.id
        val linksMessage = if (links.size == 1) {
            links.first()
        } else {
            links.mapIndexed { index, l -> "${index + 1}. $l" }.joinToString(separator = "\n\n")
        }
        val message =
            "<b><a href=\"tg://user?id=$fromId\">@$from</a></b> написал(а): $initialMessage\n\n$linksMessage"

        execute(SendMessage(chatId.toString(), message).apply { enableHtml(true) })
        execute(DeleteMessage(chatId.toString(), update.message.messageId))
    }

    private suspend fun processCommands(update: Update, command: String) {
        when (command) {
            "/current_playlist" -> sendCurrentPlaylist(update)
            "/all_playlists" -> sendAllPlaylists(update)
        }
    }

    private suspend fun sendCurrentPlaylist(update: Update) {
        val chatId = update.message.chatId
        val currentPlaylist = getCurrentPlaylist(chatId)
        execute(
            SendMessage(
                chatId.toString(), "<a href=\"${getProperPlaylistUrl(currentPlaylist.id)}\">${currentPlaylist.name}</a>"
            ).apply {
                replyToMessageId = update.message.messageId
                enableHtml(true)
            }
        )
    }

    private suspend fun sendAllPlaylists(update: Update) {
        val playlists = getSpotifyClient().playlists.getClientPlaylists()
        val message = playlists.mapIndexed { i, p ->
            "${i + 1}. <a href=\"${getProperPlaylistUrl(p?.id)}\">${p?.name}</a>"
        }.joinToString(separator = "\n")
        execute(
            SendMessage(
                update.message.chatId.toString(), message
            ).apply {
                enableHtml(true)

                replyToMessageId = update.message.messageId
            }
        )
    }

    private fun mapOdesilResponse(odesilResponse: OdesilResponse): String {
        val odesilEntityData = odesilResponse.entitiesByUniqueId[odesilResponse.entityUniqueId]
        val title = odesilEntityData?.title ?: ""
        val artistName = odesilEntityData?.artistName ?: ""
        return "$artistName - $title\n" + odesilResponse
            .linksByPlatform
            .map {
                val platformName = capitalize(it.key)
                val link = it.value.url
                "<a href=\"${link}\">${platformName}</a>"
            }.joinToString(separator = " | ")
    }

    private suspend fun updatePlaylist(chatId: Long, odesilResponse: OdesilResponse) {
        val spotifyUrl = odesilResponse.linksByPlatform["spotify"]?.url ?: return
        if (!spotifyUrl.contains("track")) {
            return
        }
        val client = getSpotifyClient()
        val playlistData = getCurrentPlaylist(chatId)
        val track = SpotifyTrackUri(spotifyUrl.split("/").last())
        if (playlistData.tracks.none { it?.track?.id == track.id }) {
            client.playlists.addPlayableToClientPlaylist(playlistData.id, track)
        }
    }

    private fun getCurrentPlaylistName(chatId: Long): String {
        val prefix = chatsAndPlaylistNames[chatId]
            ?: throw IllegalStateException("Playlist name is not found for chat $chatId")
        val now = LocalDate.now()
        val currentMonth = now.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val currentYear = now.year
        return "$prefix, $currentMonth $currentYear"
    }

    private fun getSpotifyClient(): SpotifyClientApi {
        return spotifyClientHolder.client ?: throw IllegalStateException("Should be checked before")
    }

    private suspend fun getCurrentPlaylist(chatId: Long): Playlist {
        val playlistName = getCurrentPlaylistName(chatId)
        val spotifyClient = getSpotifyClient()

        val playlist = spotifyClient.playlists.getClientPlaylists().find { it?.name == playlistName }?.id
            ?: spotifyClient.playlists.createClientPlaylist(playlistName, public = true).id
        return spotifyClient.playlists.getPlaylist(playlist)
            ?: throw IllegalStateException("Playlist must be there")
    }

    private fun getProperPlaylistUrl(playlistId: String?): String {
        return "https://open.spotify.com/playlist/$playlistId"
    }
}
