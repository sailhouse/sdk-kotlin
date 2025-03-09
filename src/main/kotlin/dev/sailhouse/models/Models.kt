package dev.sailhouse.models

import kotlinx.serialization.KSerializer
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
    val timestamp: String
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
 */
@Serializable
data class PublishEventOptions(
    val metadata: Map<String, String>? = null,
    @Serializable(with = InstantSerializer::class)
    val sendAt: Instant? = null
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
