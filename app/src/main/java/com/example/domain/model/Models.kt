package com.example.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class ServerConfig(
  val baseUrl: String = "",
  val apiToken: String = "",
  val tokenSetAtMillis: Long = 0L,
  val cfAccessClientId: String = "",
  val cfAccessClientSecret: String = "",
) {
  val hasZeroTrust: Boolean
    get() = cfAccessClientId.isNotBlank() && cfAccessClientSecret.isNotBlank()
}

@Serializable
data class Principal(
  val kind: String, // "env" | "token" | "initial"
  val id: String,
  val name: String,
)

@Serializable
data class VerifyAuthResponse(
  val principal: Principal,
)

@Serializable
data class LinkRecord(
  val id: String,
  val slug: String,
  val targetUrl: String,
  val title: String? = null,
  val statusCode: Int = 307,
  val tags: List<String> = emptyList(),
  val isActive: Boolean = true,
  val forwardQuery: Boolean = true,
  val expiresAt: String? = null,
  val clickCount: Int = 0,
  val lastClickedAt: String? = null,
  val createdAt: String,
  val updatedAt: String,
)

@Serializable
data class PaginationInfo(
  val total: Int,
  val limit: Int,
  val offset: Int,
  val hasMore: Boolean,
)

@Serializable
data class PaginatedLinks(
  val data: List<LinkRecord>,
  val pagination: PaginationInfo,
)

@Serializable
data class CreateLinkRequest(
  val targetUrl: String,
  val slug: String? = null,
  val title: String? = null,
  val statusCode: Int? = null,
  val tags: List<String>? = null,
  val isActive: Boolean? = null,
  val forwardQuery: Boolean? = null,
  val expiresAt: String? = null,
)

data class LinkPatch(
  val slug: String? = null,
  val hasSlug: Boolean = false,
  val targetUrl: String? = null,
  val hasTargetUrl: Boolean = false,
  val title: String? = null,
  val hasTitle: Boolean = false,
  val statusCode: Int? = null,
  val hasStatusCode: Boolean = false,
  val tags: List<String>? = null,
  val hasTags: Boolean = false,
  val isActive: Boolean? = null,
  val hasIsActive: Boolean = false,
  val forwardQuery: Boolean? = null,
  val hasForwardQuery: Boolean = false,
  val expiresAt: String? = null,
  val hasExpiresAt: Boolean = false,
) {
  val isEmpty: Boolean
    get() = !hasSlug && !hasTargetUrl && !hasTitle && !hasStatusCode &&
      !hasTags && !hasIsActive && !hasForwardQuery && !hasExpiresAt

  fun toJsonObject(): JsonObject = buildJsonObject {
    if (hasSlug && slug != null) put("slug", JsonPrimitive(slug))
    if (hasTargetUrl && targetUrl != null) put("targetUrl", JsonPrimitive(targetUrl))
    if (hasTitle) {
      put("title", if (title == null) JsonNull else JsonPrimitive(title))
    }
    if (hasStatusCode && statusCode != null) put("statusCode", JsonPrimitive(statusCode))
    if (hasTags && tags != null) {
      put(
        "tags",
        kotlinx.serialization.json.JsonArray(tags.map { JsonPrimitive(it) }),
      )
    }
    if (hasIsActive && isActive != null) put("isActive", JsonPrimitive(isActive))
    if (hasForwardQuery && forwardQuery != null) put("forwardQuery", JsonPrimitive(forwardQuery))
    if (hasExpiresAt) {
      put("expiresAt", if (expiresAt == null) JsonNull else JsonPrimitive(expiresAt))
    }
  }
}

@Serializable
data class DeleteResult(
  val deleted: Boolean,
  val id: String,
)

@Serializable
data class DailyClickPoint(
  val day: String,
  val clicks: Int,
)

@Serializable
data class DimensionRow(
  val value: String,
  val clicks: Int,
)

@Serializable
data class StatsDimensions(
  val country: List<DimensionRow>? = null,
  val referrer: List<DimensionRow>? = null,
)

@Serializable
data class StatsRange(
  val days: Int,
  val from: String,
  val to: String,
)

