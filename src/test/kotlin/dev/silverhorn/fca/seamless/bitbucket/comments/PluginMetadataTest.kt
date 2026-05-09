package dev.silverhorn.fca.seamless.bitbucket.comments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginMetadataTest {

    @Test
    fun `plugin id uses expected namespace`() {
        assertEquals("dev.silverhorn.fca.seamless_bitbucket_comments", PluginMetadata.PLUGIN_ID)
    }

    @Test
    fun `display name is human readable`() {
        assertTrue(PluginMetadata.DISPLAY_NAME.contains(" "))
    }
}

