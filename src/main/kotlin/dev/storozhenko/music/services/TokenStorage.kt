package dev.storozhenko.music.services

import com.adamratzman.spotify.SpotifyApiOptions
import com.adamratzman.spotify.SpotifyClientApi
import com.adamratzman.spotify.models.Token
import com.adamratzman.spotify.refreshSpotifyClientToken
import dev.storozhenko.music.getLogger
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.IllegalStateException
import kotlin.io.path.Path

class TokenStorage(private val tokenStoragePath: String) {
    private val logger = getLogger()

    fun saveToken(token: Token) {
        getFile().writeText(Json.encodeToString(Token.serializer(), token))
    }

    fun getToken(): Token? {
        val file = getFile()
        if (!file.exists()) {
            return null
        }
        val result = runCatching {
            Json.decodeFromString(Token.serializer(), file.readText())
        }
        if (result.isFailure) {
            logger.error("Token deserialization error", result.exceptionOrNull())
            file.delete()
        }
        return result.getOrNull()
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
                refreshedToken

            }
            afterTokenRefresh = { api ->
                val token = if (api.token.refreshToken == null) {
                    logger.info("New refresh token is null, using previous one")
                    api.token.copy(refreshToken = getToken()?.refreshToken)
                } else {
                    logger.info("New refresh token exists, updating")
                    api.token
                }
                saveToken(token)
            }
        }

    private fun getFile(): File {
        return Path("$tokenStoragePath/spotify.token").toFile()
    }
}