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

    @Test
    fun `settings configurable id matches plugin id prefix`() {
        assertTrue(PluginMetadata.SETTINGS_CONFIGURABLE_ID.startsWith("dev.silverhorn.fca"))
    }

    @Test
    fun `notification group id is not blank`() {
        assertTrue(PluginMetadata.NOTIFICATION_GROUP_ID.isNotBlank())
    }
}
