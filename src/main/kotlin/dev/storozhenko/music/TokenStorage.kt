package dev.storozhenko.music

import com.adamratzman.spotify.models.Token
import kotlinx.serialization.json.Json
import java.io.File
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

    private fun getFile(): File {
        return Path("$tokenStoragePath/spotify.token").toFile()
    }
}