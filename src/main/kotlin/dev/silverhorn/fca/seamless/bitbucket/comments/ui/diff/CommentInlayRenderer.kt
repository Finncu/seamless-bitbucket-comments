package dev.silverhorn.fca.seamless.bitbucket.comments.ui.diff

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.CommitComment
import java.awt.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Renders a Bitbucket commit comment as a block inlay element inside a diff editor.
 *
 * The inlay is inserted *above* the target line using [InlayModel.addBlockElement].
 * It is purely visual ? it does not modify the underlying document or trigger file saves.
 *
 * Visual structure (from top to bottom):
 * ```
 * ???????????????????????????????????????????????????
 * ? AuthorName  ?  2024-05-09 14:30               ?
 * ? Comment text (word-wrapped to editor width)   ?
 * ?   ? Reply author  ?  timestamp               ?
 * ?   ? Reply text                               ?
 * ???????????????????????????????????????????????????
 * ```
 */
class CommentInlayRenderer private constructor(
    private val comment: CommitComment
) : EditorCustomElementRenderer {

    // ?? Layout constants ??????????????????????????????????????????????????

    companion object {
        private const val PADDING_H = 8
        private const val PADDING_V = 4
        private const val INDENT_PX = 16
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        /**
         * Installs a block inlay for [comment] at [lineIndex] (0-based) in [editor].
         *
         * The inlay is automatically disposed when [parentDisposable] is disposed.
         *
         * @param editor           target editor (left or right side of the diff)
         * @param lineIndex        0-based line number in the editor document
         * @param comment          domain comment to render
         * @param project          current project (reserved for future interactive features)
         * @param commitHash       SHA-1 of the commit (reserved for reply action)
         * @param parentDisposable lifecycle parent; disposes the inlay when the diff viewer closes
         */
        fun install(
            editor: Editor,
            lineIndex: Int,
            comment: CommitComment,
            project: Project,
            commitHash: String,
            parentDisposable: Disposable
        ) {
            val document = editor.document
            if (lineIndex >= document.lineCount) return

            val offset = document.getLineStartOffset(lineIndex)
            val renderer = CommentInlayRenderer(comment)

            val inlay: Inlay<*>? = editor.inlayModel.addBlockElement(
                offset,
                /* relatesToPrecedingText = */ false,
                /* showAbove = */ true,
                /* priority = */ 0,
                renderer
            )

            if (inlay != null) {
                Disposer.register(parentDisposable, inlay)
            }
        }
    }

    // ?? EditorCustomElementRenderer ???????????????????????????????????????

    /**
     * Returns the total height in pixels required to draw this comment block,
     * including nested [CommitComment.children] (replies).
     *
     * Dynamically scaled to the editor's current font size.
     */
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = inlay.editor.contentComponent.width

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        val lineH = inlay.editor.lineHeight
        return heightForComment(comment, lineH, depth = 0)
    }

    /**
     * Paints the comment block using [Graphics2D] primitives.
     *
     * This method is called on the EDT; it must complete quickly.
     * No network I/O, no blocking operations.
     *
     * @param inlay          the inlay element (provides editor reference)
     * @param g              graphics context (cast to [Graphics2D] for anti-aliasing)
     * @param targetRegion   the rectangle allocated for this inlay by the editor
     * @param textAttributes current text attributes (colour theme)
     */
    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR)

        val editor = inlay.editor
        val lineH = editor.lineHeight
        var y = targetRegion.y

        // Draw background for the whole block.
        g2.color = UIUtil.getPanelBackground()
        g2.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height)

        // Draw a left-edge accent bar.
        g2.color = JBColor.GRAY
        g2.fillRect(targetRegion.x, targetRegion.y, 3, targetRegion.height)

        y += PADDING_V
        y = paintComment(g2, comment, targetRegion.x + PADDING_H, y, lineH, depth = 0)

        // Separator line at the bottom.
        g2.color = UIUtil.getSeparatorColor()
        g2.drawLine(targetRegion.x, y, targetRegion.x + targetRegion.width, y)
    }

    // ?? Private paint helpers ?????????????????????????????????????????????

    /**
     * Paints a single [CommitComment] and its [CommitComment.children] recursively.
     *
     * @param g2    graphics context
     * @param c     comment to paint
     * @param x     left edge in pixels
     * @param y     current top position in pixels (advanced downward with each line)
     * @param lineH line height from the editor in pixels
     * @param depth nesting depth; 0 for top-level comments, 1 for replies
     * @return updated y coordinate pointing to the next available row
     */
    private fun paintComment(g2: Graphics2D, c: CommitComment, x: Int, y: Int, lineH: Int, depth: Int): Int {
        var currentY = y
        val indentX = x + depth * INDENT_PX
        val font = g2.font

        // Header: author and date.
        val headerFont = font.deriveFont(Font.BOLD)
        g2.font = headerFont
        g2.color = UIUtil.getLabelForeground()
        val dateStr = DATE_FORMAT.format(Date(c.createdDate))
        g2.drawString("${c.authorName}  ?  $dateStr", indentX, currentY + g2.getFontMetrics().ascent)
        currentY += lineH

        // Body: comment text (simple, no Markdown rendering).
        g2.font = font
        g2.color = if (c.containsMention) JBColor.BLUE else UIUtil.getLabelForeground()
        for (line in c.text.lines()) {
            g2.drawString(line, indentX, currentY + g2.getFontMetrics().ascent)
            currentY += lineH
        }

        currentY += PADDING_V

        // Recursively paint replies.
        for (child in c.children) {
            currentY = paintComment(g2, child, x, currentY, lineH, depth + 1)
        }

        return currentY
    }

    /**
     * Computes the total pixel height needed for [c] including all nested replies.
     *
     * @param c      comment to measure
     * @param lineH  line height from the editor
     * @param depth  nesting depth (unused in height calc but kept for symmetry with [paintComment])
     */
    private fun heightForComment(c: CommitComment, lineH: Int, depth: Int): Int {
        // 1 line for header + lines of text + padding below
        val ownLines = 1 + c.text.lines().size
        val ownHeight = ownLines * lineH + PADDING_V * 2
        val childHeight = c.children.sumOf { child -> heightForComment(child, lineH, depth + 1) }
        return ownHeight + childHeight
    }
}


