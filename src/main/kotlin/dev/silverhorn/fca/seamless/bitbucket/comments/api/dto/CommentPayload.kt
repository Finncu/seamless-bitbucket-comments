package dev.silverhorn.fca.seamless.bitbucket.comments.api.dto

/**
 * POST/PUT payload for creating or updating a commit comment.
 *
 * Global comment (no anchor):
 * ```json
 * { "text": "..." }
 * ```
 *
 * Inline comment (with anchor):
 * ```json
 * {
 *   "text": "...",
 *   "anchor": { "path": "...", "line": 15, "lineType": "ADDED", "fileType": "TO" }
 * }
 * ```
 *
 * Reply:
 * ```json
 * { "text": "...", "parent": { "id": 12345 } }
 * ```
 *
 * @property text    Markdown text of the new comment
 * @property anchor  inline anchor; null for global and reply comments
 * @property parent  parent reference; non-null only when posting a reply
 */
data class CommentPayload(
    val text: String,
    val anchor: AnchorPayload? = null,
    val parent: ParentRefDto? = null
)

/**
 * Anchor portion of a [CommentPayload], describing the exact position
 * inside the diff where the new comment will be anchored.
 *
 * @property path     repository-relative file path (no leading slash)
 * @property srcPath  original path before rename; null when not applicable
 * @property line     1-based line number within the diff
 * @property lineType ADDED, REMOVED, or CONTEXT
 * @property fileType FROM (before commit) or TO (after commit)
 * @property diffType always COMMIT for commit-level comments
 * @property fromHash SHA-1 of the parent commit
 * @property toHash   SHA-1 of the commit being commented on
 */
data class AnchorPayload(
    val path: String,
    val srcPath: String? = null,
    val line: Int,
    val lineType: String,
    val fileType: String,
    val diffType: String = "COMMIT",
    val fromHash: String,
    val toHash: String
)

