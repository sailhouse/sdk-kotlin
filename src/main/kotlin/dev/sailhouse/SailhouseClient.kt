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
    
    /**
     * Admin client for managing Sailhouse resources.
     */
    val admin: AdminClient

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

    init {
        admin = AdminClient(httpClient, config.baseUrl)
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
                options?.waitGroupInstanceId?.let { waitGroupInstanceId ->
                    put("wait_group_instance_id", waitGroupInstanceId)
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
     * Pulls a single event from a subscription.
     *
     * @param topic The topic to pull from.
     * @param subscription The subscription to pull from.
     * @return The event, or null if no events are available.
     */
    suspend inline fun <reified T> pull(
        topic: String,
        subscription: String
    ): Event<T>? {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get("${config.baseUrl}/topics/$topic/subscriptions/$subscription/events/pull")
                if (response.status.isSuccess()) {
                    val eventDto = response.body<EventDto<T>>()
                    Event.fromDto(eventDto, topic, subscription, this@SailhouseClient)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
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
     * Creates a wait group for coordinated publishing of multiple events.
     *
     * @param topic The topic for the wait group.
     * @param events The events to publish as part of the wait group.
     * @param options The options for the wait group.
     */
    suspend fun <T> wait(
        topic: String,
        events: List<WaitGroupEvent<T>>,
        options: WaitOptions? = null
    ) {
        withContext(Dispatchers.IO) {
            // Create wait group instance
            val waitGroupResponse = httpClient.post("${config.baseUrl}/waitgroups/instances") {
                setBody(buildJsonObject {
                    put("topic", topic)
                    options?.ttl?.let { put("ttl", it) }
                })
            }.body<WaitGroupInstanceResponse>()

            // Publish all events with the wait group instance ID
            events.forEach { event ->
                publish(
                    event.topic,
                    event.body,
                    PublishEventOptions(
                        metadata = event.metadata,
                        sendAt = event.sendAt,
                        waitGroupInstanceId = waitGroupResponse.waitGroupInstanceId
                    )
                )
            }

            // Mark wait group as in progress
            httpClient.put("${config.baseUrl}/waitgroups/instances/${waitGroupResponse.waitGroupInstanceId}/events") {
                setBody(buildJsonObject {})
            }
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
