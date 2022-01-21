package dev.storozhenko.music

import com.adamratzman.spotify.SpotifyApiOptions
import com.adamratzman.spotify.SpotifyScope
import com.adamratzman.spotify.SpotifyUserAuthorization
import com.adamratzman.spotify.getSpotifyAuthorizationUrl
import com.adamratzman.spotify.spotifyClientApi
import io.ktor.application.call
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.getOrFail
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
    val spotifyClientHolder = SpotifyClientHolder()
    val tokenStorage = TokenStorage(tokenStoragePath)
    val previousToken = tokenStorage.getToken()
    val tokenRefreshOption: SpotifyApiOptions.() -> Unit = { afterTokenRefresh = { tokenStorage.saveToken(it.token) } }
    coroutine.launch {
        runCatching {
            if (previousToken != null) {
                val client = spotifyClientApi(
                    clientId,
                    clientSecret,
                    redirectUri,
                    SpotifyUserAuthorization(token = previousToken),
                    tokenRefreshOption
                )
                    .build()
                spotifyClientHolder.client = client
                logger.info("Recreated client from stored creds")
            }
        }.onFailure {
            logger.error("Failed to create client on start", it)
        }
    }

    telegramBotsApi.registerBot(Bot(botToken, botUsername, spotifyClientHolder, whiteListChatsAndPlaylistNames))
    embeddedServer(Netty, port = 9999) {
        routing {
            get("/") {
                val url = getSpotifyAuthorizationUrl(
                    SpotifyScope.PLAYLIST_READ_PRIVATE,
                    SpotifyScope.PLAYLIST_MODIFY_PRIVATE,
                    SpotifyScope.PLAYLIST_MODIFY_PUBLIC,
                    SpotifyScope.PLAYLIST_MODIFY_PRIVATE,
                    SpotifyScope.PLAYLIST_READ_COLLABORATIVE,
                    clientId = clientId,
                    redirectUri = redirectUri
                )
                call.respondRedirect(url, permanent = false)
            }
            get("/callback") {
                val authCode = call.request.queryParameters.getOrFail("code")
                val client = spotifyClientApi(
                    clientId,
                    clientSecret,
                    redirectUri,
                    SpotifyUserAuthorization(authorizationCode = authCode),
                    tokenRefreshOption
                ).build()
                tokenStorage.saveToken(client.token)
                spotifyClientHolder.client = client
                client.token

            }
        }
    }.start(wait = true)
}

private fun getEnv(envName: String): String {
    return System.getenv()[envName] ?: throw IllegalStateException("$envName does not exist")
}