package dev.silverhorn.fca.seamless.bitbucket.comments.platform.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Application-scoped persistent state holder for non-sensitive settings.
 *
 * Sensitive data (Personal Access Token) is **not** stored here; it is written
 * to and read from the operating-system credential store via
 * [com.intellij.ide.passwordSafe.PasswordSafe] in [BitbucketConfigurable].
 *
 * The state is serialised to `~/.<IDE>/config/options/bitbucketSettings.xml`.
 *
 * Usage:
 * ```kotlin
 * val state = BitbucketSettingsState.instance
 * val url = state.serverUrl
 * ```
 *
 * @property serverUrl   base URL of the Bitbucket Server instance, e.g. `https://bitbucket.example.com`
 * @property projectKey  Bitbucket project key, e.g. `PROJ`
 * @property repoSlug   repository slug, e.g. `my-repo`
 * @property userSlug   login slug of the authenticated user; populated on first successful API call
 * @property warmupCommitLimit number of commits to pre-fetch on IDE startup for badge warmup
 */
@Service(Service.Level.APP)
@State(name = "BitbucketSettings", storages = [Storage("bitbucketSettings.xml")])
class BitbucketSettingsState : PersistentStateComponent<BitbucketSettingsState> {

    var serverUrl: String = ""
    var projectKey: String = ""
    var repoSlug: String = ""
    var userSlug: String = ""
    var warmupCommitLimit: Int = 50

    /**
     * Returns this instance as its own state snapshot.
     * IntelliJ calls this method to serialise the current configuration.
     */
    override fun getState(): BitbucketSettingsState = this

    /**
     * Restores the state by copying field values from [state] into this instance.
     * IntelliJ calls this method during plugin initialisation.
     *
     * @param state the previously persisted state loaded from disk
     */
    override fun loadState(state: BitbucketSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        /**
         * Returns the application-level singleton instance.
         * Convenience accessor to avoid calling [com.intellij.openapi.application.ApplicationManager] directly.
         */
        val instance: BitbucketSettingsState
            get() = com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(BitbucketSettingsState::class.java)
    }
}

