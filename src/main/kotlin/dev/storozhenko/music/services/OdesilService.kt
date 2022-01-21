package dev.storozhenko.music.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import dev.storozhenko.music.OdesilResponse
import dev.storozhenko.music.getLogger
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset

class OdesilService {
    private val logger = getLogger()
    private val client = HttpClient.newBuilder().build()
    private val objectMapper = ObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    fun detect(url: String): OdesilResponse? {
        val encodedUrl = URLEncoder.encode(url, Charset.defaultCharset())
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.song.link/v1-alpha.1/links?url=$encodedUrl"))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val body = response.body()
        if (response.statusCode() != 200) {
            logger.info("Odesil fail: $body")
            return null
        }
        return objectMapper.readValue(body, OdesilResponse::class.java)
    }
}

