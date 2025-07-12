package dev.sailhouse

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Exception thrown when push subscription signature verification fails.
 */
class PushSubscriptionVerificationException(
    message: String,
    val code: String
) : Exception(message)

/**
 * Parsed signature header components.
 */
data class SignatureComponents(
    val timestamp: Long,
    val signature: String
)

/**
 * Headers expected from a push subscription request.
 */
data class PushSubscriptionHeaders(
    val sailhouseSignature: String,
    val identifier: String,
    val eventId: String
)

/**
 * Push subscription payload structure.
 */
@Serializable
data class PushSubscriptionPayload<T>(
    val data: T,
    val metadata: Map<String, JsonElement>? = null,
    val id: String,
    val timestamp: String
)

/**
 * Verification options.
 */
data class VerificationOptions(
    val tolerance: Long = 300 // tolerance in seconds
)

/**
 * Push subscription signature verifier.
 */
class PushSubscriptionVerifier(private val secret: String) {

    init {
        require(secret.isNotBlank()) { "Push subscription secret is required" }
    }

    /**
     * Verifies a push subscription signature.
     *
     * @param signature The Sailhouse-Signature header value.
     * @param body The raw request body as string.
     * @param options Verification options.
     * @return true if signature is valid.
     * @throws PushSubscriptionVerificationException if verification fails.
     */
    fun verifySignature(
        signature: String,
        body: String,
        options: VerificationOptions = VerificationOptions()
    ): Boolean {
        try {
            // Parse signature header
            val (timestamp, headerSignature) = parseSignatureHeader(signature)

            // Validate timestamp
            if (!isTimestampValid(timestamp, options.tolerance)) {
                throw PushSubscriptionVerificationException(
                    "Request timestamp is too old. Maximum age: ${options.tolerance} seconds",
                    "TIMESTAMP_TOO_OLD"
                )
            }

            // Calculate expected signature
            val expectedSignature = calculateSignature(timestamp, body)

            // Perform constant-time comparison
            if (!constantTimeEqual(expectedSignature, headerSignature)) {
                throw PushSubscriptionVerificationException(
                    "Signature verification failed",
                    "INVALID_SIGNATURE"
                )
            }

            return true
        } catch (e: PushSubscriptionVerificationException) {
            throw e
        } catch (e: Exception) {
            throw PushSubscriptionVerificationException(
                "Signature verification failed: ${e.message}",
                "VERIFICATION_ERROR"
            )
        }
    }

    /**
     * Parses the Sailhouse-Signature header.
     *
     * @param header The signature header value.
     * @return Parsed timestamp and signature.
     */
    private fun parseSignatureHeader(header: String): SignatureComponents {
        if (header.isBlank()) {
            throw PushSubscriptionVerificationException(
                "Signature header is required",
                "MISSING_SIGNATURE_HEADER"
            )
        }

        val elements = header.split(",")
        var timestamp: Long? = null
        var signature: String? = null

        for (element in elements) {
            val trimmed = element.trim()
            val parts = trimmed.split("=", limit = 2)
            if (parts.size != 2) continue

            val (key, value) = parts
            when (key) {
                "t" -> {
                    timestamp = value.toLongOrNull()
                        ?: throw PushSubscriptionVerificationException(
                            "Invalid timestamp in signature header",
                            "INVALID_TIMESTAMP"
                        )
                }
                "v1" -> {
                    signature = value
                }
            }
        }

        if (timestamp == null || signature == null) {
            throw PushSubscriptionVerificationException(
                "Invalid signature header format. Expected format: t=<timestamp>,v1=<signature>",
                "INVALID_SIGNATURE_FORMAT"
            )
        }

        return SignatureComponents(timestamp, signature)
    }

    /**
     * Checks if timestamp is within tolerance.
     *
     * @param timestamp The timestamp to validate.
     * @param tolerance Maximum age in seconds.
     * @return true if timestamp is valid.
     */
    private fun isTimestampValid(timestamp: Long, tolerance: Long): Boolean {
        val currentTime = System.currentTimeMillis() / 1000
        return abs(currentTime - timestamp) <= tolerance
    }

    /**
     * Calculates HMAC-SHA256 signature for the payload.
     *
     * @param timestamp The timestamp from the signature header.
     * @param body The raw request body.
     * @return Hex-encoded signature.
     */
    private fun calculateSignature(timestamp: Long, body: String): String {
        val payload = "$timestamp.$body"
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val hash = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Performs constant-time comparison to prevent timing attacks.
     *
     * @param expected The expected signature.
     * @param actual The actual signature from header.
     * @return true if signatures match.
     */
    private fun constantTimeEqual(expected: String, actual: String): Boolean {
        if (expected.length != actual.length) return false
        
        var result = 0
        for (i in expected.indices) {
            result = result or (expected[i].code xor actual[i].code)
        }
        return result == 0
    }
}

/**
 * Convenience function for one-off signature verification.
 *
 * @param secret The push subscription secret.
 * @param signature The Sailhouse-Signature header value.
 * @param body The raw request body as string.
 * @param options Verification options.
 * @return true if signature is valid.
 * @throws PushSubscriptionVerificationException if verification fails.
 */
fun verifyPushSubscriptionSignature(
    secret: String,
    signature: String,
    body: String,
    options: VerificationOptions = VerificationOptions()
): Boolean {
    val verifier = PushSubscriptionVerifier(secret)
    return verifier.verifySignature(signature, body, options)
}

/**
 * Safe verification that returns a boolean instead of throwing.
 *
 * @param secret The push subscription secret.
 * @param signature The Sailhouse-Signature header value.
 * @param body The raw request body as string.
 * @param options Verification options.
 * @return true if signature is valid, false otherwise.
 */
fun verifyPushSubscriptionSignatureSafe(
    secret: String,
    signature: String,
    body: String,
    options: VerificationOptions = VerificationOptions()
): Boolean {
    return try {
        verifyPushSubscriptionSignature(secret, signature, body, options)
    } catch (e: Exception) {
        false
    }
}