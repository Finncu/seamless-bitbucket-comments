package dev.silverhorn.fca.seamless.bitbucket.comments.api.client

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.silverhorn.fca.seamless.bitbucket.comments.api.BitbucketApiException
import dev.silverhorn.fca.seamless.bitbucket.comments.api.dto.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/**
 * Stateless HTTP client for the Bitbucket Server REST API v1.0.
 *
 * All methods are blocking and intended to be called from a background
 * thread (e.g. `Dispatchers.IO` coroutine scope or `Task.Backgroundable`).
 * Never invoke from the IntelliJ Event Dispatch Thread (EDT).
 *
 * The client performs transparent paging: list-returning methods keep
 * issuing requests until `isLastPage == true` and then return the
 * fully accumulated result.
 *
 * @property serverUrl     base URL of the Bitbucket Server, e.g. `https://bitbucket.example.com`
 * @property projectKey    Bitbucket project key, e.g. `PROJ`
 * @property repoSlug      repository slug, e.g. `my-repo`
 * @property bearerToken   Personal Access Token used in the `Authorization: Bearer` header
 */
class BitbucketRestClient(
    private val serverUrl: String,
    private val projectKey: String,
    private val repoSlug: String,
    private val bearerToken: String
) {

    // ?? Jackson ????????????????????????????????????????????????????????????

    /** Shared Jackson mapper; silently ignores fields added in future server versions. */
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // ?? JDK HTTP client ????????????????????????????????????????????????????

    /** JDK 21 HttpClient shared across all requests. Thread-safe and reusable. */
    private val http: HttpClient = HttpClient.newHttpClient()

    // ?? Base paths ?????????????????????????????????????????????????????????

    /** Base path for all commit-related endpoints in this repository. */
    private val repoBase: String
        get() = "$serverUrl/rest/api/1.0/projects/$projectKey/repos/$repoSlug"

    // ?? Public API ????????????????????????????????????????????????????????

    /**
     * Resolves the currently authenticated user by calling the whoami servlet.
     *
     * Used during plugin startup to obtain the user slug for mention detection.
     *
     * @return [UserDto] containing slug, displayName and emailAddress
     * @throws BitbucketApiException on non-2xx responses
     */
    fun getCurrentUser(): UserDto {
        // The whoami endpoint returns just the user object directly, not wrapped.
        val raw = get("$serverUrl/plugins/servlet/applinks/whoami")
        return try {
            mapper.readValue(raw, UserDto::class.java)
        } catch (_: Exception) {
            // Older Bitbucket versions may wrap it ? try the wrapper form.
            mapper.readValue(raw, WhoAmIDto::class.java).user
        }
    }

    /**
     * Returns the list of all files changed by the given commit.
     *
     * Internally pages through the `changes` endpoint until [PagedResponse.isLastPage]
     * is true.  The returned paths can then be used as the mandatory `?path=` parameter
     * when fetching commit comments.
     *
     * @param commitHash full 40-character SHA-1 of the commit
     * @return all [ChangedFileDto] entries across all pages
     * @throws BitbucketApiException on non-2xx responses
     */
    fun getCommitChanges(commitHash: String): List<ChangedFileDto> {
        val typeRef = object : TypeReference<PagedResponse<ChangedFileDto>>() {}
        return fetchAllPages("$repoBase/commits/$commitHash/changes", typeRef)
    }

    /**
     * Returns all comments anchored to a specific file in a commit.
     *
     * The Bitbucket Server API requires the `path` query parameter; omitting it
     * results in a 400 response with the message
     * "The path query parameter is required when retrieving comments".
     *
     * @param commitHash full SHA-1 of the commit
     * @param filePath   repository-relative file path without a leading slash
     * @return all [CommentDto] objects for this file across all pages
     * @throws BitbucketApiException on non-2xx responses
     */
    fun getFileComments(commitHash: String, filePath: String): List<CommentDto> {
        val encoded = URLEncoder.encode(filePath, StandardCharsets.UTF_8)
        val typeRef = object : TypeReference<PagedResponse<CommentDto>>() {}
        return fetchAllPages("$repoBase/commits/$commitHash/comments?path=$encoded", typeRef)
    }

    /**
     * Creates a new comment on a commit.
     *
     * For an inline comment, [payload] must contain a fully populated [dev.silverhorn.fca.seamless.bitbucket.comments.api.dto.AnchorPayload].
     * For a reply, set [dev.silverhorn.fca.seamless.bitbucket.comments.api.dto.CommentPayload.parent].
     * For a global comment, leave both null.
     *
     * @param commitHash full SHA-1 of the commit
     * @param payload    serialisable comment payload
     * @return the newly created [CommentDto] as returned by the server
     * @throws BitbucketApiException on non-2xx responses
     */
    fun postComment(commitHash: String, payload: CommentPayload): CommentDto {
        val body = mapper.writeValueAsString(payload)
        val raw = post("$repoBase/commits/$commitHash/comments", body)
        return mapper.readValue(raw, CommentDto::class.java)
    }

    /**
     * Updates the text of an existing comment.
     *
     * The `version` parameter implements optimistic locking: pass the [CommentDto.version]
     * value received when the comment was last fetched.  The server rejects stale updates
     * with a 409 Conflict.
     *
     * @param commitHash full SHA-1 of the commit
     * @param commentId  server-assigned comment ID
     * @param version    current version of the comment (from the last GET response)
     * @param newText    replacement Markdown text
     * @return the updated [CommentDto]
     * @throws BitbucketApiException on non-2xx responses
     */
    fun updateComment(commitHash: String, commentId: Long, version: Int, newText: String): CommentDto {
        val body = mapper.writeValueAsString(mapOf("version" to version, "text" to newText))
        val raw = put("$repoBase/commits/$commitHash/comments/$commentId", body)
        return mapper.readValue(raw, CommentDto::class.java)
    }

    /**
     * Permanently deletes a comment from a commit.
     *
     * The `version` parameter is required by the API for optimistic locking.
     *
     * @param commitHash full SHA-1 of the commit
     * @param commentId  server-assigned comment ID
     * @param version    current version of the comment
     * @throws BitbucketApiException on non-2xx responses
     */
    fun deleteComment(commitHash: String, commentId: Long, version: Int) {
        delete("$repoBase/commits/$commitHash/comments/$commentId?version=$version")
    }

    // ?? Paging helper ?????????????????????????????????????????????????????

    /**
     * Fetches all pages for a list endpoint and returns the accumulated values.
     *
     * Starts at `start=0` and appends `&start={nextPageStart}` until the server
     * signals [PagedResponse.isLastPage] == true.
     *
     * @param baseUrl  URL without `start` parameter (may already contain `?key=value`)
     * @param typeRef  Jackson type reference for deserialization
     */
    private fun <T> fetchAllPages(baseUrl: String, typeRef: TypeReference<PagedResponse<T>>): List<T> {
        val result = mutableListOf<T>()
        var start = 0
        var isLast = false

        while (!isLast) {
            // Append start parameter; use & if a query string already exists, else ?.
            val separator = if (baseUrl.contains('?')) '&' else '?'
            val url = "$baseUrl${separator}start=$start"
            val raw = get(url)
            val page: PagedResponse<T> = mapper.readValue(raw, typeRef)
            result.addAll(page.values)
            isLast = page.isLastPage
            start = page.nextPageStart ?: 0
        }

        return result
    }

    // ?? HTTP primitives ???????????????????????????????????????????????????

    /**
     * Executes a GET request and returns the raw response body as a String.
     *
     * @throws BitbucketApiException if the server returns a non-2xx status code
     */
    private fun get(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $bearerToken")
            .header("Accept", "application/json")
            .GET()
            .build()
        return executeAndCheck(request)
    }

    /**
     * Executes a POST request with a JSON body and returns the raw response body.
     *
     * @throws BitbucketApiException if the server returns a non-2xx status code
     */
    private fun post(url: String, jsonBody: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $bearerToken")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()
        return executeAndCheck(request)
    }

    /**
     * Executes a PUT request with a JSON body and returns the raw response body.
     *
     * @throws BitbucketApiException if the server returns a non-2xx status code
     */
    private fun put(url: String, jsonBody: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $bearerToken")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()
        return executeAndCheck(request)
    }

    /**
     * Executes a DELETE request.
     *
     * @throws BitbucketApiException if the server returns a non-2xx status code
     */
    private fun delete(url: String) {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $bearerToken")
            .DELETE()
            .build()
        executeAndCheck(request)
    }

    /**
     * Sends [request] via the shared [HttpClient], checks the response status code and
     * returns the response body on success.
     *
     * On HTTP ? 400 the method parses the Bitbucket error JSON to extract the
     * `errors[].message` strings and wraps them in a [BitbucketApiException].
     */
    private fun executeAndCheck(request: HttpRequest): String {
        val response: HttpResponse<String> =
            http.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() in 200..299) {
            return response.body()
        }

        // Parse structured error body: { "errors": [{ "message": "..." }] }
        val messages = try {
            val tree = mapper.readTree(response.body())
            tree["errors"]?.map { it["message"]?.asText() ?: "Unknown error" }
                ?: listOf("HTTP ${response.statusCode()}")
        } catch (_: Exception) {
            listOf("HTTP ${response.statusCode()}: ${response.body().take(200)}")
        }

        throw BitbucketApiException(response.statusCode(), messages)
    }
}

