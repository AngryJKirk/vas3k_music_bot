package dev.storozhenko.music.services

import com.adamratzman.spotify.SpotifyApiOptions
import com.adamratzman.spotify.SpotifyClientApi
import com.adamratzman.spotify.models.Token
import com.adamratzman.spotify.refreshSpotifyClientToken
import dev.storozhenko.music.getLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.IllegalStateException
import kotlin.io.path.Path

class TokenStorage(private val tokenStoragePath: String) {
    private val logger = getLogger()

    fun saveToken(token: Token) {
        getFile().writeText(Json.encodeToString(Token.serializer(), token))
        logger.info("Token saved")
    }

    fun getToken(): Token? {
        val file = getFile()
        if (!file.exists()) {
            logger.info("Token does not exist")
            return null
        }
        return runCatching {
            Json.decodeFromString(Token.serializer(), file.readText())
        }.getOrElse { exception ->
            logger.error("Token deserialization error", exception)
            file.delete()
            null
        }
    }

    fun tokenRefreshOption(): SpotifyApiOptions.() -> Unit =
        {
            refreshTokenProducer = { api ->
                logger.info("Refreshing token")
                val currentToken = api.token
                val refreshedToken = refreshSpotifyClientToken(
                    api.clientId ?: throw IllegalStateException("ClientId must be present"),
                    api.clientSecret,
                    api.token.refreshToken,
                    false
                )
                if (refreshedToken.refreshToken == null) {
                    logger.info("Refresh token is null, keeping previous one")
                    refreshedToken.refreshToken = currentToken.refreshToken
                }
                coroutineScope { launch { saveToken(refreshedToken) } }
                refreshedToken
            }
        }

    private fun getFile(): File {
        return Path("$tokenStoragePath/spotify.token").toFile()
    }
}