package com.example.domain.util

import com.example.domain.model.ImportLinkInput
import com.example.domain.model.ImportLinksResponse
import com.example.domain.model.ImportRowError
import com.example.domain.model.ImportRowResult
import com.example.domain.model.ImportSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

enum class ImportTargetField(
  val key: String,
  val label: String,
  val required: Boolean,
  val allowConstant: Boolean,
  val description: String,
) {
  TARGET_URL("targetUrl", "Destination URL", true, true, "Absolute http:// or https:// target URL."),
  SLUG("slug", "Slug", false, false, "Short path segment. Omit to auto-generate."),
  TITLE("title", "Title", false, true, "Optional display label (up to 200 chars)."),
  STATUS_CODE("statusCode", "Status Code", false, true, "HTTP redirect status: 301, 302, 307, or 308."),
  TAGS("tags", "Tags", false, true, "Array or comma/semicolon/pipe-separated tags (max 10)."),
  IS_ACTIVE("isActive", "Active", false, true, "Boolean flag (true/false, 1/0, yes/no). Defaults to true."),
  FORWARD_QUERY("forwardQuery", "Forward Query", false, true, "Forward incoming query params. Defaults to true."),
  EXPIRES_AT("expiresAt", "Expires At", false, true, "Date string (ISO/SQL) or epoch timestamp (s/ms)."),
  CREATED_AT("createdAt", "Created At", false, true, "Creation date or epoch timestamp (applied on insert)."),
  UPDATED_AT("updatedAt", "Updated At", false, true, "Update date or epoch timestamp (applied on insert)."),
}

enum class FieldMappingMode { OMIT, FIELD, CONSTANT }

data class FieldMappingEntry(
  val mode: FieldMappingMode,
  val sourceField: String = "",
  val constantValue: String = "",
)

data class JsonArrayCandidate(
  val path: String,
  val label: String,
  val items: List<JsonElement>,
)

data class ValidTransformedImportItem(
  val originalIndex: Int,
  val link: ImportLinkInput,
)

data class TransformMappedRowsResult(
  val validItems: List<ValidTransformedImportItem>,
  val clientErrors: List<ImportRowError>,
  val clientFailedResults: List<ImportRowResult>,
  val previewRows: List<ValidTransformedImportItem>,
) {
  val totalRows: Int
    get() = validItems.size + clientErrors.size
}

object DynamicImporter {
  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
  private val WHITESPACE_REGEX = Regex("\\s+")
  private val NON_ALPHANUM_FIELD_REGEX = Regex("[^a-z0-9$]")
  private val TAG_SPLIT_REGEX = Regex("[,;|]+")

  private val FIELD_ALIAS_MAP: Map<ImportTargetField, List<String>> = mapOf(
    ImportTargetField.TARGET_URL to listOf(
      "targeturl", "url", "longurl", "destination", "destinationurl",
      "target", "href", "link", "originalurl", "\$value",
    ),
    ImportTargetField.SLUG to listOf(
      "slug", "short", "shortcode", "alias", "code", "keyword", "backhalf", "customslug",
    ),
    ImportTargetField.TITLE to listOf("title", "name", "label", "description"),
    ImportTargetField.STATUS_CODE to listOf("statuscode", "httpstatus", "redirecttype", "codehttp"),
    ImportTargetField.TAGS to listOf("tags", "tag", "labels", "categories"),
    ImportTargetField.IS_ACTIVE to listOf("isactive", "active", "enabled"),
    ImportTargetField.FORWARD_QUERY to listOf("forwardquery", "passquery", "preservequery", "forwardparams"),
    ImportTargetField.EXPIRES_AT to listOf("expiresat", "expires", "expiry", "expiration", "expirationdate", "expireat"),
    ImportTargetField.CREATED_AT to listOf("createdat", "created", "datecreated", "createddate", "timestamp"),
    ImportTargetField.UPDATED_AT to listOf("updatedat", "updated", "modified", "modifiedat", "lastmodified"),
  )

