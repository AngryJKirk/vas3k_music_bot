package dev.storozhenko.music.run

import dev.storozhenko.music.OdesilResponse
import dev.storozhenko.music.getLogger
import dev.storozhenko.music.services.OdesilService
import dev.storozhenko.music.services.SpotifyService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.MessageEntity
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.generics.TelegramClient

class Bot(
    private val botName: String,
    private val spotifyService: SpotifyService,
    private val telegramClient: TelegramClient,
) : LongPollingUpdateConsumer {
    private val logger = getLogger()
    private val odesilService = OdesilService()
    val handler = CoroutineExceptionHandler { _, exception ->
        logger.error("Caught exception: $exception")
    }
    private val coroutine = CoroutineScope(Dispatchers.IO + SupervisorJob() + handler)
    private val helpMessage = getResource("help_message.txt")
    private val chatsAndPlaylistNames = getResource("allow_list.txt")
        .split("\n")
        .map { line -> line.split(" ") }
        .associate { (id, prefix) -> id.toLong() to prefix }

    override fun consume(updates: MutableList<Update>) {
        logger.info("Got ${updates.size} updates")
        updates.forEach {
            runCatching { consume(it) }
                .onFailure { e -> logger.error("Can not process update $it", e) }
        }
    }

    private fun consume(update: Update) {
        if (!update.hasMessage() || !update.message.hasText()) {
            logger.info("Got an update without text")
            return
        }

        val chatId = update.message.chatId
        if (chatId !in chatsAndPlaylistNames) {
            logger.info("Got an update from unauthorized chat")
            return
        }

        if (!update.message.hasEntities()) {
            logger.info("Got an update with no entities")
            return
        }

        if (!spotifyService.isReady()) {
            logger.info("Spotify service is not ready")
            telegramClient.execute(
                SendMessage(
                    chatId.toString(),
                    "Нет авторизации в spotify, пните разраба"
                )
            )
        }

        val entities = update.message.entities
        val command = getCommand(entities)

        if (command != null) {
            logger.info("Processing command $command")
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
        val links = entities
            .filter { entity -> entity.type == "url" }
            .mapNotNull(odesilService::detect)
            .mapIndexed { index, odesilEntity ->
                initialMessage = initialMessage.replace(
                    odesilEntity.messageEntity.text,
                    "[${index + 1}]"
                )
                updatePlaylist(entities, chatId, odesilEntity.odesilResponse)
                mapOdesilResponse(odesilEntity.odesilResponse)
            }


        if (links.isEmpty()) {
            logger.info("No links from Odesil, returning")
            return
        }


        val linksMessage = if (links.size == 1) {
            links.first()
        } else {
            links.mapIndexed { index, l -> "${index + 1}. $l" }.joinToString(separator = "\n\n")
        }

        initialMessage = initialMessage.takeIf { it != "[1]" } ?: ""
        val message =
            "<b>${getUserLink(update.message.from)}</b> написал(а): $initialMessage\n\n$linksMessage"

        logger.info("Sending message: $message")
        telegramClient.execute(SendMessage(chatId.toString(), message).apply { enableHtml(true) })
        logger.info("Deleting original message ${update.message.messageId}")
        telegramClient.execute(DeleteMessage(chatId.toString(), update.message.messageId))
    }

    private fun updatePlaylist(entities: List<MessageEntity>, chatId: Long, odesilResponse: OdesilResponse) {
        coroutine.launch {
            runCatching {
                logger.info("Updating playlist for $odesilResponse")
                val addEntireAlbum = entities
                    .any { it.type == "hashtag" && it.text == "#вплейлист" }
                spotifyService.updatePlaylist(
                    getPlaylistNamePrefix(chatId),
                    odesilResponse,
                    addEntireAlbum
                )
            }
                .onFailure { logger.error("Failed to update playlist:", it) }
        }
    }

    private suspend fun processCommands(update: Update, command: String) {
        when (command) {
            "/current_playlist" -> sendCurrentPlaylist(update)
            "/all_playlists" -> sendAllPlaylists(update)
            "/help" -> sendHelp(update)
        }
    }

    private fun sendHelp(update: Update) {
        logger.info("sending help")
        telegramClient.execute(SendMessage(update.message.chatId.toString(), helpMessage))
    }

    private suspend fun sendCurrentPlaylist(update: Update) {
        logger.info("sending current playlist")
        val chatId = update.message.chatId
        val playlist = spotifyService.getCurrentPlaylist(getPlaylistNamePrefix(chatId))
        telegramClient.execute(
            SendMessage(
                chatId.toString(), "<a href=\"${playlist.url}\">${playlist.name}</a>"
            ).apply {
                replyToMessageId = update.message.messageId
                enableHtml(true)
            }
        )
    }

    private suspend fun sendAllPlaylists(update: Update) {
        logger.info("sending all playlists")
        val playlistNamePrefix = getPlaylistNamePrefix(update.message.chatId)
        val playlists = spotifyService.getAllPlaylists(playlistNamePrefix)
        val message = playlists.mapIndexed { i, p ->
            "${i + 1}. <a href=\"${p.url}\">${p.name}</a>"
        }.joinToString(separator = "\n")
        telegramClient.execute(
            SendMessage(
                update.message.chatId.toString(), message
            ).apply {
                enableHtml(true)
                disableWebPagePreview()
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

    private fun getResource(name: String): String {
        return this::class.java.classLoader.getResource(name)?.readText()
            ?: throw IllegalStateException("Resource $name is not found")
    }

    private fun getUserLink(from: User): String {
        return if (from.userName != null) {
            "@${from.userName}"
        } else {
            val fullName = listOfNotNull(from.firstName, from.lastName).joinToString(" ")
            "<a href=\"tg://user?id=${from.id}\">@$fullName</a>"
        }
    }
}
