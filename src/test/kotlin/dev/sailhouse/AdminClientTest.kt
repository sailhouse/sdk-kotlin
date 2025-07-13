package dev.sailhouse

import dev.sailhouse.models.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val mockEngine = MockEngine { request ->
        when {
            request.url.encodedPath.contains("/api/v1/topics/") && 
            request.url.encodedPath.contains("/subscriptions/") -> {
                val response = RegisterResult(outcome = "created")
                respond(
                    content = json.encodeToString(response),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
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
    fun `test register push subscription`() = runBlocking {
        val result = client.admin.registerPushSubscription(
            topic = "test-topic",
            subscription = "test-subscription",
            endpoint = "https://example.com/webhook"
        )

        assertEquals("created", result.outcome)
    }

    @Test
    fun `test register push subscription with filter`() = runBlocking {
        val filter = ComplexFilter(
            filters = listOf(
                FilterCondition(
                    path = "data.type",
                    condition = "eq",
                    value = "order"
                )
            ),
            operator = "and"
        )

        val options = PushSubscriptionOptions(
            filter = filter,
            rateLimit = "10/minute",
            deduplication = "5m"
        )

        val result = client.admin.registerPushSubscription(
            topic = "test-topic",
            subscription = "test-subscription",
            endpoint = "https://example.com/webhook",
            options = options
        )

        assertEquals("created", result.outcome)
    }
}