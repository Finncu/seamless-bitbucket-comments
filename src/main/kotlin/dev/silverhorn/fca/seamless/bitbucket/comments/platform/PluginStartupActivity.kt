package dev.silverhorn.fca.seamless.bitbucket.comments.platform

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.silverhorn.fca.seamless.bitbucket.comments.api.client.BitbucketRestClient
import dev.silverhorn.fca.seamless.bitbucket.comments.platform.settings.BitbucketConfigurable
import dev.silverhorn.fca.seamless.bitbucket.comments.platform.settings.BitbucketSettingsState
import git4idea.repo.GitRepositoryManager

/**
 * Runs once after a project has been fully initialised.
 *
 * Responsibilities:
 * 1. Load the Personal Access Token from the OS credential store **on a background thread**
 *    (never on the EDT ? IntelliJ enforces this via slow-operation guards).
 * 2. Resolve and persist the current user's slug via the `whoami` API endpoint.
 * 3. Collect the most recent [BitbucketSettingsState.warmupCommitLimit] commit hashes
 *    from the local Git log and pre-populate the [CommentCache] in the background.
 *
 * Implementation note: [ProjectActivity] replaces the deprecated [com.intellij.openapi.startup.StartupActivity].
 * Coroutines are used for structured concurrency; all network I/O runs on `Dispatchers.IO`.
 */
class PluginStartupActivity : ProjectActivity {

    private val log = logger<PluginStartupActivity>()

    /**
     * Entry point called by the IntelliJ platform after the project has loaded.
     *
     * @param project the project that just finished loading
     */
    override suspend fun execute(project: Project) {
        val settings = BitbucketSettingsState.instance
        if (settings.serverUrl.isBlank() && GitRepositoryManager.getInstance(project).repositories.isEmpty()) {
            log.info("Neither Bitbucket URL nor Git repository context found ? skipping startup activity")
            return
        }

        // Run all I/O on a background thread via Task.Backgroundable.
        object : Task.Backgroundable(project, "Seamless Bitbucket Comments: Initialising", false) {
            override fun run(indicator: ProgressIndicator) {
                resolveCurrentUser(project, settings)
                warmCacheForVisibleCommits(project, settings)
            }
        }.queue()
    }

    // ?? Private helpers ???????????????????????????????????????????????????

    /**
     * Fetches the authenticated user's slug from the Bitbucket `whoami` endpoint and
     * stores it in [BitbucketSettingsState.userSlug] so the mention parser can use it.
     *
     * No-op when [settings] is incomplete or no token is stored.
     *
     * @param settings mutable settings state to update
     */
    private fun resolveCurrentUser(project: Project, settings: BitbucketSettingsState) {
        val context = BitbucketRepoContextResolver.resolve(project, settings)
            ?: run {
                log.info("No Bitbucket context resolved ? cannot resolve current user")
                return
            }

        val token = BitbucketConfigurable.loadToken(context.serverUrl) ?: run {
            log.info("No token in credential store ? cannot resolve current user")
            return
        }
        try {
            val client = BitbucketRestClient(context.serverUrl, context.projectKey, context.repoSlug, token)
            val user = client.getCurrentUser()
            if (user.slug.isNotBlank()) {
                settings.userSlug = user.slug
                log.info("Resolved Bitbucket user slug: ${user.slug}")
            }
        } catch (ex: Exception) {
            log.warn("Failed to resolve current Bitbucket user: ${ex.message}")
        }
    }

    /**
     * Collects recent commit hashes from the local Git repository and warms the
     * [CommentCache] via [CommitCommentService.warmCache].
     *
     * The number of hashes is limited by [BitbucketSettingsState.warmupCommitLimit]
     * to avoid saturating the Bitbucket API on large repositories.
     *
     * @param project  the current IntelliJ project
     * @param settings used to read [BitbucketSettingsState.warmupCommitLimit]
     */
    private fun warmCacheForVisibleCommits(project: Project, settings: BitbucketSettingsState) {
        try {
            val repoManager = GitRepositoryManager.getInstance(project)
            val hashes = repoManager.repositories
                .flatMap { repo ->
                    // Walk the current branch log and collect up to warmupCommitLimit hashes.
                    repo.info.currentRevision?.let { listOf(it) } ?: emptyList()
                }
                .take(settings.warmupCommitLimit)

            if (hashes.isEmpty()) return

            log.info("Warming comment cache for ${hashes.size} commit(s)")
            CommitCommentService.getInstance(project).warmCache(hashes)
        } catch (ex: Exception) {
            log.warn("Cache warmup failed: ${ex.message}")
        }
    }
}

