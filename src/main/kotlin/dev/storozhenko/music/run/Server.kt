package dev.storozhenko.music.run

import com.adamratzman.spotify.SpotifyUserAuthorization
import dev.storozhenko.music.getLogger
import dev.storozhenko.music.services.SpotifyService
import io.ktor.application.call
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.getOrFail

class Server(private val spotifyService: SpotifyService) {

    private val log = getLogger()

    fun run() {
        embeddedServer(Netty, port = 9999) {
            routing {
                get("/") {
                    val url = spotifyService.getAuthUrl()
                    call.respondRedirect(url, permanent = false)
                    log.info("Sent redirect to $url")
                }
                get("/callback") {
                    val authCode = call.request.queryParameters.getOrFail("code")
                    spotifyService.createClient(
                        SpotifyUserAuthorization(authorizationCode = authCode)
                    )
                    log.info("Got callback, client has been created")
                    call.respondText { "OK" }
                }
            }
        }.start(wait = true)
    }
}