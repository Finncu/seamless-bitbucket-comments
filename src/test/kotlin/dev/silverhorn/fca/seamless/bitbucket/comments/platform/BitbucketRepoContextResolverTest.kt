package dev.silverhorn.fca.seamless.bitbucket.comments.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BitbucketRepoContextResolverTest {

    @Test
    fun `parses https scm remote`() {
        val ctx = BitbucketRepoContextResolver.parseRemoteUrl("https://bitbucket.example.com/scm/PROJ/my-repo.git")
        requireNotNull(ctx)
        assertEquals("https://bitbucket.example.com", ctx.serverUrl)
        assertEquals("PROJ", ctx.projectKey)
        assertEquals("my-repo", ctx.repoSlug)
    }

    @Test
    fun `parses https projects repos remote`() {
        val ctx = BitbucketRepoContextResolver.parseRemoteUrl("https://bitbucket.example.com/projects/PROJ/repos/my-repo")
        requireNotNull(ctx)
        assertEquals("https://bitbucket.example.com", ctx.serverUrl)
        assertEquals("PROJ", ctx.projectKey)
        assertEquals("my-repo", ctx.repoSlug)
    }

    @Test
    fun `parses scp style remote`() {
        val ctx = BitbucketRepoContextResolver.parseRemoteUrl("git@bitbucket.example.com:PROJ/my-repo.git")
        requireNotNull(ctx)
        assertEquals("https://bitbucket.example.com", ctx.serverUrl)
        assertEquals("PROJ", ctx.projectKey)
        assertEquals("my-repo", ctx.repoSlug)
    }

    @Test
    fun `parses ssh remote with port`() {
        val ctx = BitbucketRepoContextResolver.parseRemoteUrl("ssh://git@bitbucket.example.com:7999/PROJ/my-repo.git")
        requireNotNull(ctx)
        assertEquals("https://bitbucket.example.com", ctx.serverUrl)
        assertEquals("PROJ", ctx.projectKey)
        assertEquals("my-repo", ctx.repoSlug)
    }

    @Test
    fun `returns null for non bitbucket remote`() {
        assertNull(BitbucketRepoContextResolver.parseRemoteUrl("https://github.com/org/repo.git"))
    }
}

