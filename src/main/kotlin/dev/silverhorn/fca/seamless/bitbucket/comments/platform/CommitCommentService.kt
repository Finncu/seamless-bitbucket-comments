package dev.silverhorn.fca.seamless.bitbucket.comments.platform

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import dev.silverhorn.fca.seamless.bitbucket.comments.PluginMetadata
import dev.silverhorn.fca.seamless.bitbucket.comments.api.BitbucketApiException
import dev.silverhorn.fca.seamless.bitbucket.comments.api.client.BitbucketRestClient
import dev.silverhorn.fca.seamless.bitbucket.comments.api.dto.CommentPayload
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.CommitComment
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.CommitCommentSummary
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.service.AnchorMapper
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.service.MentionParser
import dev.silverhorn.fca.seamless.bitbucket.comments.platform.settings.BitbucketConfigurable
import dev.silverhorn.fca.seamless.bitbucket.comments.platform.settings.BitbucketSettingsState

/**
 * Project-scoped façade for all Bitbucket commit-comment operations.
 *
 * Orchestrates the [BitbucketRestClient] (network), [CommentCache] (LRU store),
 * [MentionParser] and [AnchorMapper] (domain) without exposing their implementation
 * details to the UI layer.
 *
 * **Threading contract:**
 * All public methods that perform network I/O are *blocking* and must be called
 * from a background thread (e.g. `Dispatchers.IO` or `Task.Backgroundable`).
 * Results are delivered to the EDT via `invokeLater` in the calling UI components.
 *
 * @property project the IntelliJ [Project] this service is bound to
 */
@Service(Service.Level.PROJECT)
class CommitCommentService(private val project: Project) {

    private val log = logger<CommitCommentService>()

    // ?? Lazy client construction ???????????????????????????????????????????

    /**
     * Builds a [BitbucketRestClient] from workspace Git metadata first,
     * with fallback to manually configured settings, and token from PasswordSafe.
     *
     * Returns null and logs a warning when no usable context could be resolved.
     * **Must be called from a background thread.**
     */
    private fun buildClient(): BitbucketRestClient? {
        val settings = BitbucketSettingsState.instance
        val context = BitbucketRepoContextResolver.resolve(project, settings) ?: run {
            log.warn("Bitbucket context could not be resolved from workspace or settings")
            return null
        }
        val token = BitbucketConfigurable.loadToken(context.serverUrl) ?: run {
            log.warn("No Bitbucket token found in credential store")
            return null
        }
        return BitbucketRestClient(context.serverUrl, context.projectKey, context.repoSlug, token)
    }

    // ?? Public API ????????????????????????????????????????????????????????

    /**
     * Fetches all comments for the given [commitHash] and returns them grouped
     * into a list of top-level [CommitComment] domain objects (with nested replies).
     *
     * **Strategy (two-step because of API constraint):**
     * 1. `GET .../commits/{hash}/changes` ? list of changed file paths
     * 2. For each path: `GET .../commits/{hash}/comments?path={p}` ? accumulate
     *
     * The result is also written to [CommentCache] as a [CommitCommentSummary]
     * for badge rendering in the VCS log.
     *
     * @param commitHash full SHA-1 of the commit
     * @return top-level comments with nested children, or empty list on error
     */
    fun fetchAllComments(commitHash: String): List<CommitComment> {
        val client = buildClient() ?: return emptyList()
        val settings = BitbucketSettingsState.instance
        val userSlug = settings.userSlug

        return try {
            // Step 1: discover changed files (mandatory because ?path= is required by the API)
            val changedFiles = client.getCommitChanges(commitHash)
            val allComments = mutableListOf<CommitComment>()

            // Step 2: fetch comments per file
            for (changed in changedFiles) {
                val path = changed.path.toPathString()
                if (path.isBlank()) continue
                val dtos = client.getFileComments(commitHash, path)
                dtos.forEach { dto -> allComments.add(AnchorMapper.toDomain(dto, userSlug)) }
            }

            // Update the LRU cache with aggregated counts
            val totalCount = countAll(allComments)
            val mentionCount = countMentions(allComments)
            CommentCache.instance.put(commitHash, CommitCommentSummary(commitHash, totalCount, mentionCount))

            allComments
        } catch (ex: BitbucketApiException) {
            notifyError("Failed to load comments for $commitHash", ex.messages)
            emptyList()
        } catch (ex: Exception) {
            log.error("Unexpected error fetching comments for $commitHash", ex)
            emptyList()
        }
    }

