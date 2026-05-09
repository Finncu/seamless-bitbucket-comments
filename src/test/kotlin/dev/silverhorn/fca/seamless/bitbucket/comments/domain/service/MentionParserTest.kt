package dev.silverhorn.fca.seamless.bitbucket.comments.domain.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Unit tests for [MentionParser].
 *
 * All tests are pure (no I/O, no IntelliJ platform required) and run in the
 * standard JUnit 5 / kotlin.test test runner.
 */
class MentionParserTest {

    // ?? containsMention ???????????????????????????????????????????????????

    @Test
    fun `containsMention returns true for exact slug mention`() {
        assertTrue(MentionParser.containsMention("Hey @[~jdoe] can you review?", "jdoe"))
    }

    @Test
    fun `containsMention returns false when slug differs`() {
        assertFalse(MentionParser.containsMention("Hey @[~jdoe] can you review?", "jsmith"))
    }

    @Test
    fun `containsMention is case sensitive`() {
        assertFalse(MentionParser.containsMention("@[~JDoe]", "jdoe"))
    }

    @Test
    fun `containsMention returns false for blank text`() {
        assertFalse(MentionParser.containsMention("", "jdoe"))
    }

    @Test
    fun `containsMention returns false for blank slug`() {
        assertFalse(MentionParser.containsMention("@[~jdoe]", ""))
    }

    @Test
    fun `containsMention works for mention at start of text`() {
        assertTrue(MentionParser.containsMention("@[~alice] please fix this", "alice"))
    }

    @Test
    fun `containsMention works for mention at end of text`() {
        assertTrue(MentionParser.containsMention("Good catch @[~bob]", "bob"))
    }

    @Test
    fun `containsMention returns false when only partial slug matches`() {
        // @[~jdoe2] must not match for slug "jdoe"
        assertFalse(MentionParser.containsMention("@[~jdoe2] check this", "jdoe"))
    }

    // ?? countMentions ?????????????????????????????????????????????????????

    @Test
    fun `countMentions returns 0 for no mentions`() {
        assertEquals(0, MentionParser.countMentions("No mentions here.", "jdoe"))
    }

    @Test
    fun `countMentions returns 1 for single mention`() {
        assertEquals(1, MentionParser.countMentions("@[~jdoe] please fix", "jdoe"))
    }

    @Test
    fun `countMentions returns 2 for two mentions in same text`() {
        assertEquals(2, MentionParser.countMentions("@[~jdoe] and @[~jdoe] again", "jdoe"))
    }
}

