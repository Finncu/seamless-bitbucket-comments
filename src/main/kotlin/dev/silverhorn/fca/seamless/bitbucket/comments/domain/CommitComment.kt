package dev.silverhorn.fca.seamless.bitbucket.comments.domain

/**
 * Immutable domain model for a single Bitbucket commit comment.
 *
 * Constructed from [dev.silverhorn.fca.seamless.bitbucket.comments.api.dto.CommentDto]
 * by the platform service layer after mention detection has been applied.
 *
 * @property id             server-assigned comment identifier
 * @property version        optimistic-locking version; required for PUT/DELETE
 * @property text           raw Markdown text
 * @property authorSlug     login slug of the comment author
 * @property authorName     human-readable display name of the comment author
 * @property createdDate    epoch-millisecond creation timestamp
 * @property anchor         spatial diff anchor; null for global (commit-level) comments
 * @property parentId       ID of the parent comment when this is a reply; null for top-level comments
 * @property children       nested reply comments (already fully resolved)
 * @property containsMention true when the comment text contains a mention of the current user
 */
data class CommitComment(
    val id: Long,
    val version: Int,
    val text: String,
    val authorSlug: String,
    val authorName: String,
    val createdDate: Long,
    val anchor: Anchor?,
    val parentId: Long?,
    val children: List<CommitComment>,
    val containsMention: Boolean
)

/**
 * Value object describing the exact position of an inline comment in a diff.
 *
 * @property path      repository-relative file path (no leading slash)
 * @property srcPath   original path before a rename/move; null when not applicable
 * @property line      1-based line number within the diff; null for file-level anchors
 * @property lineType  kind of diff line: [LineType.ADDED], [LineType.REMOVED], or [LineType.CONTEXT]
 * @property fileType  which side of the diff: [FileType.FROM] (before commit) or [FileType.TO] (after commit)
 * @property fromHash  SHA-1 of the parent commit (base of the diff)
 * @property toHash    SHA-1 of the commit being commented on
 */
data class Anchor(
    val path: String,
    val srcPath: String?,
    val line: Int?,
    val lineType: LineType?,
    val fileType: FileType?,
    val fromHash: String?,
    val toHash: String?
)

/** Describes whether a diff line was added, removed, or left unchanged. */
enum class LineType { ADDED, REMOVED, CONTEXT }

/** Identifies which side of a two-way diff a comment or anchor belongs to. */
enum class FileType { FROM, TO }

