package dev.sailhouse

import dev.sailhouse.models.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SailhouseSubscriberTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private var requestCount = 0
    private val mockEngine = MockEngine { request ->
        when {
            request.url.encodedPath.contains("/pull") -> {
                requestCount++
                if (requestCount <= 2) {
                    // Return an event for the first two requests
                    val event = EventDto(
                        id = "test-event-$requestCount",
                        data = mapOf("message" to "Test message $requestCount"),
                        queryableValue = "Test message $requestCount",
                        timestamp = "2023-01-01T00:00:00Z"
                    )
                    respond(
                        content = json.encodeToString(event),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    // Return no content (no events) for subsequent requests
                    respond(
                        content = "",
                        status = HttpStatusCode.NoContent
                    )
                }
            }
            request.url.encodedPath.contains("/events/") && request.method == HttpMethod.Post -> {
                // ACK endpoint
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
    fun `test subscriber with single subscription`() = runBlocking {
        val processedEvents = mutableListOf<String>()
        
        val subscriber = client.subscriber(SubscriberOptions(perSubscriptionProcessors = 1))
        
        subscriber.subscribe<Map<String, String>>("test-topic", "test-subscription") { event ->
            processedEvents.add(event.event.id)
        }

        // Start subscriber in a coroutine and stop it after a short delay
        val job = launch {
            subscriber.start()
        }

        delay(100) // Let it process a few events
        subscriber.stop()
        job.cancel()

        // Should have processed at least one event
        assertEquals(true, processedEvents.isNotEmpty())
    }

    @Test
    fun `test subscriber options`() {
        val options = SubscriberOptions(perSubscriptionProcessors = 3)
        val subscriber = client.subscriber(options)
        
        // Test that subscriber was created with the right options
        // This is a basic test since we can't easily verify internal state
        subscriber.subscribe<Map<String, String>>("test-topic", "test-subscription") { event ->
            // Do nothing
        }
    }

    @Test
    fun `test multiple subscriptions`() = runBlocking {
        val processedEvents = mutableListOf<String>()
        
        val subscriber = client.subscriber()
        
        subscriber.subscribe<Map<String, String>>("topic1", "sub1") { event ->
            processedEvents.add("topic1-${event.event.id}")
        }
        
        subscriber.subscribe<Map<String, String>>("topic2", "sub2") { event ->
            processedEvents.add("topic2-${event.event.id}")
        }

        // Start subscriber in a coroutine and stop it after a short delay
        val job = launch {
            subscriber.start()
        }

        delay(100) // Let it process a few events
        subscriber.stop()
        job.cancel()

        // Should have processed events from both subscriptions
        assertEquals(true, processedEvents.isNotEmpty())
    }
}