  fun defaultConstantValue(field: ImportTargetField): String = when (field) {
    ImportTargetField.STATUS_CODE -> "307"
    ImportTargetField.IS_ACTIVE, ImportTargetField.FORWARD_QUERY -> "true"
    else -> ""
  }

  /**
   * Detects a native Redirect v1 export (`{ "version": 1, "links": [...] }`) and validates each
   * row individually. Unlike the previous all-or-nothing check, individual malformed rows or
   * duplicate slugs are isolated into [TransformMappedRowsResult.clientErrors] while keeping
   * the file on the fast-path.
   */
  fun parseNativeV1FastPath(
    root: JsonElement,
    serverBaseUrl: String? = null,
    previewLimit: Int = 3,
  ): TransformMappedRowsResult? {
    if (root !is JsonObject) return null
    val version = root["version"]?.let { (it as? JsonPrimitive)?.intOrNull }
    if (version != 1) return null
    val linksArr = root["links"] as? JsonArray ?: return null
    if (linksArr.isEmpty()) return null
    if (linksArr.any { it !is JsonObject }) return null

    val serverHost = serverBaseUrl?.takeIf { it.isNotBlank() }?.let(ValidationUtils::extractHostname)
    val validItems = mutableListOf<ValidTransformedImportItem>()
    val clientErrors = mutableListOf<ImportRowError>()
    val clientFailedResults = mutableListOf<ImportRowResult>()
    val seenSlugs = HashMap<String, Int>()

    for (index in linksArr.indices) {
      val item = linksArr[index] as JsonObject
      val rawSlugPreview = (item["slug"] as? JsonPrimitive)?.contentOrNull
        ?.trim()
        ?.trimStart('/')
        .orEmpty()

      val decoded = try {
        json.decodeFromJsonElement(ImportLinkInput.serializer(), item)
      } catch (e: Exception) {
        recordRowFailure(
          index = index,
          slug = rawSlugPreview,
          code = "validation_error",
          message = "Malformed v1 link object: ${e.message ?: "invalid field type"}",
          clientErrors = clientErrors,
          clientFailedResults = clientFailedResults,
        )
        continue
      }

      val candidateUrl = decoded.targetUrl.trim()
      val urlErr = ValidationUtils.validateTargetUrl(
        rawUrl = candidateUrl,
        serverBaseUrl = serverBaseUrl,
        precomputedServerHost = serverHost,
      )
      if (urlErr != null) {
        val code = if (urlErr.contains("cannot point to", ignoreCase = true)) {
          "invalid_target_url"
        } else {
          "validation_error"
        }
        recordRowFailure(
          index = index,
          slug = rawSlugPreview,
          code = code,
          message = "targetUrl: $urlErr",
          clientErrors = clientErrors,
          clientFailedResults = clientFailedResults,
        )
        continue
      }

      val cleanSlug = decoded.slug?.trim()?.trimStart('/')?.ifEmpty { null }
      if (cleanSlug != null) {
        val slugErr = ValidationUtils.validateSlug(cleanSlug)
        if (slugErr != null) {
          recordRowFailure(
            index = index,
            slug = cleanSlug,
            code = "validation_error",
            message = "slug: $slugErr",
            clientErrors = clientErrors,
            clientFailedResults = clientFailedResults,
          )
          continue
        }
        val prevRow = seenSlugs[cleanSlug]
        if (prevRow != null) {
          recordRowFailure(
            index = index,
            slug = cleanSlug,
            code = "duplicate_in_batch",
            message = "slug: Duplicate slug \"$cleanSlug\" in file (already used on row #${prevRow + 1}).",
            clientErrors = clientErrors,
            clientFailedResults = clientFailedResults,
          )
          continue
        }
        seenSlugs[cleanSlug] = index
      }

      val cleanTitle = decoded.title?.trim()?.ifEmpty { null }
      if (cleanTitle != null && cleanTitle.length > ValidationUtils.MAX_TITLE_LENGTH) {
        recordRowFailure(
          index = index,
          slug = cleanSlug.orEmpty(),
          code = "validation_error",
          message = "title: Title must be at most ${ValidationUtils.MAX_TITLE_LENGTH} characters.",
          clientErrors = clientErrors,
          clientFailedResults = clientFailedResults,
        )
        continue
      }

      if (decoded.statusCode != null && decoded.statusCode !in ValidationUtils.REDIRECT_STATUS_CODES) {
        recordRowFailure(
          index = index,
          slug = cleanSlug.orEmpty(),
          code = "validation_error",
          message = "statusCode: Must be 301, 302, 307, or 308 (got ${decoded.statusCode}).",
          clientErrors = clientErrors,
          clientFailedResults = clientFailedResults,
        )
        continue
      }

      var tagsError: String? = null
      val cleanTags = decoded.tags?.let { rawTags ->
        val normalized = rawTags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        when {
          normalized.size > ValidationUtils.MAX_TAGS -> {
            tagsError = "tags: Cannot exceed ${ValidationUtils.MAX_TAGS} tags."
            null
          }
          normalized.any { it.length > ValidationUtils.MAX_TAG_LENGTH } -> {
            tagsError = "tags: Tag must be at most ${ValidationUtils.MAX_TAG_LENGTH} characters."
            null
          }
          else -> normalized
        }
      }
      if (tagsError != null) {
        recordRowFailure(
          index = index,
          slug = cleanSlug.orEmpty(),
          code = "validation_error",
          message = tagsError!!,
          clientErrors = clientErrors,
          clientFailedResults = clientFailedResults,
        )
        continue
      }

      val expRes = parseOptionalV1Timestamp(decoded.expiresAt, "expiresAt")
      val createdRes = parseOptionalV1Timestamp(decoded.createdAt, "createdAt")
      val updatedRes = parseOptionalV1Timestamp(decoded.updatedAt, "updatedAt")
      val tsError = expRes.exceptionOrNull()?.message
        ?: createdRes.exceptionOrNull()?.message
        ?: updatedRes.exceptionOrNull()?.message
      if (tsError != null) {
        recordRowFailure(
          index = index,
          slug = cleanSlug.orEmpty(),
          code = "validation_error",
          message = tsError,
          clientErrors = clientErrors,
          clientFailedResults = clientFailedResults,
        )
        continue
      }

      validItems.add(
        ValidTransformedImportItem(
          originalIndex = index,
          link = decoded.copy(
            targetUrl = candidateUrl,
            slug = cleanSlug,
            title = cleanTitle,
            tags = cleanTags,
            expiresAt = expRes.getOrNull(),
            createdAt = createdRes.getOrNull(),
            updatedAt = updatedRes.getOrNull(),
          ),
        ),
      )
    }

    return TransformMappedRowsResult(
      validItems = validItems,
      clientErrors = clientErrors,
      clientFailedResults = clientFailedResults,
      previewRows = validItems.take(previewLimit),
    )
  }

