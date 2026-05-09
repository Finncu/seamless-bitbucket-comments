package dev.silverhorn.fca.seamless.bitbucket.comments.domain.service

/**
 * Pure, stateless Markdown mention scanner for Bitbucket Server comment texts.
 *
 * Bitbucket Server 9.x encodes user mentions as `@[~slug]` within comment Markdown.
 * This object compiles a [Regex] per user slug and scans raw comment text for matches.
 *
 * Design decisions:
 * - Slug-only detection; no UUID-based mention format is supported (not required).
 * - Pattern is compiled once per [containsMention] invocation but cached between
 *   repeated calls for the same slug via [cachedPattern].
 * - All functions are pure (no side effects, no I/O).
 */
object MentionParser {

    /** Caches the last compiled pattern to avoid recompilation on repeated calls with the same slug. */
    @Volatile
    private var cachedSlug: String = ""

    @Volatile
    private var cachedPattern: Regex = Regex("")

    /**
     * Returns true when [text] contains at least one mention of the user identified by [userSlug].
     *
     * The Bitbucket Server mention syntax is `@[~slug]`.  This method searches for an exact slug
     * match (case-sensitive, as Bitbucket slugs are case-sensitive).
     *
     * @param text      raw Markdown text of a comment (the `content.raw` field from the API)
     * @param userSlug  login slug of the current user, e.g. `jdoe`
     * @return true if the text contains `@[~jdoe]`
     */
    fun containsMention(text: String, userSlug: String): Boolean {
        if (userSlug.isBlank() || text.isBlank()) return false
        val pattern = patternFor(userSlug)
        return pattern.containsMatchIn(text)
    }

    /**
     * Counts the number of distinct mention occurrences of [userSlug] in [text].
     *
     * Useful when correlating mention density for analytics or future badge enhancements.
     *
     * @param text      raw Markdown text of a comment
     * @param userSlug  login slug of the current user
     * @return number of `@[~slug]` occurrences in the text
     */
    fun countMentions(text: String, userSlug: String): Int {
        if (userSlug.isBlank() || text.isBlank()) return 0
        return patternFor(userSlug).findAll(text).count()
    }

    /**
     * Returns a compiled [Regex] for the given [slug], reusing the cached instance
     * when the slug has not changed.
     *
     * The pattern targets the literal string `@[~slug]`.  The brackets and tilde are
     * escaped because they carry special meaning in regex syntax.
     */
    private fun patternFor(slug: String): Regex {
        if (slug == cachedSlug) return cachedPattern
        val pattern = Regex(Regex.escape("@[~$slug]"))
        cachedSlug = slug
        cachedPattern = pattern
        return pattern
    }
}