    /**
     * Posts a new comment to the given commit.
     *
     * On success the [CommentCache] is invalidated for this commit so the next
     * badge render will reflect the updated count.
     *
     * @param commitHash full SHA-1 of the commit
     * @param payload    pre-built comment payload (global, inline, or reply)
     * @return the newly created [CommitComment], or null on error
     */
    fun postComment(commitHash: String, payload: CommentPayload): CommitComment? {
        val client = buildClient() ?: return null
        val settings = BitbucketSettingsState.instance
        return try {
            val dto = client.postComment(commitHash, payload)
            CommentCache.instance.invalidate(commitHash)
            AnchorMapper.toDomain(dto, settings.userSlug)
        } catch (ex: BitbucketApiException) {
            notifyError("Failed to post comment", ex.messages)
            null
        } catch (ex: Exception) {
            log.error("Unexpected error posting comment to $commitHash", ex)
            null
        }
    }

    /**
     * Updates the text of an existing comment.
     *
     * Invalidates the cache to ensure counts remain accurate after the edit.
     *
     * @param commitHash full SHA-1 of the commit
     * @param commentId  server-assigned comment ID
     * @param version    current optimistic-locking version from the last GET response
     * @param newText    replacement Markdown text
     * @return the updated [CommitComment], or null on error
     */
    fun updateComment(commitHash: String, commentId: Long, version: Int, newText: String): CommitComment? {
        val client = buildClient() ?: return null
        val settings = BitbucketSettingsState.instance
        return try {
            val dto = client.updateComment(commitHash, commentId, version, newText)
            CommentCache.instance.invalidate(commitHash)
            AnchorMapper.toDomain(dto, settings.userSlug)
        } catch (ex: BitbucketApiException) {
            notifyError("Failed to update comment #$commentId", ex.messages)
            null
        } catch (ex: Exception) {
            log.error("Unexpected error updating comment #$commentId", ex)
            null
        }
    }

    /**
     * Deletes a comment from a commit.
     *
     * Invalidates the cache entry for the commit so the badge reflects the new count.
     *
     * @param commitHash full SHA-1 of the commit
     * @param commentId  server-assigned comment ID
     * @param version    current optimistic-locking version
     * @return true when the deletion succeeded
     */
    fun deleteComment(commitHash: String, commentId: Long, version: Int): Boolean {
        val client = buildClient() ?: return false
        return try {
            client.deleteComment(commitHash, commentId, version)
            CommentCache.instance.invalidate(commitHash)
            true
        } catch (ex: BitbucketApiException) {
            notifyError("Failed to delete comment #$commentId", ex.messages)
            false
        } catch (ex: Exception) {
            log.error("Unexpected error deleting comment #$commentId", ex)
            false
        }
    }

    /**
     * Warms the cache for the given list of commit hashes by fetching their comment counts.
     *
     * Only loads hashes that are not already in the cache.  Intended to be called
     * from [PluginStartupActivity] on a background thread with low scheduling priority.
     *
     * @param commitHashes SHA-1 hashes of commits visible in the VCS log at startup
     */
    fun warmCache(commitHashes: List<String>) {
        for (hash in commitHashes) {
            if (!CommentCache.instance.contains(hash)) {
                fetchAllComments(hash)
            }
        }
    }

    // ?? Internal helpers ??????????????????????????????????????????????????

    /** Counts all comments recursively (top-level + replies). */
    private fun countAll(comments: List<CommitComment>): Int =
        comments.sumOf { 1 + countAll(it.children) }

    /** Counts all comments that contain a mention of the current user, recursively. */
    private fun countMentions(comments: List<CommitComment>): Int =
        comments.sumOf { (if (it.containsMention) 1 else 0) + countMentions(it.children) }

    /**
     * Displays a balloon notification in the IDE with the given error [messages].
     *
     * @param title    short description of the failed operation
     * @param messages list of error messages from the API response
     */
    private fun notifyError(title: String, messages: List<String>) {
        val content = messages.joinToString("\n")
        NotificationGroupManager.getInstance()
            .getNotificationGroup(PluginMetadata.NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.ERROR)
            .notify(project)
    }

    companion object {
        /**
         * Returns the project-scoped singleton instance.
         *
         * @param project the current IntelliJ project
         */
        fun getInstance(project: Project): CommitCommentService =
            project.getService(CommitCommentService::class.java)
    }
}

