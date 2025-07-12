package dev.sailhouse.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import java.time.Instant

/**
 * Represents an event in the Sailhouse system.
 *
 * @param id The unique identifier of the event.
 * @param data The data payload of the event.
 * @param queryableValue The value that can be queried.
 * @param timestamp The timestamp when the event was created.
 */
@Serializable
data class EventDto<T>(
    val id: String,
    val data: T,
    val queryableValue: String,
    val timestamp: String,
    val metadata: Map<String, JsonElement>? = null
)

/**
 * Response from the Sailhouse API when retrieving events.
 *
 * @param events The list of events.
 * @param offset The offset for pagination.
 * @param limit The limit for pagination.
 */
@Serializable
data class EventsResponseDto<T>(
    val events: List<EventDto<T>>,
    val offset: Int,
    val limit: Int
)

/**
 * Response from the Sailhouse API when publishing an event.
 *
 * @param id The unique identifier of the published event.
 */
@Serializable
data class PublishEventResponseDto(
    val id: String
)

/**
 * Options for publishing an event.
 *
 * @param metadata Additional metadata for the event.
 * @param sendAt The time to send the event.
 * @param waitGroupInstanceId The wait group instance ID for coordinated publishing.
 */
@Serializable
data class PublishEventOptions(
    val metadata: Map<String, String>? = null,
    @Serializable(with = InstantSerializer::class)
    val sendAt: Instant? = null,
    val waitGroupInstanceId: String? = null
)

/**
 * Options for retrieving events.
 *
 * @param timeWindow The time window to retrieve events from.
 * @param queryablePath The path to query events by.
 */
@Serializable
data class GetEventOptions(
    val timeWindow: String? = null,
    val queryablePath: String? = null
)

/**
 * Options for wait group creation.
 *
 * @param ttl The time-to-live for the wait group.
 */
@Serializable
data class WaitOptions(
    val ttl: String? = null
)

/**
 * Response from creating a wait group instance.
 *
 * @param waitGroupInstanceId The unique identifier for the wait group instance.
 */
@Serializable
data class WaitGroupInstanceResponse(
    @SerialName("wait_group_instance_id")
    val waitGroupInstanceId: String
)

/**
 * Event to be published as part of a wait group.
 *
 * @param topic The topic to publish to.
 * @param body The event data.
 * @param metadata Additional metadata for the event.
 * @param sendAt The time to send the event.
 */
@Serializable
data class WaitGroupEvent<T>(
    val topic: String,
    val body: T,
    val metadata: Map<String, String>? = null,
    @Serializable(with = InstantSerializer::class)
    val sendAt: Instant? = null
)

/**
 * Filter condition for complex filtering.
 *
 * @param path The path to filter on.
 * @param condition The condition to apply.
 * @param value The value to compare against.
 */
@Serializable
data class FilterCondition(
    val path: String,
    val condition: String,
    val value: String
)

/**
 * Complex filter with multiple conditions.
 *
 * @param filters The list of filter conditions.
 * @param operator The operator to apply between conditions.
 */
@Serializable
data class ComplexFilter(
    val filters: List<FilterCondition>,
    val operator: String
)

/**
 * Options for push subscription registration.
 *
 * @param filter The filter to apply to events.
 * @param rateLimit The rate limit configuration.
 * @param deduplication The deduplication configuration.
 */
@Serializable
data class PushSubscriptionOptions(
    val filter: Any? = null, // Can be Boolean, ComplexFilter, or null
    val rateLimit: String? = null,
    val deduplication: String? = null
)

/**
 * Result of subscription registration.
 *
 * @param outcome The outcome of the registration.
 */
@Serializable
data class RegisterResult(
    val outcome: String // "created", "updated", or "none"
)

/**
 * Serializer for Instant objects.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = JsonPrimitive.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}

/**
 * Serializer for generic JSON content.
 */
class JsonContentSerializer<T>(private val serializer: KSerializer<T>) : KSerializer<T> {
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun serialize(encoder: Encoder, value: T) {
        if (encoder is JsonEncoder) {
            val element = Json.encodeToJsonElement(serializer, value)
            encoder.encodeJsonElement(element)
        } else {
            serializer.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): T {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            return Json.decodeFromJsonElement(serializer, element)
        } else {
            return serializer.deserialize(decoder)
        }
    }
}
