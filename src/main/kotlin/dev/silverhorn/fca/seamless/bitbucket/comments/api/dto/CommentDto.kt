package dev.silverhorn.fca.seamless.bitbucket.comments.api.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Represents a single comment returned by
 * `GET .../commits/{hash}/comments?path={path}`.
 *
 * The API may add new fields in future server versions; all unknown
 * properties are silently ignored via [JsonIgnoreProperties].
 *
 * @property id       server-assigned comment identifier
 * @property version  optimistic-locking version; required for PUT/DELETE
 * @property text     raw Markdown text of the comment
 * @property author   author information
 * @property createdDate epoch-millisecond creation timestamp
 * @property updatedDate epoch-millisecond last-update timestamp
 * @property anchor   spatial anchor describing where in the diff this comment lives; null for global comments
 * @property parent   parent comment reference when this is a reply; null for top-level comments
 * @property comments child/reply comments (nested list)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CommentDto(
    val id: Long = 0,
    val version: Int = 0,
    val text: String = "",
    val author: AuthorDto = AuthorDto(),
    val createdDate: Long = 0,
    val updatedDate: Long = 0,
    val anchor: AnchorDto? = null,
    val parent: ParentRefDto? = null,
    val comments: List<CommentDto> = emptyList()
)

/**
 * Minimal author information embedded in every comment.
 *
 * @property slug        login identifier ? used for mention matching
 * @property displayName human-readable full name
 * @property emailAddress primary e-mail; may be absent on some server configurations
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AuthorDto(
    val slug: String = "",
    val displayName: String = "",
    val emailAddress: String? = null
)

/**
 * Spatial anchor describing the exact position of an inline comment in a diff.
 *
 * @property path      repository-relative file path (no leading slash)
 * @property srcPath   original path before a rename/move; null when the file was not renamed
 * @property line      1-based line number within the diff; null for file-level anchors
 * @property lineType  kind of diff line: ADDED, REMOVED, or CONTEXT
 * @property fileType  which side of the diff: FROM (before commit) or TO (after commit)
 * @property diffType  type of the diff, typically COMMIT for individual commit comments
 * @property fromHash  SHA-1 of the parent commit (base of the diff)
 * @property toHash    SHA-1 of the commit being commented on
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AnchorDto(
    val path: String = "",
    val srcPath: String? = null,
    val line: Int? = null,
    val lineType: String? = null,   // ADDED | REMOVED | CONTEXT
    val fileType: String? = null,   // FROM | TO
    val diffType: String? = null,   // COMMIT | RANGE | EFFECTIVE
    val fromHash: String? = null,
    val toHash: String? = null
)

/**
 * Lightweight reference to a parent comment, used only to identify reply relationships.
 *
 * @property id server-assigned identifier of the parent comment
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ParentRefDto(val id: Long = 0)

