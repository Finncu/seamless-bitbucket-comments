package dev.silverhorn.fca.seamless.bitbucket.comments.platform.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import dev.silverhorn.fca.seamless.bitbucket.comments.PluginMetadata
import dev.silverhorn.fca.seamless.bitbucket.comments.api.client.BitbucketRestClient
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings panel registered under **Settings ? Tools ? Seamless Bitbucket Comments**.
 *
 * Stores non-sensitive fields (server URL, project key, repo slug) via
 * [BitbucketSettingsState] and persists the Personal Access Token exclusively
 * through the OS credential store via [PasswordSafe] ? never in plain text.
 *
 * Token reads from [PasswordSafe] must NOT happen on the EDT; the [apply] method
 * therefore delegates the credential-save operation to a background thread.
 */
class BitbucketConfigurable : SearchableConfigurable {

    // ?? UI fields ??????????????????????????????????????????????????????????

    private val serverUrlField = JBTextField()
    private val projectKeyField = JBTextField()
    private val repoSlugField = JBTextField()
    private val tokenField = JBPasswordField()

    private var panel: JPanel? = null

    // ?? SearchableConfigurable ?????????????????????????????????????????????

    /** Stable identifier for deep-linking from the settings search. */
    override fun getId(): String = PluginMetadata.SETTINGS_CONFIGURABLE_ID

    /** Display name shown in the settings tree and search results. */
    override fun getDisplayName(): String = "Seamless Bitbucket Comments"

    /**
     * Constructs and returns the settings panel.
     * Called by the framework on the EDT when the user opens the settings page.
     */
    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Server URL:"), serverUrlField, true)
//            .addLabeledComponent(JBLabel("Project key:"), projectKeyField, true)
//            .addLabeledComponent(JBLabel("Repository slug:"), repoSlugField, true)
            .addLabeledComponent(JBLabel("Personal Access Token:"), tokenField, true)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return panel!!
    }

    /**
     * Checks whether the UI values differ from the currently persisted state.
     * The framework calls this to decide whether to enable the [apply] button.
     */
    override fun isModified(): Boolean {
        val state = BitbucketSettingsState.instance
        return serverUrlField.text != state.serverUrl
            || projectKeyField.text != state.projectKey
            || repoSlugField.text != state.repoSlug
            || tokenField.password.isNotEmpty()
    }

    /**
     * Persists changes when the user clicks **Apply** or **OK**.
     *
     * Non-sensitive fields are written to [BitbucketSettingsState].
     * The token is stored in the OS credential store via [PasswordSafe] on a
     * background thread to avoid blocking the EDT.
     */
    override fun apply() {
        val state = BitbucketSettingsState.instance
        state.serverUrl = serverUrlField.text.trimEnd('/')
        state.projectKey = projectKeyField.text.trim()
        state.repoSlug = repoSlugField.text.trim()

        val tokenChars = tokenField.password
        if (tokenChars.isNotEmpty()) {
            val token = String(tokenChars)
            val attrs = credentialAttributes(state.serverUrl)
            ApplicationManager.getApplication().executeOnPooledThread {
                PasswordSafe.instance.set(attrs, Credentials(state.userSlug, token))
            }
            tokenChars.fill('\u0000') // wipe from memory
        }
    }

    /**
     * Resets the UI to reflect the currently persisted state.
     * Called when the user clicks **Reset** or when the dialog is opened.
     * The token field is intentionally left empty for security reasons.
     */
    override fun reset() {
        val state = BitbucketSettingsState.instance
        serverUrlField.text = state.serverUrl
        projectKeyField.text = state.projectKey
        repoSlugField.text = state.repoSlug
        tokenField.text = "" // never pre-fill the token field
    }

    override fun disposeUIResources() {
        panel = null
    }

    // ?? Helpers ???????????????????????????????????????????????????????????

    companion object {
        /**
         * Builds a [CredentialAttributes] key that is unique per plugin and server URL.
         * Using both prevents collision when the user connects to multiple Bitbucket instances.
         *
         * @param serverUrl base URL of the Bitbucket Server
         * @return attributes suitable for [PasswordSafe.get] / [PasswordSafe.set]
         */
        fun credentialAttributes(serverUrl: String): CredentialAttributes =
            CredentialAttributes(generateServiceName(PluginMetadata.PLUGIN_ID, serverUrl))

        /**
         * Retrieves the stored token for [serverUrl] synchronously.
         *
         * **Must be called from a background thread.** Calling this on the EDT will
         * result in an `IllegalStateException` from IntelliJ's slow-operation guard.
         *
         * @param serverUrl base URL of the Bitbucket Server
         * @return the stored token, or null if none has been saved yet
         */
        fun loadToken(serverUrl: String): String? {
            val attrs = credentialAttributes(serverUrl)
            return PasswordSafe.instance.get(attrs)?.getPasswordAsString()
        }
    }
}

