package dev.storozhenko.music

import com.adamratzman.spotify.SpotifyUserAuthorization
import com.adamratzman.spotify.spotifyClientApi
import dev.storozhenko.music.run.Bot
import dev.storozhenko.music.run.Server
import dev.storozhenko.music.services.SpotifyService
import dev.storozhenko.music.services.TokenStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

private val clientId = getEnv("SPOTIFY_CLIENT_ID")
private val clientSecret = getEnv("SPOTIFY_CLIENT_SECRET")
private val redirectUri = getEnv("SPOTIFY_REDIRECT_URL")
private val botToken = getEnv("TELEGRAM_API_TOKEN")
private val botUsername = getEnv("TELEGRAM_BOT_USERNAME")
private val tokenStoragePath = getEnv("TOKEN_STORAGE_PATH")
private val whiteListChatsAndPlaylistNames = mapOf(
    -1001430847921L to "Vas3k.Music",
    138265855L to "Test"
)

private val logger = LoggerFactory.getLogger("main")

private val coroutine = CoroutineScope(Dispatchers.Default)
fun main() {
    val telegramBotsApi = TelegramBotsApi(DefaultBotSession::class.java)
    val spotifyService = SpotifyService()
    val tokenStorage = TokenStorage(tokenStoragePath)
    val server = Server(tokenStorage, spotifyService, clientId, clientSecret, redirectUri)
    coroutine.launch { recreateClient(tokenStorage, spotifyService) }
    telegramBotsApi.registerBot(Bot(botToken, botUsername, spotifyService, whiteListChatsAndPlaylistNames))
    server.run()
}

private suspend fun recreateClient(tokenStorage: TokenStorage, spotifyService: SpotifyService) {
    runCatching {
        val previousToken = tokenStorage.getToken()
        if (previousToken != null) {
            val client = spotifyClientApi(
                clientId,
                clientSecret,
                redirectUri,
                SpotifyUserAuthorization(token = previousToken),
                tokenStorage.tokenRefreshOption()
            )
                .build()
            spotifyService.client = client
            logger.info("Recreated client from stored credentials")
        }
    }.onFailure {
        logger.error("Failed to create client on start", it)
    }
}

private fun getEnv(envName: String): String {
    return System.getenv()[envName] ?: throw IllegalStateException("$envName does not exist")
}