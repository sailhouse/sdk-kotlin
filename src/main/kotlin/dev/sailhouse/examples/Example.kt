package dev.sailhouse.examples

import dev.sailhouse.SailhouseClient
import dev.sailhouse.models.GetEventOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Example data class for events.
 */
@Serializable
data class ExampleEvent(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Example usage of the Sailhouse Kotlin SDK.
 */
object Example {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        // Create a client with your API key
        val client = SailhouseClient("your_api_key_here")

        try {
            // Publish an event
            val publishResponse = client.publish("example-topic", ExampleEvent("Hello from Kotlin SDK!"))
            println("Published event with ID: ${publishResponse.id}")

            // Publish a scheduled event
            val scheduledTime = Instant.now().plus(1, ChronoUnit.HOURS)
            val scheduledResponse = client.publish(
                "example-topic",
                ExampleEvent("Scheduled event from Kotlin SDK!"),
                scheduledTime,
                mapOf("source" to "example")
            )
            println("Scheduled event with ID: ${scheduledResponse.id} for $scheduledTime")

            // Get events
            val events = client.getEvents<ExampleEvent>(
                "example-topic",
                "example-subscription",
                GetEventOptions(timeWindow = "1h")
            )

            println("Retrieved ${events.events.size} events:")
            events.forEach { event ->
                println("Event ID: ${event.id}, Message: ${event.data.message}")
                event.ack()
            }

            // Stream events
            println("Starting event stream, press Ctrl+C to stop...")
            val job = client.streamEvents<ExampleEvent>("example-topic", "example-subscription") { event ->
                println("Received event: ${event.data.message}")
                event.ack()
            }

            // Keep the application running
            job.join()
        } finally {
            // Close the client when done
            client.close()
        }
    }
}
