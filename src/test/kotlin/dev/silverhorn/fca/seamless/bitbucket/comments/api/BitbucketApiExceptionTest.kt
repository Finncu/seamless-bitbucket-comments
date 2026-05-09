package dev.silverhorn.fca.seamless.bitbucket.comments.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [BitbucketApiException].
 *
 * Verifies that the exception constructs the message string correctly from a list
 * of individual error messages, which mirrors what the client parses from the
 * Bitbucket `errors[].message` JSON array.
 */
class BitbucketApiExceptionTest {

    @Test
    fun `message contains status code`() {
        val ex = BitbucketApiException(400, listOf("The path query parameter is required."))
        assertTrue(ex.message!!.contains("400"))
    }

    @Test
    fun `message contains all error messages joined by semicolon`() {
        val ex = BitbucketApiException(422, listOf("Field A is missing", "Field B is invalid"))
        assertTrue(ex.message!!.contains("Field A is missing"))
        assertTrue(ex.message!!.contains("Field B is invalid"))
    }

    @Test
    fun `statusCode property is preserved`() {
        val ex = BitbucketApiException(403, listOf("Forbidden"))
        assertEquals(403, ex.statusCode)
    }

    @Test
    fun `messages list is preserved`() {
        val msgs = listOf("Error 1", "Error 2")
        val ex = BitbucketApiException(500, msgs)
        assertEquals(msgs, ex.messages)
    }

    @Test
    fun `single message produces clean output`() {
        val ex = BitbucketApiException(404, listOf("Not found"))
        assertEquals("Bitbucket API error [404]: Not found", ex.message)
    }
}