  private fun parseOptionalV1Timestamp(raw: String?, fieldName: String): Result<String?> {
    if (raw == null || raw.isBlank()) return Result.success(null)
    return CsvAndTimestampParser.parseImportTimestamp(raw).fold(
      onSuccess = { Result.success(it) },
      onFailure = { Result.failure(IllegalArgumentException("$fieldName: ${it.message}")) },
    )
  }

  private fun recordRowFailure(
    index: Int,
    slug: String,
    code: String,
    message: String,
    clientErrors: MutableList<ImportRowError>,
    clientFailedResults: MutableList<ImportRowResult>,
  ) {
    clientErrors.add(
      ImportRowError(
        index = index,
        slug = slug,
        code = code,
        message = message,
      ),
    )
    clientFailedResults.add(
      ImportRowResult(
        index = index,
        slug = slug,
        status = "failed",
        reason = message,
      ),
    )
  }

  fun discoverJsonArrayPaths(root: JsonElement, maxDepth: Int = 4): List<JsonArrayCandidate> {
    if (root is JsonArray) {
      val count = root.size
      return listOf(
        JsonArrayCandidate(
          path = "",
          label = "(Root Array) ($count item${if (count == 1) "" else "s"})",
          items = root.toList(),
        ),
      )
    }
    if (root !is JsonObject) return emptyList()

    data class RankedCandidate(
      val candidate: JsonArrayCandidate,
      val depth: Int,
      val order: Int,
    )

    val found = mutableListOf<RankedCandidate>()
    val queue = ArrayDeque<Triple<JsonObject, String, Int>>()
    queue.add(Triple(root, "", 0))
    var order = 0

    while (queue.isNotEmpty()) {
      val (node, prefix, depth) = queue.removeFirst()
      for ((key, value) in node.entries) {
        val path = if (prefix.isEmpty()) key else "$prefix.$key"
        when {
          value is JsonArray -> {
            val count = value.size
            found.add(
              RankedCandidate(
                candidate = JsonArrayCandidate(
                  path = path,
                  label = "$path ($count item${if (count == 1) "" else "s"})",
                  items = value.toList(),
                ),
                depth = depth + 1,
                order = order++,
              ),
            )
          }
          value is JsonObject && depth + 1 < maxDepth -> {
            queue.add(Triple(value, path, depth + 1))
          }
        }
      }
    }

    return found.sortedWith(
      compareBy<RankedCandidate>(
        { if (it.candidate.items.isEmpty()) 1 else 0 },
        { if (it.candidate.path == "links") 0 else 1 },
        { it.depth },
        { it.order },
      ),
    ).map { it.candidate }
  }

