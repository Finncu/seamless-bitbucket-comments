package dev.silverhorn.fca.seamless.bitbucket.comments.platform

import dev.silverhorn.fca.seamless.bitbucket.comments.domain.CommitCommentSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [CommentCache].
 *
 * Tests run without any IntelliJ platform; the cache is instantiated directly.
 * The [CommentCache] class is not an IntelliJ service here ? the service wiring
 * only applies at runtime.
 */
class CommentCacheTest {

    private fun newSummary(hash: String, total: Int = 2, mentions: Int = 1) =
        CommitCommentSummary(hash, total, mentions)

    @Test
    fun `get returns null on cache miss`() {
        val cache = CommentCache()
        assertNull(cache.get("abc123"))
    }

    @Test
    fun `put and get round-trip`() {
        val cache = CommentCache()
        val summary = newSummary("deadbeef")
        cache.put("deadbeef", summary)
        assertEquals(summary, cache.get("deadbeef"))
    }

    @Test
    fun `contains returns false before put`() {
        val cache = CommentCache()
        assertFalse(cache.contains("nope"))
    }

    @Test
    fun `contains returns true after put`() {
        val cache = CommentCache()
        cache.put("abc", newSummary("abc"))
        assertTrue(cache.contains("abc"))
    }

    @Test
    fun `invalidate removes the entry`() {
        val cache = CommentCache()
        cache.put("abc", newSummary("abc"))
        cache.invalidate("abc")
        assertNull(cache.get("abc"))
        assertFalse(cache.contains("abc"))
    }

    @Test
    fun `multiple entries coexist independently`() {
        val cache = CommentCache()
        cache.put("hash1", newSummary("hash1", total = 5, mentions = 2))
        cache.put("hash2", newSummary("hash2", total = 0, mentions = 0))

        assertEquals(5, cache.get("hash1")?.totalCount)
        assertEquals(0, cache.get("hash2")?.totalCount)
    }
}

