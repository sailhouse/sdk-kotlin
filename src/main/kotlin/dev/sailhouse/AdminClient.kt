package dev.sailhouse

import dev.sailhouse.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Admin client for managing Sailhouse resources.
 *
 * @param httpClient The HTTP client for making requests.
 * @param baseUrl The base URL for the Sailhouse API.
 */
class AdminClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    /**
     * Registers a push subscription.
     *
     * @param topic The topic to subscribe to.
     * @param subscription The subscription name.
     * @param endpoint The endpoint URL for push notifications.
     * @param options Additional options for the subscription.
     * @return The registration result.
     */
    suspend fun registerPushSubscription(
        topic: String,
        subscription: String,
        endpoint: String,
        options: PushSubscriptionOptions? = null
    ): RegisterResult {
        return withContext(Dispatchers.IO) {
            val requestBody = buildJsonObject {
                put("type", "push")
                put("endpoint", endpoint)
                options?.filter?.let { filter ->
                    when (filter) {
                        is ComplexFilter -> {
                            put("filter", buildJsonObject {
                                put("filters", buildJsonArray {
                                    filter.filters.forEach { condition ->
                                        add(buildJsonObject {
                                            put("path", condition.path)
                                            put("condition", condition.condition)
                                            put("value", condition.value)
                                        })
                                    }
                                })
                                put("operator", filter.operator)
                            })
                        }
                        is Boolean -> put("filter", filter)
                        null -> put("filter", JsonNull)
                    }
                }
                options?.rateLimit?.let { put("rate_limit", it) }
                options?.deduplication?.let { put("deduplication", it) }
            }

            httpClient.put("$baseUrl/api/v1/topics/$topic/subscriptions/$subscription") {
                setBody(requestBody)
            }.body()
        }
    }
}