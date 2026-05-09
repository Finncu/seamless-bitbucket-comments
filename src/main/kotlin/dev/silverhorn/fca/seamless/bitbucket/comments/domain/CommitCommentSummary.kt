package dev.silverhorn.fca.seamless.bitbucket.comments.domain

/**
 * Aggregated comment statistics for a single commit, stored in the LRU cache.
 *
 * Intentionally lightweight ? only the numbers needed to render the VCS-log badge
 * are stored, not the full comment tree.
 *
 * @property commitHash   SHA-1 of the commit these statistics belong to
 * @property totalCount   total number of comments across all files (including replies)
 * @property mentionCount number of comments that contain a mention of the current user
 */
data class CommitCommentSummary(
    val commitHash: String,
    val totalCount: Int,
    val mentionCount: Int
) {
    /** True when any comment mentions the current user. */
    val hasMentions: Boolean get() = mentionCount > 0
}

