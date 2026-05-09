package dev.silverhorn.fca.seamless.bitbucket.comments.platform

import com.intellij.openapi.components.Service
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.CommitCommentSummary
import java.util.Collections

/**
 * Application-scoped LRU cache mapping commit SHA-1 hashes to their aggregated
 * [CommitCommentSummary] (total comment count + mention count).
 *
 * The cache is intentionally kept small and evicts the oldest entry when it
 * exceeds [MAX_ENTRIES].  This prevents unbounded memory growth when users scroll
 * through thousands of commits.
 *
 * Thread safety: backed by a [Collections.synchronizedMap] wrapper around an
 * access-ordered [LinkedHashMap] so concurrent reads and writes from background
 * threads are safe.
 *
 * Usage (from a background thread):
 * ```kotlin
 * val cache = CommentCache.instance
 * cache.put(hash, summary)
 * val summary = cache.get(hash) // null on cache miss
 * ```
 */
@Service(Service.Level.APP)
class CommentCache {

    companion object {
        /** Maximum number of commit hashes retained in the cache. */
        private const val MAX_ENTRIES = 500

        /**
         * Returns the application-level singleton instance.
         */
        val instance: CommentCache
            get() = com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(CommentCache::class.java)
    }

    /** Thread-safe access-ordered map implementing LRU eviction. */
    private val store: MutableMap<String, CommitCommentSummary> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, CommitCommentSummary>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CommitCommentSummary>): Boolean =
                    size > MAX_ENTRIES
            }
        )

    /**
     * Returns the cached [CommitCommentSummary] for [commitHash], or null on a cache miss.
     *
     * @param commitHash full 40-character SHA-1 of the commit
     */
    fun get(commitHash: String): CommitCommentSummary? = store[commitHash]

    /**
     * Stores or replaces the [CommitCommentSummary] for [commitHash].
     *
     * @param commitHash full SHA-1 of the commit
     * @param summary    aggregated comment statistics to cache
     */
    fun put(commitHash: String, summary: CommitCommentSummary) {
        store[commitHash] = summary
    }

    /**
     * Removes the entry for [commitHash] if present.
     * Call this after a comment is created or deleted to force a fresh fetch.
     *
     * @param commitHash full SHA-1 of the commit whose cache entry should be invalidated
     */
    fun invalidate(commitHash: String) {
        store.remove(commitHash)
    }

    /**
     * Returns true when a cached entry exists for [commitHash].
     *
     * @param commitHash full SHA-1 of the commit
     */
    fun contains(commitHash: String): Boolean = store.containsKey(commitHash)
}

