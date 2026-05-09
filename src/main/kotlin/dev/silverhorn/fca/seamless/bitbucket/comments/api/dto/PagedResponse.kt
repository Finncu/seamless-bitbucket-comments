package dev.silverhorn.fca.seamless.bitbucket.comments.api.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Generic paging wrapper returned by every list endpoint of the
 * Bitbucket Server REST API v1.0.
 *
 * @param T the element type contained in [values]
 * @property size    number of elements in the current page
 * @property limit   maximum page size as negotiated with the server
 * @property isLastPage true when no further pages exist
 * @property nextPageStart start-index to use as `?start=` for the next request; null when [isLastPage] is true
 * @property values  the actual payload of this page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PagedResponse<T>(
    val size: Int = 0,
    val limit: Int = 25,
    val isLastPage: Boolean = true,
    val nextPageStart: Int? = null,
    val values: List<T> = emptyList()
)

