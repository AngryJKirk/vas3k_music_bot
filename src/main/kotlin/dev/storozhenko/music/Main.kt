package dev.storozhenko.music

import dev.storozhenko.music.run.Bot
import dev.storozhenko.music.run.Server
import dev.storozhenko.music.services.SpotifyService
import dev.storozhenko.music.services.TokenStorage
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication

val spotifyCredentials = SpotifyCredentials(
    getEnv("SPOTIFY_CLIENT_ID"),
    getEnv("SPOTIFY_CLIENT_SECRET"),
    getEnv("SPOTIFY_REDIRECT_URL")
)
private val botToken = getEnv("TELEGRAM_API_TOKEN")
private val botUsername = getEnv("TELEGRAM_BOT_USERNAME")
private val tokenStoragePath = getEnv("TOKEN_STORAGE_PATH")

fun main() {
    val telegramBotsApi = TelegramBotsLongPollingApplication()

    val tokenStorage = TokenStorage(tokenStoragePath)
    val spotifyService = SpotifyService(tokenStorage, spotifyCredentials)
    val server = Server(spotifyService)
    val telegramClient = OkHttpTelegramClient(botToken)
    val bot = Bot(botUsername, spotifyService, telegramClient)
    telegramBotsApi.registerBot(botToken, bot)
    server.run()
}

private fun getEnv(envName: String): String {
    return System.getenv()[envName]?.takeIf(String::isNotBlank) ?: throw IllegalStateException("$envName does not exist or empty")
}