  fun csvRowsToJsonElements(rows: List<Map<String, String>>): List<JsonElement> {
    return rows.map { row ->
      JsonObject(row.mapValues { (_, v) -> JsonPrimitive(v) })
    }
  }

  fun discoverSourceFields(items: List<JsonElement>, sampleLimit: Int = 100): List<String> {
    val fields = linkedSetOf<String>()
    val limit = minOf(items.size, sampleLimit)
    for (i in 0 until limit) {
      val item = items[i]
      if (item is JsonNull) continue
      if (item is JsonObject) {
        collectObjectFieldPaths(item, "", 1, 3, fields)
      } else if (item is JsonPrimitive) {
        fields.add("\$value")
      }
    }
    return fields.toList()
  }

  private fun collectObjectFieldPaths(
    obj: JsonObject,
    prefix: String,
    depth: Int,
    maxDepth: Int,
    out: MutableSet<String>,
  ) {
    for ((key, value) in obj.entries) {
      val path = if (prefix.isEmpty()) key else "$prefix.$key"
      if (value is JsonObject && depth < maxDepth && value.isNotEmpty()) {
        collectObjectFieldPaths(value, path, depth + 1, maxDepth, out)
      } else {
        out.add(path)
      }
    }
  }

  fun getFieldByPath(item: JsonElement, path: String): JsonElement? {
    if (path.isEmpty()) return null
    if (path == "\$value") return item
    if (item !is JsonObject) return null
    item[path]?.let { return it }

    var cursor: JsonElement = item
    for (segment in path.split(".")) {
      val obj = cursor as? JsonObject ?: return null
      cursor = obj[segment] ?: return null
    }
    return cursor
  }

  fun formatSamplePreview(element: JsonElement?): String {
    if (element == null || element is JsonNull) return ""
    val raw = when (element) {
      is JsonPrimitive -> element.contentOrNull.orEmpty()
      else -> element.toString()
    }
    val compact = raw.replace(WHITESPACE_REGEX, " ").trim()
    if (compact.isEmpty()) return ""
    return if (compact.length > 28) "${compact.take(28)}..." else compact
  }

  private fun normalizeFieldName(name: String): String {
    val leaf = if ('.' in name) name.substringAfterLast('.') else name
    return leaf.lowercase().replace(NON_ALPHANUM_FIELD_REGEX, "")
  }

