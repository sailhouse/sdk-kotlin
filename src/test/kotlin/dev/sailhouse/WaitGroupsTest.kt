package dev.sailhouse

import dev.sailhouse.models.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WaitGroupsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val mockEngine = MockEngine { request ->
        when {
            request.url.encodedPath == "/waitgroups/instances" -> {
                val response = WaitGroupInstanceResponse(waitGroupInstanceId = "test-wg-instance")
                respond(
                    content = json.encodeToString(response),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            request.url.encodedPath.contains("/topics/") && request.url.encodedPath.contains("/events") -> {
                val response = PublishEventResponseDto(id = "test-event-id")
                respond(
                    content = json.encodeToString(response),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            request.url.encodedPath.contains("/waitgroups/instances/") && 
            request.url.encodedPath.contains("/events") -> {
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
    fun `test wait group creation and execution`() = runBlocking {
        val events = listOf(
            WaitGroupEvent(
                topic = "orders",
                body = mapOf("orderId" to "123", "status" to "created")
            ),
            WaitGroupEvent(
                topic = "inventory",
                body = mapOf("productId" to "456", "quantity" to -1)
            )
        )

        val options = WaitOptions(ttl = "5m")

        // This should not throw an exception
        client.wait("orders", events, options)
    }

    @Test
    fun `test publish with wait group instance id`() = runBlocking {
        val event = mapOf("message" to "Test message")
        val options = PublishEventOptions(
            waitGroupInstanceId = "test-wg-instance"
        )

        val response = client.publish("test-topic", event, options)
        assertEquals("test-event-id", response.id)
    }
}