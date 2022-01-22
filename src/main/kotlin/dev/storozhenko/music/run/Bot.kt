package dev.storozhenko.music.run

import dev.storozhenko.music.OdesilResponse
import dev.storozhenko.music.getLogger
import dev.storozhenko.music.services.OdesilService
import dev.storozhenko.music.services.SpotifyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.MessageEntity
import org.telegram.telegrambots.meta.api.objects.Update
import java.nio.charset.Charset

class Bot(
    private val token: String,
    private val botName: String,
    private val spotifyService: SpotifyService,
    private val chatsAndPlaylistNames: Map<Long, String>
) : TelegramLongPollingBot() {
    private val odesilService = OdesilService()
    private val coroutine = CoroutineScope(Dispatchers.Default)
    private val logger = getLogger()
    private val helpMessage = this::class.java.classLoader
        .getResourceAsStream("help_message.txt")
        ?.readAllBytes()
        ?.toString(Charset.defaultCharset()) ?: "разработчик еблан забыл добавить хелп"

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

        if (!spotifyService.isReady()) {
            execute(
                SendMessage(
                    chatId.toString(),
                    "Нет авторизации в spotify, пните разраба"
                )
            )
        }

        val entities = update.message.entities
        val command = getCommand(entities)

        if (command != null) {
            coroutine.launch {
                runCatching {
                    processCommands(update, command)
                }.onFailure {
                    logger.error("Can't process command for $update", it)
                }
            }
            return
        }

        var initialMessage = update.message.text
        val urlEntities = entities.filter { entity -> entity.type == "url" }
        if (urlEntities.isEmpty()) {
            return
        }
        var i = 1
        val links = mutableListOf<String>()
        for (entity in urlEntities) {
            val odesilResponse = odesilService.detect(entity.text) ?: continue
            if (entity.offset == 0 && entity.length == initialMessage.length) {
                initialMessage = ""
            } else {
                initialMessage = initialMessage.replace(entity.text, "[$i]")
                i++
            }
            links.add(mapOdesilResponse(odesilResponse))
            coroutine.launch {
                val addEntireAlbum = entities
                    .any { it.type == "hashtag" && it.text == "#вплейлист" }
                runCatching {
                    spotifyService.updatePlaylist(
                        getPlaylistNamePrefix(chatId),
                        odesilResponse,
                        addEntireAlbum
                    )
                }
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
            "/help" -> sendHelp(update)
        }
    }

    private fun sendHelp(update: Update) {
        execute(SendMessage(update.message.chatId.toString(), helpMessage))
    }

    private suspend fun sendCurrentPlaylist(update: Update) {
        val chatId = update.message.chatId
        val playlist = spotifyService.getCurrentPlaylist(getPlaylistNamePrefix(chatId))
        execute(
            SendMessage(
                chatId.toString(), "<a href=\"${playlist.url}\">${playlist.name}</a>"
            ).apply {
                replyToMessageId = update.message.messageId
                enableHtml(true)
            }
        )
    }

    private suspend fun sendAllPlaylists(update: Update) {
        val playlistNamePrefix = getPlaylistNamePrefix(update.message.chatId)
        val playlists = spotifyService.getAllPlaylists(playlistNamePrefix)
        val message = playlists.mapIndexed { i, p ->
            "${i + 1}. <a href=\"${p.url}\">${p.name}</a>"
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

    private val platformOrder = listOf(
        "spotify" to "Spotify",
        "yandex" to "Yandex.Music",
        "appleMusic" to "Apple Music",
        "itunes" to "iTunes",
        "youtube" to "YouTube",
        "youtubeMusic" to "YouTube Music",
        "google" to "Google",
        "googleStore" to "Google Store",
        "pandora" to "Pandora",
        "deezer" to "Deezer",
        "tidal" to "Tidal",
        "amazonStore" to "Amazon Store",
        "amazonMusic" to "Amazon Music",
        "soundcloud" to "SoundCloud",
        "napster" to "Napster",
        "spinrilla" to "Spinrilla",
        "audius" to "Audius"
    )

    private fun mapOdesilResponse(odesilResponse: OdesilResponse): String {
        val odesilEntityData = odesilResponse.entitiesByUniqueId[odesilResponse.entityUniqueId]
        val title = odesilEntityData?.title ?: ""
        val artistName = odesilEntityData?.artistName ?: ""
        val platforms = platformOrder.mapNotNull { (platformId, platformName) ->
            odesilResponse.linksByPlatform[platformId]?.let { platformData -> platformName to platformData }
        }
        val songName = "$artistName - $title\n"
        return songName + platforms.joinToString(separator = " | ")
        { (platformName, platformData) -> "<a href=\"${platformData.url}\">${platformName}</a>" }
    }

    private fun getPlaylistNamePrefix(chatId: Long): String {
        return chatsAndPlaylistNames[chatId]
            ?: throw IllegalStateException("Playlist name is not found for chat $chatId")
    }

    private fun getCommand(entities: List<MessageEntity>): String? {
        val entityText = entities.firstOrNull { entity -> entity.type == "bot_command" }?.text
        return if (entityText != null && (!entityText.contains("@") || entityText.contains(botName)))
            entityText.split("@").first()
        else {
            null
        }
    }
}