  fun autoDetectFieldMapping(sourceFields: List<String>): Map<ImportTargetField, FieldMappingEntry> {
    data class NormalizedField(val field: String, val norm: String, val isNested: Boolean)

    val pairs = sourceFields.map { NormalizedField(it, normalizeFieldName(it), '.' in it) }
    val mapping = linkedMapOf<ImportTargetField, FieldMappingEntry>()
    val usedFields = mutableSetOf<String>()
    val unmatched = mutableListOf<ImportTargetField>()

    for (descriptor in ImportTargetField.entries) {
      val aliases = FIELD_ALIAS_MAP[descriptor].orEmpty()
      var matched: String? = null
      for (alias in aliases) {
        val hit = pairs.firstOrNull { !it.isNested && it.field !in usedFields && it.norm == alias }
          ?: pairs.firstOrNull { it.isNested && it.field !in usedFields && it.norm == alias }
        if (hit != null) {
          matched = hit.field
          break
        }
      }
      if (matched != null) {
        usedFields.add(matched)
        mapping[descriptor] = FieldMappingEntry(mode = FieldMappingMode.FIELD, sourceField = matched)
      } else {
        unmatched.add(descriptor)
      }
    }

    for (descriptor in unmatched) {
      if (descriptor.required) {
        val fallback = sourceFields.firstOrNull { it !in usedFields } ?: sourceFields.firstOrNull().orEmpty()
        if (fallback.isNotEmpty()) usedFields.add(fallback)
        mapping[descriptor] = FieldMappingEntry(mode = FieldMappingMode.FIELD, sourceField = fallback)
      } else {
        mapping[descriptor] = FieldMappingEntry(mode = FieldMappingMode.OMIT)
      }
    }
    return mapping
  }

  private fun resolveRawValue(
    row: JsonElement,
    entry: FieldMappingEntry?,
  ): JsonElement? {
    if (entry == null || entry.mode == FieldMappingMode.OMIT) return null
    if (entry.mode == FieldMappingMode.CONSTANT) return JsonPrimitive(entry.constantValue)
    if (entry.sourceField.isEmpty()) return null
    return getFieldByPath(row, entry.sourceField)
  }

  private fun jsonElementToPrimitiveAny(el: JsonElement?): Any? {
    if (el == null || el is JsonNull) return null
    if (el !is JsonPrimitive) return el
    if (el.isString) return el.content
    el.booleanOrNull?.let { return it }
    el.longOrNull?.let { return it }
    el.doubleOrNull?.let { return it }
    return el.contentOrNull
  }

  private fun resolveMappedTimestamp(
    row: JsonElement,
    mapping: Map<ImportTargetField, FieldMappingEntry>,
    field: ImportTargetField,
  ): Result<String?> {
    val entry = mapping[field]
    if (entry == null || entry.mode == FieldMappingMode.OMIT) return Result.success(null)
    val rawTs = jsonElementToPrimitiveAny(resolveRawValue(row, entry)) ?: return Result.success(null)
    if (rawTs is String && rawTs.isBlank()) return Result.success(null)
    return CsvAndTimestampParser.parseImportTimestamp(rawTs).fold(
      onSuccess = { Result.success(it) },
      onFailure = { Result.failure(IllegalArgumentException("${field.key}: ${it.message}")) },
    )
  }

  private fun coerceBoolean(el: JsonElement?): Result<Boolean?> {
    if (el == null || el is JsonNull) return Result.success(null)
    if (el !is JsonPrimitive) return Result.failure(IllegalArgumentException("Unsupported boolean value."))
    if (!el.isString) {
      el.booleanOrNull?.let { return Result.success(it) }
      el.longOrNull?.let { num ->
        return when (num) {
          1L -> Result.success(true)
          0L -> Result.success(false)
          else -> Result.failure(IllegalArgumentException("Invalid boolean number \"$num\"."))
        }
      }
    }
    val trimmed = el.content.trim().lowercase()
    if (trimmed.isEmpty()) return Result.success(null)
    return when (trimmed) {
      "true", "1", "yes", "y", "active", "enabled", "on" -> Result.success(true)
      "false", "0", "no", "n", "inactive", "disabled", "off" -> Result.success(false)
      else -> Result.failure(IllegalArgumentException("Invalid boolean value \"${el.content.trim().take(32)}\"."))
    }
  }

