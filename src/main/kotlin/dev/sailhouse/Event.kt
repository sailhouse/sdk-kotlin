package dev.sailhouse

import dev.sailhouse.models.EventDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

/**
 * Represents an event in the Sailhouse system.
 *
 * @param id The unique identifier of the event.
 * @param data The data payload of the event.
 * @param queryableValue The value that can be queried.
 * @param timestamp The timestamp when the event was created.
 * @param metadata Additional metadata associated with the event.
 * @param topic The topic the event belongs to.
 * @param subscription The subscription the event was retrieved from.
 * @param client The Sailhouse client instance.
 */
class Event<T>(
    val id: String,
    val data: T,
    val queryableValue: String,
    val timestamp: String,
    val metadata: Map<String, JsonElement>? = null,
    private val topic: String,
    private val subscription: String,
    private val client: SailhouseClient
) {
    /**
     * Acknowledges the event.
     */
    suspend fun ack() {
        withContext(Dispatchers.IO) {
            client.ackEvent(topic, subscription, id)
        }
    }

    companion object {
        /**
         * Creates an Event from an EventDto.
         *
         * @param dto The DTO to create the event from.
         * @param topic The topic the event belongs to.
         * @param subscription The subscription the event was retrieved from.
         * @param client The Sailhouse client instance.
         * @return A new Event instance.
         */
        fun <T> fromDto(
            dto: EventDto<T>,
            topic: String,
            subscription: String,
            client: SailhouseClient
        ): Event<T> {
            return Event(
                id = dto.id,
                data = dto.data,
                queryableValue = dto.queryableValue,
                timestamp = dto.timestamp,
                metadata = dto.metadata,
                topic = topic,
                subscription = subscription,
                client = client
            )
        }
    }
}
