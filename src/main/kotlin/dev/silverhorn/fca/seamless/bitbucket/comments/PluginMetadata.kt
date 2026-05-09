package dev.silverhorn.fca.seamless.bitbucket.comments

/**
 * Central place for plugin constants used by production code and tests.
 */
object PluginMetadata {
    /** Stable plugin ID as declared in plugin.xml. */
    const val PLUGIN_ID: String = "dev.silverhorn.fca.seamless_bitbucket_comments"

    /** Human-readable display name used in notifications and UI titles. */
    const val DISPLAY_NAME: String = "Seamless Bitbucket Comments"

    /** Stable ID for the settings configurable; referenced in plugin.xml and deep-links. */
    const val SETTINGS_CONFIGURABLE_ID: String = "dev.silverhorn.fca.seamless.bitbucket.comments.settings"

    /** Notification group ID as declared in plugin.xml. */
    const val NOTIFICATION_GROUP_ID: String = "SeamlessBitbucketComments"
}
