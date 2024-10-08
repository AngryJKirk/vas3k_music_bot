package dev.storozhenko.music.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import dev.storozhenko.music.OdesilEntity
import dev.storozhenko.music.OdesilResponse
import dev.storozhenko.music.getLogger
import org.telegram.telegrambots.meta.api.objects.MessageEntity
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

    fun detect(messageEntity: MessageEntity): OdesilEntity? {
        val encodedUrl = URLEncoder.encode(messageEntity.text, Charset.defaultCharset())
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.song.link/v1-alpha.1/links?url=$encodedUrl"))
            .build()
        val response = retryRequest(request) ?: return null
        val body = response.body()
        if (response.statusCode() != 200) {
            logger.info("Odesil fail: $body")
            return null
        }
        val odesilResponse = objectMapper.readValue(body, OdesilResponse::class.java)
        return OdesilEntity(odesilResponse, messageEntity)
    }

    private fun retryRequest(request: HttpRequest): HttpResponse<String>? {
        for (i in 0..5) {
            try {
                return client.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (e: Exception) {
                logger.error("Exception occurred while trying to send request, retry number is $i", e)
            }
        }
        return null
    }
}

