package dev.silverhorn.fca.seamless.bitbucket.comments.api.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Represents one file entry from
 * `GET .../commits/{hash}/changes`.
 *
 * The API returns a [PagedResponse] of these objects, which the plugin
 * must page through to discover all changed files before requesting
 * file-scoped comments ([CommentDto]).
 *
 * @property path      new path after the commit; use this as the `?path=` parameter
 * @property srcPath   original path before a rename/move; null when unchanged
 * @property type      change type reported by Bitbucket (e.g. ADD, MODIFY, DELETE, RENAME, COPY)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChangedFileDto(
    val path: ChangedFilePathDto = ChangedFilePathDto(),
    val srcPath: ChangedFilePathDto? = null,
    val type: String = "MODIFY"
)

/**
 * Path wrapper used inside [ChangedFileDto].
 *
 * @property toString combines [components] to a repository-relative path string
 * @property components ordered list of path segments (directories + filename)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChangedFilePathDto(
    val components: List<String> = emptyList(),
    val toString: String = ""
) {
    /**
     * Returns the repository-relative path as a single string without a leading slash.
     * Prefers the [toString] field supplied by the server; falls back to joining [components].
     */
    fun toPathString(): String =
        toString.ifEmpty { components.joinToString("/") }
}

