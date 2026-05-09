package dev.silverhorn.fca.seamless.bitbucket.comments.domain.service

import dev.silverhorn.fca.seamless.bitbucket.comments.api.dto.AnchorPayload
import dev.silverhorn.fca.seamless.bitbucket.comments.api.dto.CommentDto
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.*

/**
 * Converts between Bitbucket API DTOs and the plugin's domain model,
 * and constructs the [AnchorPayload] required for creating new inline comments.
 *
 * All methods are pure (no side effects, no I/O) and can be called from any thread.
 */
object AnchorMapper {

    // ?? DTO ? Domain ??????????????????????????????????????????????????????

    /**
     * Maps a [CommentDto] tree (including nested [CommentDto.comments]) to a [CommitComment] tree.
     *
     * Mention detection is applied recursively; [userSlug] is forwarded to [MentionParser].
     *
     * @param dto       the server-supplied comment data transfer object
     * @param userSlug  login slug of the current user for mention detection
     * @return immutable [CommitComment] domain object with [CommitComment.containsMention] set
     */
    fun toDomain(dto: CommentDto, userSlug: String): CommitComment = CommitComment(
        id = dto.id,
        version = dto.version,
        text = dto.text,
        authorSlug = dto.author.slug,
        authorName = dto.author.displayName,
        createdDate = dto.createdDate,
        anchor = dto.anchor?.let {
            Anchor(
                path = it.path,
                srcPath = it.srcPath,
                line = it.line,
                lineType = it.lineType?.let(::parseLineType),
                fileType = it.fileType?.let(::parseFileType),
                fromHash = it.fromHash,
                toHash = it.toHash
            )
        },
        parentId = dto.parent?.id,
        children = dto.comments.map { child -> toDomain(child, userSlug) },
        containsMention = MentionParser.containsMention(dto.text, userSlug)
    )

    // ?? UI ? API Payload ??????????????????????????????????????????????????

    /**
     * Builds an [AnchorPayload] from the contextual information available in the
     * [dev.silverhorn.fca.seamless.bitbucket.comments.ui.diff.CommitDiffExtension]
     * when a user clicks a gutter icon.
     *
     * The mapping follows these rules:
     * - Left editor  ? [FileType.FROM]
     * - Right editor ? [FileType.TO]
     * - Inserted line ? [LineType.ADDED]
     * - Deleted line  ? [LineType.REMOVED]
     * - Unchanged line in hunk context ? [LineType.CONTEXT]
     *
     * @param editorSide   which editor the user clicked in
     * @param diffChange   the kind of change on the clicked line
     * @param lineNumber   1-based line number within the diff
     * @param filePath     repository-relative path of the file shown in the diff (no leading slash)
     * @param srcFilePath  original path before a rename; null when the file was not renamed
     * @param fromHash     SHA-1 of the parent commit
     * @param toHash       SHA-1 of the commit being commented on
     * @return ready-to-serialise [AnchorPayload]
     */
    fun toAnchorPayload(
        editorSide: EditorSide,
        diffChange: DiffChangeType,
        lineNumber: Int,
        filePath: String,
        srcFilePath: String? = null,
        fromHash: String,
        toHash: String
    ): AnchorPayload = AnchorPayload(
        path = filePath,
        srcPath = srcFilePath,
        line = lineNumber,
        lineType = diffChange.toApiString(),
        fileType = editorSide.toApiString(),
        diffType = "COMMIT",
        fromHash = fromHash,
        toHash = toHash
    )

    // ?? Enum helpers ??????????????????????????????????????????????????????

    private fun parseLineType(raw: String): LineType? = when (raw.uppercase()) {
        "ADDED"   -> LineType.ADDED
        "REMOVED" -> LineType.REMOVED
        "CONTEXT" -> LineType.CONTEXT
        else      -> null
    }

    private fun parseFileType(raw: String): FileType? = when (raw.uppercase()) {
        "FROM" -> FileType.FROM
        "TO"   -> FileType.TO
        else   -> null
    }

    private fun DiffChangeType.toApiString(): String = when (this) {
        DiffChangeType.INSERTED -> "ADDED"
        DiffChangeType.DELETED  -> "REMOVED"
        DiffChangeType.EQUAL    -> "CONTEXT"
    }

    private fun EditorSide.toApiString(): String = when (this) {
        EditorSide.LEFT  -> "FROM"
        EditorSide.RIGHT -> "TO"
    }
}

/**
 * Represents which side of the two-pane diff editor the user is interacting with.
 *
 * - [LEFT]  = the original / before-commit side (`FROM` in Bitbucket API)
 * - [RIGHT] = the modified / after-commit side (`TO` in Bitbucket API)
 */
enum class EditorSide { LEFT, RIGHT }

/**
 * Classifies the kind of change on a specific diff line.
 *
 * Maps directly to Bitbucket's `lineType` field:
 * - [INSERTED] ? `ADDED`
 * - [DELETED]  ? `REMOVED`
 * - [EQUAL]    ? `CONTEXT`
 */
enum class DiffChangeType { INSERTED, DELETED, EQUAL }

