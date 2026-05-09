package dev.silverhorn.fca.seamless.bitbucket.comments.domain.service

import dev.silverhorn.fca.seamless.bitbucket.comments.api.dto.AnchorPayload
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [AnchorMapper.toAnchorPayload].
 *
 * Covers all six combinations of [EditorSide] × [DiffChangeType] to ensure the
 * mapping to Bitbucket API string literals (`fileType`, `lineType`) is correct.
 */
class AnchorMapperTest {

    private val filePath = "src/main/java/App.java"
    private val fromHash = "aaaa".repeat(10)
    private val toHash   = "bbbb".repeat(10)

    @Test
    fun `right editor + inserted line maps to TO and ADDED`() {
        val anchor = build(EditorSide.RIGHT, DiffChangeType.INSERTED)
        assertEquals("TO", anchor.fileType)
        assertEquals("ADDED", anchor.lineType)
    }

    @Test
    fun `right editor + deleted line maps to TO and REMOVED`() {
        val anchor = build(EditorSide.RIGHT, DiffChangeType.DELETED)
        assertEquals("TO", anchor.fileType)
        assertEquals("REMOVED", anchor.lineType)
    }

    @Test
    fun `right editor + equal line maps to TO and CONTEXT`() {
        val anchor = build(EditorSide.RIGHT, DiffChangeType.EQUAL)
        assertEquals("TO", anchor.fileType)
        assertEquals("CONTEXT", anchor.lineType)
    }

    @Test
    fun `left editor + inserted line maps to FROM and ADDED`() {
        val anchor = build(EditorSide.LEFT, DiffChangeType.INSERTED)
        assertEquals("FROM", anchor.fileType)
        assertEquals("ADDED", anchor.lineType)
    }

    @Test
    fun `left editor + deleted line maps to FROM and REMOVED`() {
        val anchor = build(EditorSide.LEFT, DiffChangeType.DELETED)
        assertEquals("FROM", anchor.fileType)
        assertEquals("REMOVED", anchor.lineType)
    }

    @Test
    fun `left editor + equal line maps to FROM and CONTEXT`() {
        val anchor = build(EditorSide.LEFT, DiffChangeType.EQUAL)
        assertEquals("FROM", anchor.fileType)
        assertEquals("CONTEXT", anchor.lineType)
    }

    @Test
    fun `diffType is always COMMIT`() {
        val anchor = build(EditorSide.RIGHT, DiffChangeType.INSERTED)
        assertEquals("COMMIT", anchor.diffType)
    }

    @Test
    fun `line number is forwarded unchanged`() {
        val anchor = AnchorMapper.toAnchorPayload(
            EditorSide.RIGHT, DiffChangeType.EQUAL, 42, filePath, null, fromHash, toHash
        )
        assertEquals(42, anchor.line)
    }

    @Test
    fun `file path is forwarded unchanged`() {
        val anchor = build(EditorSide.RIGHT, DiffChangeType.EQUAL)
        assertEquals(filePath, anchor.path)
    }

    @Test
    fun `srcPath is null when not provided`() {
        val anchor = build(EditorSide.RIGHT, DiffChangeType.EQUAL)
        assertEquals(null, anchor.srcPath)
    }

    // ?? Helper ????????????????????????????????????????????????????????????

    private fun build(side: EditorSide, change: DiffChangeType): AnchorPayload =
        AnchorMapper.toAnchorPayload(side, change, 1, filePath, null, fromHash, toHash)
}

