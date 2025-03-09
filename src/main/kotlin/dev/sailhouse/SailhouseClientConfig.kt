package dev.sailhouse

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

/**
 * Configuration for the Sailhouse client.
 *
 * @param baseUrl The base URL of the Sailhouse API.
 * @param timeout The timeout for HTTP requests in milliseconds.
 * @param engine The HTTP client engine to use.
 */
data class SailhouseClientConfig(
    val baseUrl: String = "https://api.sailhouse.dev",
    val timeout: Long = 30_000,
    val engine: HttpClientEngine = CIO.create()
)
