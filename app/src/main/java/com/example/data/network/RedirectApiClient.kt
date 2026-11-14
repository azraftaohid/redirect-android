package com.example.data.network

import com.example.domain.model.ApiErrorEnvelope
import com.example.domain.model.CreateLinkRequest
import com.example.domain.model.CreateTokenRequest
import com.example.domain.model.CreateTokenResponse
import com.example.domain.model.DeleteResult
import com.example.domain.model.ExportedLink
import com.example.domain.model.ImportConflictMode
import com.example.domain.model.ImportLinkInput
import com.example.domain.model.ImportLinksRequest
import com.example.domain.model.ImportLinksResponse
import com.example.domain.model.ImportRowError
import com.example.domain.model.ImportRowResult
import com.example.domain.model.ImportSummary
import com.example.domain.model.LinkPatch
import com.example.domain.model.LinkRecord
import com.example.domain.model.LinkSortOption
import com.example.domain.model.LinkStatsResponse
import com.example.domain.model.LinksExportEnvelope
import com.example.domain.model.PaginatedLinks
import com.example.domain.model.Principal
import com.example.domain.model.RotateInitialTokenResponse
import com.example.domain.model.ServerConfig
import com.example.domain.model.StatusFilter
import com.example.domain.model.TokenInfo
import com.example.domain.model.TokensListResponse
import com.example.domain.model.VerifyAuthResponse
import com.example.domain.util.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class RedirectApiException(
  val status: Int,
  val code: String,
  override val message: String,
) : Exception(message)

class AccessSessionExpiredException(
  val status: Int,
  override val message: String =
    "Cloudflare Access intercepted the API response. Check your Zero Trust Service Token headers or reconnect.",
) : Exception(message)

class ImportChunkException(
  override val message: String,
  val partialResponse: ImportLinksResponse,
  val causeException: Throwable? = null,
) : Exception(message, causeException)

