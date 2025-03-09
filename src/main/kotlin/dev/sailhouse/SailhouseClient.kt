package dev.sailhouse

import dev.sailhouse.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.time.Instant
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger {}

/**
 * Client for interacting with the Sailhouse API.
 *
 * @param apiKey The API key for authentication.
 * @param config The configuration for the client.
 */
class SailhouseClient(
    private val apiKey: String,
    private val config: SailhouseClientConfig = SailhouseClientConfig()
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.IO

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(config.engine) {
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets)
        defaultRequest {
            header(HttpHeaders.Authorization, apiKey)
            header("x-source", "sailhouse-kotlin")
            contentType(ContentType.Application.Json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeout
            connectTimeoutMillis = config.timeout
            socketTimeoutMillis = config.timeout
        }
    }

    /**
     * Publishes an event to a topic.
     *
     * @param topic The topic to publish to.
     * @param event The event data to publish.
     * @param options The options for publishing the event.
     * @return The response from the API.
     */
    suspend fun <T> publish(
        topic: String,
        event: T,
        options: PublishEventOptions? = null
    ): PublishEventResponseDto {
        return withContext(Dispatchers.IO) {
            val requestBody = buildJsonObject {
                put("data", json.encodeToJsonElement(event))
                options?.metadata?.let { metadata ->
                    put("metadata", buildJsonObject {
                        metadata.forEach { (key, value) ->
                            put(key, value)
                        }
                    })
                }
                options?.sendAt?.let { sendAt ->
                    put("send_at", sendAt.toString())
                }
            }

            httpClient.post("${config.baseUrl}/topics/$topic/events") {
                setBody(requestBody)
            }.body()
        }
    }

    /**
     * Publishes an event to a topic with a scheduled time.
     *
     * @param topic The topic to publish to.
     * @param event The event data to publish.
     * @param sendAt The time to send the event.
     * @param metadata Additional metadata for the event.
     * @return The response from the API.
     */
    suspend fun <T> publish(
        topic: String,
        event: T,
        sendAt: Instant,
        metadata: Map<String, String>? = null
    ): PublishEventResponseDto {
        return publish(topic, event, PublishEventOptions(metadata, sendAt))
    }

    /**
     * Gets events from a subscription.
     *
     * @param topic The topic to get events from.
     * @param subscription The subscription to get events from.
     * @param options The options for getting events.
     * @return The response from the API.
     */
    suspend inline fun <reified T> getEvents(
        topic: String,
        subscription: String,
        options: GetEventOptions? = null
    ): EventsResponse<T> {
        return withContext(Dispatchers.IO) {
            val response = httpClient.get("${config.baseUrl}/topics/$topic/subscriptions/$subscription/events") {
                options?.timeWindow?.let { parameter("time_window", it) }
                options?.queryablePath?.let { parameter("queryable_path", it) }
            }.body<EventsResponseDto<T>>()

            EventsResponse.fromDto(response, topic, subscription, this@SailhouseClient)
        }
    }

    /**
     * Streams events from a subscription.
     *
     * @param topic The topic to stream events from.
     * @param subscription The subscription to stream events from.
     * @param handler The handler for processing events.
     * @param options The options for streaming events.
     * @return A job that can be cancelled to stop streaming.
     */
    inline fun <reified T> streamEvents(
        topic: String,
        subscription: String,
        crossinline handler: suspend (Event<T>) -> Unit,
        options: GetEventOptions? = null
    ): Job {
        val clientId = generateClientId()

        return launch {
            try {
                httpClient.webSocket("${config.baseUrl}/events/stream") {
                    // Send initial connection message
                    val connectionMessage = buildJsonObject {
                        put("topic_slug", topic)
                        put("subscription_slug", subscription)
                        put("token", apiKey)
                        put("client_id", clientId)
                        options?.timeWindow?.let { put("time_window", it) }
                        options?.queryablePath?.let { put("queryable_path", it) }
                    }

                    send(Frame.Text(connectionMessage.toString()))

                    // Process incoming messages
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                val eventDto = json.decodeFromString<EventDto<T>>(text)
                                val event = Event.fromDto(eventDto, topic, subscription, this@SailhouseClient)
                                handler(event)
                            }
                            else -> logger.warn { "Received unexpected frame type: $frame" }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error in WebSocket connection" }
                throw e
            }
        }
    }

    /**
     * Acknowledges an event.
     *
     * @param topic The topic the event belongs to.
     * @param subscription The subscription the event was retrieved from.
     * @param eventId The ID of the event to acknowledge.
     */
    suspend fun ackEvent(
        topic: String,
        subscription: String,
        eventId: String
    ) {
        withContext(Dispatchers.IO) {
            httpClient.post("${config.baseUrl}/topics/$topic/subscriptions/$subscription/events/$eventId")
        }
    }

    /**
     * Closes the client and releases resources.
     */
    fun close() {
        httpClient.close()
        coroutineContext.cancelChildren()
    }

    private fun generateClientId(): String {
        return "kotlin-sdk-${System.currentTimeMillis()}-${(0..999999).random()}"
    }
}