  private fun coerceStatusCode(el: JsonElement?): Result<Int?> {
    if (el == null || el is JsonNull) return Result.success(null)
    if (el !is JsonPrimitive) return Result.failure(IllegalArgumentException("Unsupported statusCode value."))
    val num: Int = if (!el.isString) {
      el.intOrNull ?: return Result.failure(IllegalArgumentException("Invalid statusCode \"${el.content}\"."))
    } else {
      val trimmed = el.content.trim()
      if (trimmed.isEmpty()) return Result.success(null)
      trimmed.toIntOrNull() ?: return Result.failure(IllegalArgumentException("Invalid statusCode \"${trimmed.take(16)}\"."))
    }
    if (num !in ValidationUtils.REDIRECT_STATUS_CODES) {
      return Result.failure(IllegalArgumentException("statusCode must be 301, 302, 307, or 308 (got $num)."))
    }
    return Result.success(num)
  }

  private fun coerceTags(el: JsonElement?): Result<List<String>?> {
    if (el == null || el is JsonNull) return Result.success(null)
    val rawStrings = mutableListOf<String>()
    when (el) {
      is JsonArray -> {
        for (item in el) {
          when (item) {
            is JsonNull -> Unit
            is JsonPrimitive -> {
              val s = item.content.trim()
              if (s.isNotEmpty()) rawStrings.add(s)
            }
            is JsonObject -> {
              val named = (item["name"] ?: item["label"] ?: item["tag"] ?: item["value"]) as? JsonPrimitive
              if (named != null && named.isString) {
                val s = named.content.trim()
                if (s.isNotEmpty()) rawStrings.add(s)
              } else {
                return Result.failure(IllegalArgumentException("Invalid tag object in tags array."))
              }
            }
            else -> {
              return Result.failure(IllegalArgumentException("Invalid item in tags array."))
            }
          }
        }
      }
      is JsonPrimitive -> {
        if (!el.isString) return Result.failure(IllegalArgumentException("Unsupported tags value."))
        val trimmed = el.content.trim()
        if (trimmed.isEmpty()) return Result.success(null)
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
          val parsedArr = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() as? JsonArray
          if (parsedArr != null) return coerceTags(parsedArr)
        }
        trimmed.split(TAG_SPLIT_REGEX)
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .forEach { rawStrings.add(it) }
      }
      else -> return Result.failure(IllegalArgumentException("Unsupported tags value."))
    }

