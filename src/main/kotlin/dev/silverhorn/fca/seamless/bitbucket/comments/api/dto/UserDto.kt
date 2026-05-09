package dev.silverhorn.fca.seamless.bitbucket.comments.api.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Minimal representation of the authenticated user as returned by
 * `GET /plugins/servlet/applinks/whoami`.
 *
 * @property slug        login identifier; used for mention pattern matching
 * @property displayName human-readable full name
 * @property emailAddress primary e-mail address
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserDto(
    val slug: String = "",
    val displayName: String = "",
    val emailAddress: String = ""
)

/**
 * Wrapper returned by the whoami endpoint.
 * The actual user data is nested under the `user` key.
 *
 * @property user the authenticated user's profile
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WhoAmIDto(
    val user: UserDto = UserDto()
)

