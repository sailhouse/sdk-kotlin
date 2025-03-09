package dev.sailhouse

import dev.sailhouse.models.EventsResponseDto

/**
 * Response from the Sailhouse API when retrieving events.
 *
 * @param events The list of events.
 * @param offset The offset for pagination.
 * @param limit The limit for pagination.
 */
data class EventsResponse<T>(
    val events: List<Event<T>>,
    val offset: Int,
    val limit: Int
) : Iterable<Event<T>> {
    /**
     * Returns an iterator over the events.
     */
    override fun iterator(): Iterator<Event<T>> = events.iterator()

    companion object {
        /**
         * Creates an EventsResponse from an EventsResponseDto.
         *
         * @param dto The DTO to create the response from.
         * @param topic The topic the events belong to.
         * @param subscription The subscription the events were retrieved from.
         * @param client The Sailhouse client instance.
         * @return A new EventsResponse instance.
         */
        fun <T> fromDto(
            dto: EventsResponseDto<T>,
            topic: String,
            subscription: String,
            client: SailhouseClient
        ): EventsResponse<T> {
            return EventsResponse(
                events = dto.events.map { eventDto ->
                    Event.fromDto(eventDto, topic, subscription, client)
                },
                offset = dto.offset,
                limit = dto.limit
            )
        }
    }
}
