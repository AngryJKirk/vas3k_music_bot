package dev.storozhenko.music.run

import com.adamratzman.spotify.SpotifyUserAuthorization
import dev.storozhenko.music.getLogger
import dev.storozhenko.music.services.SpotifyService
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail

class Server(private val spotifyService: SpotifyService) {


    fun run() {
        embeddedServer(Netty, port = 9999, module = { module(spotifyService) }).start(wait = true)
    }
}

fun Application.module(spotifyService: SpotifyService) {
    routing {
        get("/") {
            val url = spotifyService.getAuthUrl()
            call.respondRedirect(url, permanent = false)
            getLogger().info("Sent redirect to $url")
        }
        get("/callback") {
            val authCode = call.request.queryParameters.getOrFail("code")
            spotifyService.createClient(
                SpotifyUserAuthorization(authorizationCode = authCode)
            )
            getLogger().info("Got callback, client has been created")
            call.respondText { "OK" }
        }
    }
}