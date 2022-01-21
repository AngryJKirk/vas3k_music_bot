package dev.storozhenko.music.run

import com.adamratzman.spotify.SpotifyScope
import com.adamratzman.spotify.SpotifyUserAuthorization
import com.adamratzman.spotify.getSpotifyAuthorizationUrl
import com.adamratzman.spotify.spotifyClientApi
import dev.storozhenko.music.SpotifyCredentials
import dev.storozhenko.music.services.SpotifyService
import dev.storozhenko.music.services.TokenStorage
import io.ktor.application.call
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.getOrFail

class Server(
    private val tokenStorage: TokenStorage,
    private val spotifyService: SpotifyService,
    private val spotifyCredentials: SpotifyCredentials
) {

    fun run() {
        embeddedServer(Netty, port = 9999) {
            routing {
                get("/") {
                    val url = getSpotifyAuthorizationUrl(
                        SpotifyScope.PLAYLIST_READ_PRIVATE,
                        SpotifyScope.PLAYLIST_MODIFY_PRIVATE,
                        SpotifyScope.PLAYLIST_MODIFY_PUBLIC,
                        SpotifyScope.PLAYLIST_MODIFY_PRIVATE,
                        SpotifyScope.PLAYLIST_READ_COLLABORATIVE,
                        clientId = spotifyCredentials.clientId,
                        redirectUri = spotifyCredentials.redirectUri
                    )
                    call.respondRedirect(url, permanent = false)
                }
                get("/callback") {
                    val authCode = call.request.queryParameters.getOrFail("code")
                    val client = spotifyClientApi(
                        spotifyCredentials.clientId,
                        spotifyCredentials.clientSecret,
                        spotifyCredentials.redirectUri,
                        SpotifyUserAuthorization(authorizationCode = authCode),
                        tokenStorage.tokenRefreshOption()
                    ).build()
                    tokenStorage.saveToken(client.token)
                    spotifyService.client = client
                }
            }
        }.start(wait = true)
    }
}