@Serializable
data class LinkStatsResponse(
  val link: LinkRecord,
  val range: StatsRange,
  val daily: List<DailyClickPoint>,
  val dimensions: StatsDimensions = StatsDimensions(),
)

@Serializable
data class TokenInfo(
  val id: String,
  val name: String,
  val createdAt: String,
  val expiresAt: String? = null,
  val preview: String,
  val isInitial: Boolean? = null,
)

@Serializable
data class TokensListResponse(
  val data: List<TokenInfo>,
)

@Serializable
data class CreateTokenRequest(
  val name: String,
  val expiresAt: String? = null,
)

@Serializable
data class CreateTokenResponse(
  val id: String,
  val name: String,
  val createdAt: String,
  val expiresAt: String? = null,
  val preview: String,
  val isInitial: Boolean? = null,
  val token: String,
)

@Serializable
data class RotateInitialTokenResponse(
  val token: String,
  val id: String,
  val name: String,
  val isInitial: Boolean = true,
  val preview: String,
)

@Serializable
data class ExportedLink(
  val slug: String,
  val targetUrl: String,
  val title: String? = null,
  val statusCode: Int = 307,
  val tags: List<String> = emptyList(),
  val isActive: Boolean = true,
  val forwardQuery: Boolean = true,
  val expiresAt: String? = null,
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

@Serializable
data class ExportPagination(
  val limit: Int,
  val nextCursor: String? = null,
  val hasMore: Boolean = false,
)

@Serializable
data class LinksExportEnvelope(
  val version: Int = 1,
  val exportedAt: String = "",
  val count: Int = 0,
  val links: List<ExportedLink>,
  val pagination: ExportPagination? = null,
)

@Serializable
data class ImportLinkInput(
  val slug: String,
  val targetUrl: String,
  val title: String? = null,
  val statusCode: Int? = null,
  val tags: List<String>? = null,
  val isActive: Boolean? = null,
  val forwardQuery: Boolean? = null,
  val expiresAt: String? = null,
)

@Serializable
data class ImportLinksRequest(
  val version: Int = 1,
  val onConflict: String,
  val links: List<ImportLinkInput>,
)

@Serializable
data class ImportSummary(
  val total: Int = 0,
  val created: Int = 0,
  val updated: Int = 0,
  val skipped: Int = 0,
  val failed: Int = 0,
)

@Serializable
data class ImportRowError(
  val index: Int,
  val slug: String,
  val code: String,
  val message: String,
)

@Serializable
data class ImportRowResult(
  val index: Int,
  val slug: String,
  val status: String, // "created" | "updated" | "skipped" | "failed"
  val id: String? = null,
  val reason: String? = null, // "slug_exists" | "duplicate_in_batch" | error code
)

@Serializable
data class ImportLinksResponse(
  val version: Int = 1,
  val onConflict: String = "skip",
  val summary: ImportSummary = ImportSummary(),
  val skippedSlugs: List<String> = emptyList(),
  val createdSlugs: List<String> = emptyList(),
  val updatedSlugs: List<String> = emptyList(),
  val errors: List<ImportRowError> = emptyList(),
  val results: List<ImportRowResult> = emptyList(),
)

@Serializable
data class ApiErrorDetail(
  val code: String,
  val message: String,
  val details: JsonElement? = null,
)

@Serializable
data class ApiErrorEnvelope(
  val error: ApiErrorDetail,
)

enum class StatusFilter(val queryValue: Boolean?) {
  ALL(null),
  ACTIVE_ONLY(true),
  INACTIVE_ONLY(false),
}

enum class LinkSortOption(
  val sortBy: String,
  val sortOrder: String,
) {
  NEWEST("createdAt", "desc"),
  OLDEST("createdAt", "asc"),
  RECENTLY_UPDATED("updatedAt", "desc"),
  MOST_CLICKS("clickCount", "desc"),
  SLUG_ASC("slug", "asc"),
}

enum class ImportConflictMode(val apiValue: String) {
  SKIP("skip"),
  OVERWRITE("overwrite"),
}
