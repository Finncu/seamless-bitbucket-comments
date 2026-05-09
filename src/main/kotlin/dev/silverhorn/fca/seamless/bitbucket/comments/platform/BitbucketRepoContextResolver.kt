package dev.silverhorn.fca.seamless.bitbucket.comments.platform

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import dev.silverhorn.fca.seamless.bitbucket.comments.platform.settings.BitbucketSettingsState
import git4idea.repo.GitRepositoryManager

/**
 * Resolved Bitbucket repository context required for REST calls.
 *
 * @property serverUrl base URL of Bitbucket server, e.g. `https://bitbucket.company.tld`
 * @property projectKey Bitbucket project key, e.g. `PROJ`
 * @property repoSlug Bitbucket repository slug, e.g. `my-repo`
 */
data class BitbucketRepoContext(
    val serverUrl: String,
    val projectKey: String,
    val repoSlug: String
)

/**
 * Resolves Bitbucket repository metadata from the workspace (Git/IntelliJ) first,
 * with a fallback to manually configured settings.
 */
object BitbucketRepoContextResolver {

    private val log = logger<BitbucketRepoContextResolver>()

    /**
     * Resolves context from Git remotes in the current project.
     * Falls back to [settings] only when no parsable Bitbucket remote is found.
     */
    fun resolve(project: Project, settings: BitbucketSettingsState): BitbucketRepoContext? {
        val fromGit = resolveFromGit(project)
        if (fromGit != null) return fromGit

        if (settings.serverUrl.isNotBlank() && settings.projectKey.isNotBlank() && settings.repoSlug.isNotBlank()) {
            return BitbucketRepoContext(
                serverUrl = settings.serverUrl.trimEnd('/'),
                projectKey = settings.projectKey,
                repoSlug = settings.repoSlug
            )
        }

        return null
    }

    private fun resolveFromGit(project: Project): BitbucketRepoContext? {
        val repoManager = GitRepositoryManager.getInstance(project)

        for (repo in repoManager.repositories) {
            for (remote in repo.remotes) {
                for (url in remote.urls) {
                    val parsed = parseRemoteUrl(url)
                    if (parsed != null) {
                        log.debug("Resolved Bitbucket context from remote '$url'")
                        return parsed
                    }
                }
            }
        }

        return null
    }

    /**
     * Parses common Bitbucket Server remote URL shapes:
     * - https://host/scm/PROJ/repo.git
     * - https://host/projects/PROJ/repos/repo
     * - git@host:PROJ/repo.git
     * - ssh://git@host:7999/PROJ/repo.git
     */
    internal fun parseRemoteUrl(rawUrl: String): BitbucketRepoContext? {
        val url = rawUrl.trim()
        if (url.isBlank()) return null

        parseHttp(url)?.let { return it }
        parseScpLike(url)?.let { return it }
        parseSsh(url)?.let { return it }

        return null
    }

    private fun parseHttp(url: String): BitbucketRepoContext? {
        val scmRegex = Regex("^(https?://[^/]+)/scm/([^/]+)/([^/.]+)(?:\\.git)?/?$")
        val projectsRegex = Regex("^(https?://[^/]+)/projects/([^/]+)/repos/([^/]+?)(?:\\.git)?/?$")

        val scm = scmRegex.matchEntire(url)
        if (scm != null) {
            return BitbucketRepoContext(
                serverUrl = scm.groupValues[1],
                projectKey = scm.groupValues[2],
                repoSlug = scm.groupValues[3]
            )
        }

        val projects = projectsRegex.matchEntire(url)
        if (projects != null) {
            return BitbucketRepoContext(
                serverUrl = projects.groupValues[1],
                projectKey = projects.groupValues[2],
                repoSlug = projects.groupValues[3]
            )
        }

        return null
    }

    private fun parseScpLike(url: String): BitbucketRepoContext? {
        val scpRegex = Regex("^git@([^:]+):([^/]+)/([^/.]+)(?:\\.git)?$")
        val match = scpRegex.matchEntire(url) ?: return null

        return BitbucketRepoContext(
            serverUrl = "https://${match.groupValues[1]}",
            projectKey = match.groupValues[2],
            repoSlug = match.groupValues[3]
        )
    }

    private fun parseSsh(url: String): BitbucketRepoContext? {
        val sshRegex = Regex("^ssh://git@([^/:]+)(?::\\d+)?/([^/]+)/([^/.]+)(?:\\.git)?/?$")
        val match = sshRegex.matchEntire(url) ?: return null

        return BitbucketRepoContext(
            serverUrl = "https://${match.groupValues[1]}",
            projectKey = match.groupValues[2],
            repoSlug = match.groupValues[3]
        )
    }
}

