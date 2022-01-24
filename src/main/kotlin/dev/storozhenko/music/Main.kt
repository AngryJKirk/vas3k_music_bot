package dev.storozhenko.music

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.storozhenko.music.run.Bot
import dev.storozhenko.music.run.Server
import dev.storozhenko.music.services.SpotifyService
import dev.storozhenko.music.services.TokenStorage
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

val spotifyCredentials = SpotifyCredentials(
    getEnv("SPOTIFY_CLIENT_ID"),
    getEnv("SPOTIFY_CLIENT_SECRET"),
    getEnv("SPOTIFY_REDIRECT_URL")
)
private val botToken = getEnv("TELEGRAM_API_TOKEN")
private val botUsername = getEnv("TELEGRAM_BOT_USERNAME")
private val tokenStoragePath = getEnv("TOKEN_STORAGE_PATH")


fun main() {
    val telegramBotsApi = TelegramBotsApi(DefaultBotSession::class.java)
    val tokenStorage = TokenStorage(tokenStoragePath)
    val spotifyService = SpotifyService(tokenStorage, spotifyCredentials)
    val server = Server(spotifyService)
    telegramBotsApi.registerBot(Bot(botToken, botUsername, spotifyService))
    server.run()
}

private fun getEnv(envName: String): String {
    return System.getenv()[envName] ?: throw IllegalStateException("$envName does not exist")
}