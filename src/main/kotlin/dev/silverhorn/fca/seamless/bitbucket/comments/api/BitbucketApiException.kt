package dev.silverhorn.fca.seamless.bitbucket.comments.api

/**
 * Thrown when the Bitbucket Server REST API returns an HTTP error response.
 *
 * The server returns a structured JSON body for validation errors:
 * ```json
 * { "errors": [{ "context": "...", "message": "..." }] }
 * ```
 * [messages] contains the extracted `message` strings from this array so
 * callers can surface them in the IDE notification system without parsing JSON again.
 *
 * @property statusCode      HTTP status code returned by the server
 * @property messages        human-readable error messages extracted from the response body
 */
class BitbucketApiException(
    val statusCode: Int,
    val messages: List<String>
) : RuntimeException(
    "Bitbucket API error [$statusCode]: ${messages.joinToString("; ")}"
)

