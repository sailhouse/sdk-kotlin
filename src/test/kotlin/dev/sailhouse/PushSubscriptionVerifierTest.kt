package dev.sailhouse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushSubscriptionVerifierTest {
    private val secret = "test-secret"
    private val verifier = PushSubscriptionVerifier(secret)

    @Test
    fun `test valid signature verification`() {
        val timestamp = System.currentTimeMillis() / 1000
        val body = """{"data": {"message": "test"}, "id": "123", "timestamp": "2023-01-01T00:00:00Z"}"""
        
        // Calculate expected signature manually for testing
        val payload = "$timestamp.$body"
        val expectedSignature = calculateHmacSha256(secret, payload)
        val signature = "t=$timestamp,v1=$expectedSignature"

        assertTrue(verifier.verifySignature(signature, body))
    }

    @Test
    fun `test invalid signature verification`() {
        val timestamp = System.currentTimeMillis() / 1000
        val body = """{"data": {"message": "test"}, "id": "123", "timestamp": "2023-01-01T00:00:00Z"}"""
        val signature = "t=$timestamp,v1=invalid_signature"

        assertFailsWith<PushSubscriptionVerificationException> {
            verifier.verifySignature(signature, body)
        }
    }

    @Test
    fun `test timestamp too old`() {
        val oldTimestamp = (System.currentTimeMillis() / 1000) - 400 // 400 seconds ago
        val body = """{"data": {"message": "test"}, "id": "123", "timestamp": "2023-01-01T00:00:00Z"}"""
        val payload = "$oldTimestamp.$body"
        val expectedSignature = calculateHmacSha256(secret, payload)
        val signature = "t=$oldTimestamp,v1=$expectedSignature"

        val exception = assertFailsWith<PushSubscriptionVerificationException> {
            verifier.verifySignature(signature, body)
        }
        assertEquals("TIMESTAMP_TOO_OLD", exception.code)
    }

    @Test
    fun `test invalid signature header format`() {
        val body = """{"data": {"message": "test"}, "id": "123", "timestamp": "2023-01-01T00:00:00Z"}"""
        val signature = "invalid_format"

        val exception = assertFailsWith<PushSubscriptionVerificationException> {
            verifier.verifySignature(signature, body)
        }
        assertEquals("INVALID_SIGNATURE_FORMAT", exception.code)
    }

    @Test
    fun `test missing signature header`() {
        val body = """{"data": {"message": "test"}, "id": "123", "timestamp": "2023-01-01T00:00:00Z"}"""
        val signature = ""

        val exception = assertFailsWith<PushSubscriptionVerificationException> {
            verifier.verifySignature(signature, body)
        }
        assertEquals("MISSING_SIGNATURE_HEADER", exception.code)
    }

    @Test
    fun `test convenience function`() {
        val timestamp = System.currentTimeMillis() / 1000
        val body = """{"data": {"message": "test"}, "id": "123", "timestamp": "2023-01-01T00:00:00Z"}"""
        val payload = "$timestamp.$body"
        val expectedSignature = calculateHmacSha256(secret, payload)
        val signature = "t=$timestamp,v1=$expectedSignature"

        assertTrue(verifyPushSubscriptionSignature(secret, signature, body))
    }

    @Test
    fun `test safe verification function`() {
        val timestamp = System.currentTimeMillis() / 1000
        val body = """{"data": {"message": "test"}, "id": "123", "timestamp": "2023-01-01T00:00:00Z"}"""
        val validSignature = "t=$timestamp,v1=${calculateHmacSha256(secret, "$timestamp.$body")}"
        val invalidSignature = "t=$timestamp,v1=invalid"

        assertTrue(verifyPushSubscriptionSignatureSafe(secret, validSignature, body))
        assertFalse(verifyPushSubscriptionSignatureSafe(secret, invalidSignature, body))
    }

    @Test
    fun `test custom tolerance`() {
        val oldTimestamp = (System.currentTimeMillis() / 1000) - 200 // 200 seconds ago
        val body = """{"data": {"message": "test"}, "id": "123", "timestamp": "2023-01-01T00:00:00Z"}"""
        val payload = "$oldTimestamp.$body"
        val expectedSignature = calculateHmacSha256(secret, payload)
        val signature = "t=$oldTimestamp,v1=$expectedSignature"

        // Should fail with default tolerance (300 seconds) - actually this should pass
        // Let's use 100 seconds tolerance to make it fail
        val options = VerificationOptions(tolerance = 100)
        
        assertFailsWith<PushSubscriptionVerificationException> {
            verifier.verifySignature(signature, body, options)
        }
    }

    private fun calculateHmacSha256(secret: String, data: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val secretKeySpec = javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(secretKeySpec)
        val hash = mac.doFinal(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}