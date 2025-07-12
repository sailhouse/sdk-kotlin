package dev.sailhouse

import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Options for configuring the subscriber.
 *
 * @param perSubscriptionProcessors Number of concurrent processors per subscription.
 */
data class SubscriberOptions(
    val perSubscriptionProcessors: Int = 1
)

/**
 * Handler for processing subscription events.
 */
typealias SubscriptionHandler<T> = suspend (SubscriptionEvent<T>) -> Unit

/**
 * Event wrapper for subscription handlers.
 *
 * @param event The event to process.
 */
data class SubscriptionEvent<T>(
    val event: Event<T>
)

/**
 * Subscription configuration.
 */
private data class Subscription<T>(
    val topic: String,
    val subscription: String,
    val handler: SubscriptionHandler<T>
)

/**
 * Long-running subscriber for processing events from multiple subscriptions.
 *
 * @param client The Sailhouse client.
 * @param options Configuration options for the subscriber.
 */
class SailhouseSubscriber(
    private val client: SailhouseClient,
    private val options: SubscriberOptions = SubscriberOptions()
) {
    private val subscriptions = mutableListOf<Subscription<*>>()
    private var isRunning = false
    private var activeJobs = mutableListOf<Job>()

    /**
     * Subscribes to a topic/subscription with a handler.
     *
     * @param topic The topic to subscribe to.
     * @param subscription The subscription name.
     * @param handler The handler for processing events.
     */
    fun <T> subscribe(
        topic: String,
        subscription: String,
        handler: SubscriptionHandler<T>
    ) {
        subscriptions.add(Subscription(topic, subscription, handler))
    }

    /**
     * Starts processing events from all subscriptions.
     */
    suspend fun start() {
        if (isRunning) {
            throw IllegalStateException("Subscriber is already running")
        }

        isRunning = true
        logger.info { "Starting subscriber with ${subscriptions.size} subscriptions" }

        activeJobs = subscriptions.flatMap { subscription ->
            (1..options.perSubscriptionProcessors).map {
                CoroutineScope(Dispatchers.IO).launch {
                    runSubscriberLoop(subscription)
                }
            }
        }.toMutableList()

        // Wait for all jobs to complete
        activeJobs.joinAll()
    }

    /**
     * Stops the subscriber.
     */
    fun stop() {
        logger.info { "Stopping subscriber" }
        isRunning = false
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }

    /**
     * Runs the event processing loop for a single subscription.
     */
    private suspend fun <T> runSubscriberLoop(subscription: Subscription<T>) {
        val topic = subscription.topic
        val subscriptionName = subscription.subscription
        val handler = subscription.handler

        logger.info { "Starting processor for $topic/$subscriptionName" }

        while (isRunning) {
            try {
                // Pull an event from the subscription
                val event = client.pull<T>(topic, subscriptionName)

                if (event != null) {
                    try {
                        // Handle the event
                        handler(SubscriptionEvent(event))
                        
                        // Acknowledge the event
                        event.ack()
                    } catch (handlerError: Exception) {
                        logger.error(handlerError) {
                            "Error handling event ${event.id} from $topic/$subscriptionName"
                        }
                    }
                } else {
                    // No events available, wait before trying again
                    delay(1000)
                }
            } catch (pullError: Exception) {
                logger.error(pullError) { "Error pulling from $topic/$subscriptionName" }
                delay(1000)
            }
        }

        logger.info { "Stopped processor for $topic/$subscriptionName" }
    }
}

/**
 * Creates a new subscriber instance.
 *
 * @param options Configuration options for the subscriber.
 * @return A new SailhouseSubscriber instance.
 */
fun SailhouseClient.subscriber(options: SubscriberOptions = SubscriberOptions()): SailhouseSubscriber {
    return SailhouseSubscriber(this, options)
}