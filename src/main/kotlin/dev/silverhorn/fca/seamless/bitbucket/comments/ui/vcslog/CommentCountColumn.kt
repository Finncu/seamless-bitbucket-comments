package dev.silverhorn.fca.seamless.bitbucket.comments.ui.vcslog

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.column.VcsLogCustomColumn
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.CommitCommentSummary
import dev.silverhorn.fca.seamless.bitbucket.comments.platform.CommentCache
import dev.silverhorn.fca.seamless.bitbucket.comments.platform.CommitCommentService
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.JLabel
import javax.swing.table.TableCellRenderer

/**
 * Custom column in the VCS log table that renders a badge showing:
 * - total comment count (grey-toned text, left side of badge)
 * - mention count in blue (right side of badge; hidden when 0)
 *
 * The column queries the [CommentCache]; on a cache miss it schedules a
 * background fetch and repaint once data is available.
 *
 * Registered in `plugin.xml` as `<vcs.log.custom.column>`.
 */
class CommentCountColumn : VcsLogCustomColumn<CommitCommentSummary?> {

    // ?? VcsLogColumn identity ?????????????????????????????????????????????

    /** Stable column identifier; used by the settings persistence layer. */
    override val id: String = "sbc.commentCount"

    /** Label shown in the column header and the toggle menu. */
    override val localizedName: String = "Comments"

    /**
     * Dynamic columns recalculate their value on every log refresh.
     * Returning true ensures the badge stays up to date after cache population.
     */
    override val isDynamic: Boolean = true

    /** Shown by default so users immediately see comment activity. */
    override fun isEnabledByDefault(): Boolean = true

    // ?? Data access ???????????????????????????????????????????????????????

    /**
     * Returns the [CommitCommentSummary] from the cache for the commit at [row],
     * or null on a cache miss.
     *
     * A cache miss also schedules a background load; once the cache entry is
     * written, the column repaints automatically via [isDynamic].
     *
     * @param model the VCS log table model providing commit metadata
     * @param row   0-based row index in the visible table
     */
    override fun getValue(model: GraphTableModel, row: Int): CommitCommentSummary? {
        val metadata = runCatching { model.getCommitMetadata(row) }.getOrNull() ?: return null
        val hash = metadata.id.asString()
        val cached = CommentCache.instance.get(hash)
        if (cached != null) return cached

        // Kick off a background load without blocking the EDT.
        val project = model.logData.project
        object : Task.Backgroundable(project, "", false) {
            override fun run(indicator: ProgressIndicator) {
                CommitCommentService.getInstance(project).fetchAllComments(hash)
                // isDynamic() causes the table to re-query getValue on next repaint.
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    model.fireTableRowsUpdated(row, row)
                }
            }
        }.queue()

        return null
    }

    /**
     * Stub value used while the real value is being loaded asynchronously.
     * Returning null renders an empty cell.
     */
    override fun getStubValue(model: GraphTableModel): CommitCommentSummary? = null

    // ?? Renderer factory ??????????????????????????????????????????????????

    /**
     * Returns the [TableCellRenderer] used to paint badge cells.
     * A new renderer per column is sufficient; the same instance handles all rows.
     *
     * @param table the VCS log graph table (not used directly)
     */
    override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer =
        CommentBadgeRenderer()
}

/**
 * Swing [TableCellRenderer] that draws the comment count badge for a single VCS log row.
 *
 * Rendering rules:
 * - When value is null (cache miss / no comments) ? empty cell
 * - When [CommitCommentSummary.totalCount] is 0 ? empty cell
 * - Otherwise ? rounded badge with grey total + optional blue mention count
 */
internal class CommentBadgeRenderer : JLabel(), TableCellRenderer {

    private val roundRect = RoundRectangle2D.Float()
    private val badgePadH = 6f
    private val badgePadV = 2f
    private val arcSize = 8f

    init {
        isOpaque = false
    }

    override fun getTableCellRendererComponent(
        table: javax.swing.JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): java.awt.Component {
        summary = value as? CommitCommentSummary
        background = if (isSelected) table?.selectionBackground ?: UIUtil.getTableBackground()
        else UIUtil.getTableBackground()
        return this
    }

    @Volatile
    private var summary: CommitCommentSummary? = null

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val s = summary ?: return
        if (s.totalCount == 0) return

        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR)

        val font = g2.font.deriveFont(Font.PLAIN, g2.font.size2D - 1f)
        g2.font = font
        val fm = g2.getFontMetrics(font)

        val totalText = s.totalCount.toString()
        val mentionText = if (s.hasMentions) s.mentionCount.toString() else ""

        val totalW = fm.stringWidth(totalText)
        val mentionW = if (mentionText.isNotEmpty()) fm.stringWidth(" $mentionText") else 0
        val textH = fm.ascent + fm.descent

        val badgeW = badgePadH * 2 + totalW + mentionW
        val badgeH = textH + badgePadV * 2

        val x = badgePadH
        val y = (height - badgeH) / 2f

        // Draw pill background.
        g2.color = UIUtil.getPanelBackground().darker()
        roundRect.setRoundRect(x, y, badgeW, badgeH, arcSize, arcSize)
        g2.fill(roundRect)

        // Total count in default foreground.
        g2.color = UIUtil.getLabelForeground()
        g2.drawString(totalText, (x + badgePadH).toInt(), (y + badgePadV + fm.ascent).toInt())

        // Mention count in blue.
        if (mentionText.isNotEmpty()) {
            g2.color = JBColor.BLUE
            val mentionX = x + badgePadH + totalW
            g2.drawString(" $mentionText", mentionX.toInt(), (y + badgePadV + fm.ascent).toInt())
        }
    }
}