    val normalized = rawStrings
      .map { it.trim().lowercase() }
      .filter { it.isNotEmpty() }
      .distinct()
    if (normalized.any { it.length > ValidationUtils.MAX_TAG_LENGTH }) {
      return Result.failure(IllegalArgumentException("Tag must be at most ${ValidationUtils.MAX_TAG_LENGTH} characters."))
    }
    if (normalized.size > ValidationUtils.MAX_TAGS) {
      return Result.failure(IllegalArgumentException("Cannot exceed ${ValidationUtils.MAX_TAGS} tags."))
    }
    return Result.success(normalized)
  }

  fun transformMappedRows(
    rows: List<JsonElement>,
    mapping: Map<ImportTargetField, FieldMappingEntry>,
    serverBaseUrl: String? = null,
    previewLimit: Int = 3,
  ): TransformMappedRowsResult {
    val serverHost = serverBaseUrl?.takeIf { it.isNotBlank() }?.let(ValidationUtils::extractHostname)
    val validItems = mutableListOf<ValidTransformedImportItem>()
    val clientErrors = mutableListOf<ImportRowError>()
    val clientFailedResults = mutableListOf<ImportRowResult>()
    val seenSlugs = HashMap<String, Int>()

    for (index in rows.indices) {
      val row = rows[index]
      var rowErrorCode = "validation_error"
      var rowErrorMessage: String? = null

      val rawSlugEl = resolveRawValue(row, mapping[ImportTargetField.SLUG])
      val cleanedSlugCandidate = if (rawSlugEl is JsonPrimitive && rawSlugEl !is JsonNull) {
        rawSlugEl.content.trim().trimStart('/')
      } else {
        ""
      }

      // 1. targetUrl (required)
      val rawTargetEl = resolveRawValue(row, mapping[ImportTargetField.TARGET_URL])
      var validTargetUrl = ""
      if (rawTargetEl !is JsonPrimitive || !rawTargetEl.isString || rawTargetEl.content.trim().isEmpty()) {
        rowErrorMessage = "targetUrl: Target URL is required."
      } else {
        val candidateUrl = rawTargetEl.content.trim()
        val urlErr = ValidationUtils.validateTargetUrl(
          rawUrl = candidateUrl,
          serverBaseUrl = serverBaseUrl,
          precomputedServerHost = serverHost,
        )
        if (urlErr != null) {
          if (urlErr.contains("cannot point to", ignoreCase = true)) {
            rowErrorCode = "invalid_target_url"
          }
          rowErrorMessage = "targetUrl: $urlErr"
        } else {
          validTargetUrl = candidateUrl
        }
      }

      // 2. slug (optional + batch duplicate detection)
      var validSlug: String? = null
      val slugEntry = mapping[ImportTargetField.SLUG]
      if (rowErrorMessage == null && slugEntry != null && slugEntry.mode != FieldMappingMode.OMIT) {
        if (rawSlugEl != null && rawSlugEl !is JsonNull) {
          if (rawSlugEl !is JsonPrimitive) {
            rowErrorMessage = "slug: Slug must be a string."
          } else if (cleanedSlugCandidate.isNotEmpty()) {
            val slugErr = ValidationUtils.validateSlug(cleanedSlugCandidate)
            if (slugErr != null) {
              rowErrorMessage = "slug: $slugErr"
            } else {
              val prevRowIdx = seenSlugs[cleanedSlugCandidate]
              if (prevRowIdx != null) {
                rowErrorCode = "duplicate_in_batch"
                rowErrorMessage = "slug: Duplicate slug \"$cleanedSlugCandidate\" in file (already used on row #${prevRowIdx + 1})."
              } else {
                seenSlugs[cleanedSlugCandidate] = index
                validSlug = cleanedSlugCandidate
              }
            }
          }
        }
      }

      // 3. title (optional)
      var validTitle: String? = null
      val titleEntry = mapping[ImportTargetField.TITLE]
      if (rowErrorMessage == null && titleEntry != null && titleEntry.mode != FieldMappingMode.OMIT) {
        val rawTitleEl = resolveRawValue(row, titleEntry)
        if (rawTitleEl != null && rawTitleEl !is JsonNull) {
          if (rawTitleEl !is JsonPrimitive) {
            rowErrorMessage = "title: Title must be a string."
          } else {
            val t = rawTitleEl.content.trim()
            if (t.length > ValidationUtils.MAX_TITLE_LENGTH) {
              rowErrorMessage = "title: Title must be at most ${ValidationUtils.MAX_TITLE_LENGTH} characters."
            } else if (t.isNotEmpty()) {
              validTitle = t
            }
          }
        }
      }

      // 4. statusCode (optional)
      var validStatusCode: Int? = null
      val statusEntry = mapping[ImportTargetField.STATUS_CODE]
      if (rowErrorMessage == null && statusEntry != null && statusEntry.mode != FieldMappingMode.OMIT) {
        coerceStatusCode(resolveRawValue(row, statusEntry)).fold(
          onSuccess = { validStatusCode = it },
          onFailure = { rowErrorMessage = "statusCode: ${it.message}" },
        )
      }

      // 5. tags (optional)
      var validTags: List<String>? = null
      val tagsEntry = mapping[ImportTargetField.TAGS]
      if (rowErrorMessage == null && tagsEntry != null && tagsEntry.mode != FieldMappingMode.OMIT) {
        coerceTags(resolveRawValue(row, tagsEntry)).fold(
          onSuccess = { validTags = it },
          onFailure = { rowErrorMessage = "tags: ${it.message}" },
        )
      }

      // 6. isActive & forwardQuery
      var validIsActive: Boolean? = null
      val activeEntry = mapping[ImportTargetField.IS_ACTIVE]
      if (rowErrorMessage == null && activeEntry != null && activeEntry.mode != FieldMappingMode.OMIT) {
        coerceBoolean(resolveRawValue(row, activeEntry)).fold(
          onSuccess = { validIsActive = it },
          onFailure = { rowErrorMessage = "isActive: ${it.message}" },
        )
      }

      var validForwardQuery: Boolean? = null
      val fwdEntry = mapping[ImportTargetField.FORWARD_QUERY]
      if (rowErrorMessage == null && fwdEntry != null && fwdEntry.mode != FieldMappingMode.OMIT) {
        coerceBoolean(resolveRawValue(row, fwdEntry)).fold(
          onSuccess = { validForwardQuery = it },
          onFailure = { rowErrorMessage = "forwardQuery: ${it.message}" },
        )
      }

      // 7. Timestamps (deduplicated via resolveMappedTimestamp)
      var validExpiresAt: String? = null
      if (rowErrorMessage == null) {
        resolveMappedTimestamp(row, mapping, ImportTargetField.EXPIRES_AT).fold(
          onSuccess = { validExpiresAt = it },
          onFailure = { rowErrorMessage = it.message },
        )
      }

      var validCreatedAt: String? = null
      if (rowErrorMessage == null) {
        resolveMappedTimestamp(row, mapping, ImportTargetField.CREATED_AT).fold(
          onSuccess = { validCreatedAt = it },
          onFailure = { rowErrorMessage = it.message },
        )
      }

      var validUpdatedAt: String? = null
      if (rowErrorMessage == null) {
        resolveMappedTimestamp(row, mapping, ImportTargetField.UPDATED_AT).fold(
          onSuccess = { validUpdatedAt = it },
          onFailure = { rowErrorMessage = it.message },
        )
      }

      if (rowErrorMessage == null) {
        validItems.add(
          ValidTransformedImportItem(
            originalIndex = index,
            link = ImportLinkInput(
              slug = validSlug,
              targetUrl = validTargetUrl,
              title = validTitle,
              statusCode = validStatusCode,
              tags = validTags,
              isActive = validIsActive,
              forwardQuery = validForwardQuery,
              expiresAt = validExpiresAt,
              createdAt = validCreatedAt,
              updatedAt = validUpdatedAt,
            ),
          ),
        )
      } else {
        recordRowFailure(
          index = index,
          slug = cleanedSlugCandidate,
          code = rowErrorCode,
          message = rowErrorMessage!!,
          clientErrors = clientErrors,
          clientFailedResults = clientFailedResults,
        )
      }
    }

    return TransformMappedRowsResult(
      validItems = validItems,
      clientErrors = clientErrors,
      clientFailedResults = clientFailedResults,
      previewRows = validItems.take(previewLimit),
    )
  }

  fun mergeClientAndServerImportReport(
    serverReport: ImportLinksResponse,
    validIndexMap: List<Int>,
    clientErrors: List<ImportRowError>,
    clientFailedResults: List<ImportRowResult>,
    totalRows: Int,
  ): ImportLinksResponse {
    val mappedErrors = serverReport.errors.map { err ->
      err.copy(index = validIndexMap.getOrNull(err.index) ?: err.index)
    }
    val mappedResults = serverReport.results.map { res ->
      res.copy(index = validIndexMap.getOrNull(res.index) ?: res.index)
    }
    val errors = (clientErrors + mappedErrors).sortedBy { it.index }
    val results = (clientFailedResults + mappedResults).sortedBy { it.index }
    return serverReport.copy(
      summary = ImportSummary(
        total = totalRows,
        created = serverReport.summary.created,
        updated = serverReport.summary.updated,
        skipped = serverReport.summary.skipped,
        failed = serverReport.summary.failed + clientErrors.size,
      ),
      errors = errors,
      results = results,
    )
  }
}
