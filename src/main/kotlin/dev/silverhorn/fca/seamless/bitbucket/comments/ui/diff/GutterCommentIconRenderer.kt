package dev.silverhorn.fca.seamless.bitbucket.comments.ui.diff

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.silverhorn.fca.seamless.bitbucket.comments.api.dto.CommentPayload
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.service.AnchorMapper
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.service.DiffChangeType
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.service.EditorSide
import dev.silverhorn.fca.seamless.bitbucket.comments.platform.CommitCommentService
import javax.swing.Icon

/**
 * Adds a **+** gutter icon to each visible line in the two-sided diff viewer.
 *
 * When clicked, the icon triggers a lightweight input dialog allowing the user
 * to write a new inline comment.  On confirmation the comment is posted to
 * Bitbucket via [CommitCommentService] and rendered as a [CommentInlayRenderer].
 *
 * All gutter highlighters are removed when [parentDisposable] is disposed
 * (i.e. when the diff viewer is closed).
 */
class GutterCommentIconRenderer private constructor(
    private val editor: Editor,
    private val lineIndex: Int,           // 0-based
    private val editorSide: EditorSide,
    private val project: Project,
    private val filePath: String,
    private val fromHash: String,
    private val toHash: String
) : GutterIconRenderer() {

    // ?? GutterIconRenderer contract ???????????????????????????????????????

    /** Icon displayed in the gutter ? a small plus sign. */
    override fun getIcon(): Icon = AllIcons.General.Add

    /** Tooltip shown on mouse hover. */
    override fun getTooltipText(): String = "Add Bitbucket comment"

    /**
     * Equality is required by the Swing painting pipeline to detect changes.
     * Two renderers are equal when they address the same line and side.
     */
    override fun equals(other: Any?): Boolean =
        other is GutterCommentIconRenderer && other.lineIndex == lineIndex && other.editorSide == editorSide

    override fun hashCode(): Int = 31 * lineIndex.hashCode() + editorSide.hashCode()

    // ?? Click action ??????????????????????????????????????????????????????

    /**
     * Returns an [AnAction] that opens a simple comment-input popup and,
     * on confirmation, posts the new comment to Bitbucket.
     */
    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            openCommentInputDialog()
        }
    }

    override fun isNavigateAction(): Boolean = false

    // ?? Dialog and submit ?????????????????????????????????????????????????

    /**
     * Shows a lightweight input dialog.  On OK, builds an [AnchorMapper]-derived
     * [com.intellij.openapi.editor.markup.GutterIconRenderer] payload and posts it
     * via [CommitCommentService] on a background thread.
     */
    private fun openCommentInputDialog() {
        val text = com.intellij.openapi.ui.Messages.showInputDialog(
            project,
            "Enter your comment (Markdown supported):",
            "New Bitbucket Comment",
            null
        )?.takeIf { it.isNotBlank() } ?: return

        // Determine the change type at this line from the diff model.
        val diffChange = resolveDiffChangeType()

        val anchorPayload = AnchorMapper.toAnchorPayload(
            editorSide = editorSide,
            diffChange = diffChange,
            lineNumber = lineIndex + 1, // Bitbucket uses 1-based line numbers
            filePath = filePath,
            fromHash = fromHash,
            toHash = toHash
        )

        val payload = CommentPayload(text = text, anchor = anchorPayload)

        object : Task.Backgroundable(project, "Posting comment?", false) {
            override fun run(indicator: ProgressIndicator) {
                val created = CommitCommentService.getInstance(project).postComment(toHash, payload)
                if (created != null) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        CommentInlayRenderer.install(
                            editor, lineIndex, created, project, toHash,
                            /* parentDisposable = */ project
                        )
                    }
                }
            }
        }.queue()
    }

    /**
     * Determines the diff change type for the current line by inspecting the editor's
     * diff highlighting model.  Falls back to [DiffChangeType.EQUAL] (CONTEXT) when
     * no specific change can be determined.
     */
    private fun resolveDiffChangeType(): DiffChangeType {
        // Access the highlighters on the markup model to detect inserted/deleted lines.
        val markupModel = editor.markupModel
        val lineStart = editor.document.getLineStartOffset(lineIndex)
        val lineEnd = editor.document.getLineEndOffset(lineIndex)

        for (highlighter in markupModel.allHighlighters) {
            if (highlighter.startOffset > lineEnd || highlighter.endOffset < lineStart) continue
            val textAttr = highlighter.textAttributes ?: continue
            val bg = textAttr.backgroundColor ?: continue

            // Heuristic: IntelliJ colours ADDED lines green and DELETED lines red.
            return when {
                bg.green > bg.red + bg.blue -> DiffChangeType.INSERTED
                bg.red > bg.green + bg.blue -> DiffChangeType.DELETED
                else -> DiffChangeType.EQUAL
            }
        }
        return DiffChangeType.EQUAL
    }

    // ?? Factory (installs gutter icons on all lines) ??????????????????????

    companion object {

        /**
         * Iterates over all lines in both editors and attaches a [GutterCommentIconRenderer]
         * to each one.
         *
         * All created [RangeHighlighter] objects are registered against [parentDisposable]
         * so they are removed when the diff viewer closes.
         *
         * @param leftEditor       the left (FROM) editor panel
         * @param rightEditor      the right (TO) editor panel
         * @param project          current project
         * @param filePath         repository-relative file path shown in the diff
         * @param fromHash         SHA-1 of the base commit
         * @param toHash           SHA-1 of the head commit
         * @param parentDisposable lifecycle parent (the diff viewer's [Disposable])
         */
        fun installOnAllLines(
            leftEditor: Editor,
            rightEditor: Editor,
            project: Project,
            filePath: String,
            fromHash: String,
            toHash: String,
            parentDisposable: Disposable
        ) {
            installForEditor(leftEditor, EditorSide.LEFT, project, filePath, fromHash, toHash, parentDisposable)
            installForEditor(rightEditor, EditorSide.RIGHT, project, filePath, fromHash, toHash, parentDisposable)
        }

        /**
         * Installs gutter icons on every line of [editor].
         *
         * @param editor           target editor
         * @param side             which side of the diff this editor represents
         * @param project          current project
         * @param filePath         repository-relative file path
         * @param fromHash         SHA-1 of the base commit
         * @param toHash           SHA-1 of the head commit
         * @param parentDisposable lifecycle parent
         */
        private fun installForEditor(
            editor: Editor,
            side: EditorSide,
            project: Project,
            filePath: String,
            fromHash: String,
            toHash: String,
            parentDisposable: Disposable
        ) {
            val document = editor.document
            val markupModel = editor.markupModel

            val (startLine, endLine) = visibleLineRange(editor, document.lineCount)

            for (line in startLine..endLine) {
                val lineStart = document.getLineStartOffset(line)
                val highlighter: RangeHighlighter = markupModel.addRangeHighlighter(
                    lineStart, lineStart,
                    HighlighterLayer.LAST,
                    null,
                    HighlighterTargetArea.LINES_IN_RANGE
                )

                highlighter.gutterIconRenderer = GutterCommentIconRenderer(
                    editor = editor,
                    lineIndex = line,
                    editorSide = side,
                    project = project,
                    filePath = filePath,
                    fromHash = fromHash,
                    toHash = toHash
                )

                Disposer.register(parentDisposable) { highlighter.dispose() }
            }
        }

        /**
         * Returns the current visible line range in the editor.
         * This avoids creating thousands of highlighters on large files.
         */
        private fun visibleLineRange(editor: Editor, lineCount: Int): Pair<Int, Int> {
            if (lineCount <= 0) return 0 to 0

            val area = editor.scrollingModel.visibleArea
            val start = editor.xyToLogicalPosition(java.awt.Point(0, area.y)).line
            val end = editor.xyToLogicalPosition(java.awt.Point(0, area.y + area.height)).line

            val clampedStart = start.coerceIn(0, lineCount - 1)
            val clampedEnd = end.coerceIn(clampedStart, lineCount - 1)
            return clampedStart to clampedEnd
        }
    }
}