class RedirectApiClient(
  private val client: OkHttpClient = defaultOkHttpClient(),
) {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  suspend fun bootstrapInitialTokenPing(
    baseUrl: String,
    cfAccessClientId: String = "",
    cfAccessClientSecret: String = "",
  ): Unit = withContext(Dispatchers.IO) {
    val config = ServerConfig(
      baseUrl = ValidationUtils.normalizeServerBaseUrl(baseUrl),
      cfAccessClientId = cfAccessClientId.trim(),
      cfAccessClientSecret = cfAccessClientSecret.trim(),
    )
    val url = buildUrl(config.baseUrl, "/api/v1/auth/verify")
    val req = Request.Builder()
      .url(url)
      .get()
      .header("Accept", "application/json")
      .applyZeroTrust(config)
      .build()

    client.newCall(req).execute().use { response ->
      if (response.code in 300..399) {
        throw AccessSessionExpiredException(response.code)
      }
      val contentType = response.header("Content-Type").orEmpty()
      if (response.code < 500 && contentType.contains("text/html", ignoreCase = true)) {
        throw AccessSessionExpiredException(response.code)
      }
      // 401 is expected when bootstrapping unauthenticated initial_token
    }
  }

  suspend fun verifyAuth(config: ServerConfig): Principal {
    val res = executeJson(
      config = config,
      method = "GET",
      path = "/api/v1/auth/verify",
      deserializer = VerifyAuthResponse.serializer(),
    )
    return res.principal
  }

  suspend fun listLinks(
    config: ServerConfig,
    search: String = "",
    tag: String? = null,
    slug: String? = null,
    targetUrl: String? = null,
    statusFilter: StatusFilter = StatusFilter.ALL,
    sortOption: LinkSortOption = LinkSortOption.NEWEST,
    limit: Int = ValidationUtils.PAGE_SIZE,
    offset: Int = 0,
  ): PaginatedLinks {
    val params = linkedMapOf<String, String>()
    if (search.isNotBlank()) params["search"] = search.trim()
    if (!tag.isNullOrBlank()) params["tag"] = tag.trim()
    if (!slug.isNullOrBlank()) params["slug"] = slug.trim()
    if (!targetUrl.isNullOrBlank()) params["targetUrl"] = targetUrl.trim()
    statusFilter.queryValue?.let { params["isActive"] = it.toString() }
    params["sortBy"] = sortOption.sortBy
    params["sortOrder"] = sortOption.sortOrder
    params["limit"] = limit.toString()
    params["offset"] = offset.toString()

    return executeJson(
      config = config,
      method = "GET",
      path = "/api/v1/links",
      queryParams = params,
      deserializer = PaginatedLinks.serializer(),
    )
  }

  suspend fun createLink(config: ServerConfig, request: CreateLinkRequest): LinkRecord {
    val bodyStr = json.encodeToString(CreateLinkRequest.serializer(), request)
    return executeJson(
      config = config,
      method = "POST",
      path = "/api/v1/links",
      body = bodyStr.toRequestBody(jsonMediaType),
      deserializer = LinkRecord.serializer(),
    )
  }

  suspend fun getLink(config: ServerConfig, id: String): LinkRecord {
    return executeJson(
      config = config,
      method = "GET",
      path = "/api/v1/links/$id",
      deserializer = LinkRecord.serializer(),
    )
  }

  suspend fun patchLink(config: ServerConfig, id: String, patch: LinkPatch): LinkRecord {
    val bodyStr = patch.toJsonObject().toString()
    return executeJson(
      config = config,
      method = "PATCH",
      path = "/api/v1/links/$id",
      body = bodyStr.toRequestBody(jsonMediaType),
      deserializer = LinkRecord.serializer(),
    )
  }

  suspend fun deleteLink(config: ServerConfig, id: String): DeleteResult {
    return executeJson(
      config = config,
      method = "DELETE",
      path = "/api/v1/links/$id",
      deserializer = DeleteResult.serializer(),
    )
  }

  suspend fun getLinkStats(config: ServerConfig, id: String, days: Int): LinkStatsResponse {
    return executeJson(
      config = config,
      method = "GET",
      path = "/api/v1/links/$id/stats",
      queryParams = mapOf("days" to days.toString()),
      deserializer = LinkStatsResponse.serializer(),
    )
  }

  suspend fun listTokens(config: ServerConfig): List<TokenInfo> {
    return executeJson(
      config = config,
      method = "GET",
      path = "/api/v1/tokens",
      deserializer = TokensListResponse.serializer(),
    ).data
  }

  suspend fun createToken(config: ServerConfig, request: CreateTokenRequest): CreateTokenResponse {
    val bodyStr = json.encodeToString(CreateTokenRequest.serializer(), request)
    return executeJson(
      config = config,
      method = "POST",
      path = "/api/v1/tokens",
      body = bodyStr.toRequestBody(jsonMediaType),
      deserializer = CreateTokenResponse.serializer(),
    )
  }

  suspend fun revokeToken(config: ServerConfig, id: String): DeleteResult {
    return executeJson(
      config = config,
      method = "DELETE",
      path = "/api/v1/tokens/$id",
      deserializer = DeleteResult.serializer(),
    )
  }

  suspend fun rotateInitialToken(config: ServerConfig): RotateInitialTokenResponse {
    return executeJson(
      config = config,
      method = "POST",
      path = "/api/v1/tokens/initial/rotate",
      body = ByteArray(0).toRequestBody(null),
      deserializer = RotateInitialTokenResponse.serializer(),
    )
  }

  suspend fun exportAllLinks(
    config: ServerConfig,
    onPageFetched: (Int) -> Unit = {},
  ): Pair<List<ExportedLink>, String> {
    val all = mutableListOf<ExportedLink>()
    var cursor: String? = null
    var exportedAt = ""
    val maxPages = 200
    for (page in 0 until maxPages) {
      val params = linkedMapOf("limit" to ValidationUtils.EXPORT_DEFAULT_LIMIT.toString())
      if (cursor != null) params["cursor"] = cursor
      val envelope = executeJson(
        config = config,
        method = "GET",
        path = "/api/v1/links/export",
        queryParams = params,
        deserializer = LinksExportEnvelope.serializer(),
      )
      if (exportedAt.isEmpty()) exportedAt = envelope.exportedAt
      all.addAll(envelope.links)
      onPageFetched(all.size)
      val next = envelope.pagination?.nextCursor
      if (envelope.pagination?.hasMore != true || next.isNullOrBlank()) break
      cursor = next
    }
    return all to exportedAt
  }

  suspend fun importLinksChunked(
    config: ServerConfig,
    links: List<ImportLinkInput>,
    onConflict: ImportConflictMode,
    onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
  ): ImportLinksResponse {
    val chunkSize = ValidationUtils.IMPORT_CHUNK_SIZE
    var created = 0
    var updated = 0
    var skipped = 0
    var failed = 0
    val skippedSlugs = mutableListOf<String>()
    val createdSlugs = mutableListOf<String>()
    val updatedSlugs = mutableListOf<String>()
    val errors = mutableListOf<ImportRowError>()
    val results = mutableListOf<ImportRowResult>()

    var offset = 0
    while (offset < links.size) {
      val end = minOf(offset + chunkSize, links.size)
      val slice = links.subList(offset, end)
      try {
        val reqBody = json.encodeToString(
          ImportLinksRequest.serializer(),
          ImportLinksRequest(version = 1, onConflict = onConflict.apiValue, links = slice),
        )
        val res = executeJson(
          config = config,
          method = "POST",
          path = "/api/v1/links/import",
          body = reqBody.toRequestBody(jsonMediaType),
          deserializer = ImportLinksResponse.serializer(),
        )
        created += res.summary.created
        updated += res.summary.updated
        skipped += res.summary.skipped
        failed += res.summary.failed
        skippedSlugs.addAll(res.skippedSlugs)
        createdSlugs.addAll(res.createdSlugs)
        updatedSlugs.addAll(res.updatedSlugs)
        errors.addAll(res.errors.map { it.copy(index = it.index + offset) })
        results.addAll(res.results.map { it.copy(index = it.index + offset) })
        offset = end
        onProgress(offset, links.size)
      } catch (e: Exception) {
        val partial = ImportLinksResponse(
          version = 1,
          onConflict = onConflict.apiValue,
          summary = ImportSummary(
            total = offset,
            created = created,
            updated = updated,
            skipped = skipped,
            failed = failed,
          ),
          skippedSlugs = skippedSlugs,
          createdSlugs = createdSlugs,
          updatedSlugs = updatedSlugs,
          errors = errors,
          results = results,
        )
        throw ImportChunkException(
          message = e.message ?: "Import failed at row ${offset + 1}.",
          partialResponse = partial,
          causeException = e,
        )
      }
    }

    return ImportLinksResponse(
      version = 1,
      onConflict = onConflict.apiValue,
      summary = ImportSummary(
        total = links.size,
        created = created,
        updated = updated,
        skipped = skipped,
        failed = failed,
      ),
      skippedSlugs = skippedSlugs,
      createdSlugs = createdSlugs,
      updatedSlugs = updatedSlugs,
      errors = errors,
      results = results,
    )
  }

  private suspend fun <T> executeJson(
    config: ServerConfig,
    method: String,
    path: String,
    queryParams: Map<String, String> = emptyMap(),
    body: RequestBody? = null,
    deserializer: DeserializationStrategy<T>,
  ): T = withContext(Dispatchers.IO) {
    val url = buildUrl(config.baseUrl, path, queryParams)
    val builder = Request.Builder()
      .url(url)
      .header("Accept", "application/json")
      .applyZeroTrust(config)

    if (config.apiToken.isNotBlank()) {
      builder.header("Authorization", "Bearer ${config.apiToken.trim()}")
    }

    when (method) {
      "GET" -> builder.get()
      "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
      "PATCH" -> builder.patch(body ?: ByteArray(0).toRequestBody(null))
      "DELETE" -> builder.delete(body)
      else -> builder.method(method, body)
    }

    client.newCall(builder.build()).execute().use { response ->
      if (response.code in 300..399) {
        throw AccessSessionExpiredException(response.code)
      }
      val contentType = response.header("Content-Type").orEmpty()
      val rawBody = response.body?.string().orEmpty()
      if (response.code < 500 && contentType.contains("text/html", ignoreCase = true)) {
        throw AccessSessionExpiredException(response.code)
      }
      if (!response.isSuccessful) {
        val parsedError = try {
          json.decodeFromString(ApiErrorEnvelope.serializer(), rawBody).error
        } catch (_: Exception) {
          null
        }
        throw RedirectApiException(
          status = response.code,
          code = parsedError?.code ?: "http_${response.code}",
          message = parsedError?.message ?: "Request failed with HTTP ${response.code}",
        )
      }
      try {
        json.decodeFromString(deserializer, rawBody)
      } catch (e: Exception) {
        throw IOException("Unexpected response format from server.", e)
      }
    }
  }

  private fun buildUrl(
    baseUrl: String,
    path: String,
    queryParams: Map<String, String> = emptyMap(),
  ): String {
    val normalized = ValidationUtils.normalizeServerBaseUrl(baseUrl)
    val parsed = "$normalized$path".toHttpUrlOrNull()
      ?: throw IllegalArgumentException("Invalid server URL: $baseUrl")
    val builder = parsed.newBuilder()
    queryParams.forEach { (k, v) -> builder.addQueryParameter(k, v) }
    return builder.build().toString()
  }

  private fun Request.Builder.applyZeroTrust(config: ServerConfig): Request.Builder {
    if (config.cfAccessClientId.isNotBlank()) {
      header("CF-Access-Client-Id", config.cfAccessClientId.trim())
    }
    if (config.cfAccessClientSecret.isNotBlank()) {
      header("CF-Access-Client-Secret", config.cfAccessClientSecret.trim())
    }
    return this
  }

  companion object {
    fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .build()
  }
}
