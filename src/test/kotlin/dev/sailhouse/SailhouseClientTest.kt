package dev.sailhouse

import dev.sailhouse.models.EventDto
import dev.sailhouse.models.EventsResponseDto
import dev.sailhouse.models.PublishEventResponseDto
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SailhouseClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val mockEngine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/topics/test-topic/events" -> {
                val response = PublishEventResponseDto(id = "test-event-id")
                respond(
                    content = json.encodeToString(response),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            "/topics/test-topic/subscriptions/test-subscription/events" -> {
                val events = listOf(
                    EventDto(
                        id = "test-event-id",
                        data = mapOf("message" to "Test message"),
                        queryableValue = "Test message",
                        timestamp = "2023-01-01T00:00:00Z"
                    )
                )
                val response = EventsResponseDto(events = events, offset = 0, limit = 10)
                respond(
                    content = json.encodeToString(response),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            "/topics/test-topic/subscriptions/test-subscription/events/test-event-id" -> {
                respond(
                    content = "",
                    status = HttpStatusCode.OK
                )
            }
            else -> {
                respond(
                    content = "Not found",
                    status = HttpStatusCode.NotFound
                )
            }
        }
    }

    private val config = SailhouseClientConfig(
        baseUrl = "https://api.sailhouse.dev",
        engine = mockEngine
    )

    private val client = SailhouseClient("test-api-key", config)

    @Test
    fun `test publish event`() = runBlocking {
        val event = mapOf("message" to "Test message")
        val response = client.publish("test-topic", event)

        assertEquals("test-event-id", response.id)
    }

    @Test
    fun `test get events`() = runBlocking {
        val response = client.getEvents<Map<String, String>>("test-topic", "test-subscription")

        assertEquals(1, response.events.size)
        assertEquals("test-event-id", response.events[0].id)
        assertEquals("Test message", response.events[0].data["message"])
    }

    @Test
    fun `test ack event`() = runBlocking {
        // This should not throw an exception
        client.ackEvent("test-topic", "test-subscription", "test-event-id")
    }
}
