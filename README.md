# Sailhouse Kotlin SDK ⛵

The [Sailhouse](https://sailhouse.dev) Kotlin SDK provides an idiomatic Kotlin abstraction over the Sailhouse HTTP API for JVM languages.

## Installation

### Gradle

```kotlin
implementation("dev.sailhouse:sailhouse-kotlin-sdk:0.1.0")
```

### Maven

```xml
<dependency>
    <groupId>dev.sailhouse</groupId>
    <artifactId>sailhouse-kotlin-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Basic Usage

### Initialization

```kotlin
import dev.sailhouse.SailhouseClient

// Create a client with your API key
val client = SailhouseClient("your_api_key")
```

### Publishing Events

```kotlin
// Simple event publishing
client.publish("topic-name", mapOf("message" to "Hello World!"))

// With a data class
data class MyEvent(val message: String, val timestamp: Long = System.currentTimeMillis())
client.publish("topic-name", MyEvent("Hello World!"))

// With scheduling
import java.time.Instant
import java.time.temporal.ChronoUnit

val scheduledTime = Instant.now().plus(1, ChronoUnit.HOURS)
client.publish("topic-name", MyEvent("Scheduled message"), scheduledTime)
```

### Retrieving Events

```kotlin
// Get events from a subscription
val events = client.getEvents<MyEvent>("topic-name", "subscription-name")

// Process events
events.forEach { event ->
    println("Event ID: ${event.id}, Data: ${event.data}")

    // Acknowledge the event
    event.ack()
}
```

### Streaming Events

```kotlin
// Stream events with coroutines
val job = client.streamEvents<MyEvent>("topic-name", "subscription-name") { event ->
    println("Received event: ${event.data}")
    event.ack()
}

// Cancel streaming when done
job.cancel()
```

## Advanced Usage

### Configuring the Client

```kotlin
import dev.sailhouse.SailhouseClientConfig
import io.ktor.client.engine.cio.CIO

val config = SailhouseClientConfig(
    baseUrl = "https://custom-api.sailhouse.dev",
    timeout = 30_000, // 30 seconds
    engine = CIO.create()
)

val client = SailhouseClient("your_api_key", config)
```

## License

[MIT Licence](LICENCE)
