package com.example

import com.example.data.network.RedirectApiClient
import com.example.domain.model.DailyClickPoint
import com.example.domain.model.ExportedLink
import com.example.domain.model.ImportConflictMode
import com.example.domain.model.ImportLinkInput
import com.example.domain.model.ImportLinksResponse
import com.example.domain.model.ImportRowError
import com.example.domain.model.ImportRowResult
import com.example.domain.model.ImportSummary
import com.example.domain.model.LinkPatch
import com.example.domain.model.ServerConfig
import com.example.domain.util.CsvAndTimestampParser
import com.example.domain.util.CsvParseResult
import com.example.domain.util.DateUtils
import com.example.domain.util.DynamicImporter
import com.example.domain.util.FieldMappingEntry
import com.example.domain.util.FieldMappingMode
import com.example.domain.util.ImportTargetField
import com.example.domain.util.ShareParser
import com.example.domain.util.StatsUtils
import com.example.domain.util.TransferUtils
import com.example.domain.util.ValidationUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun validation_normalizesServerUrlAndRejectsLoop() {
    assertEquals("https://go.example.com", ValidationUtils.normalizeServerBaseUrl("go.example.com/"))
    assertNull(ValidationUtils.validateServerBaseUrl("https://go.example.com"))
    val loopErr = ValidationUtils.validateTargetUrl(
      "https://go.example.com/test",
      "https://go.example.com",
    )
    assertNotNull(loopErr)
    assertNull(
      ValidationUtils.validateTargetUrl("https://docs.example.org/path", "https://go.example.com"),
    )
  }

  @Test
  fun validation_rejectsUnsafeControlCharsAndSpacesInTargetUrl() {
    assertNotNull(ValidationUtils.validateTargetUrl("https://example.com/has space"))
    assertNotNull(ValidationUtils.validateTargetUrl("https://example.com/\tbad"))
    assertNotNull(ValidationUtils.validateTargetUrl("https://example.com/\u2028sep"))
    assertNull(ValidationUtils.validateTargetUrl("https://example.com/has%20space"))
  }

  @Test
  fun validation_enforcesReservedSlugsAndTags() {
    assertNotNull(ValidationUtils.validateSlug("api"))
    assertNotNull(ValidationUtils.validateSlug("APP"))
    assertNull(ValidationUtils.validateSlug("My_Slug-1"))
    val generated = ValidationUtils.generateValidRandomSlug()
    assertEquals(6, generated.length)
    assertNull(ValidationUtils.validateSlug(generated))

    val tags = ValidationUtils.normalizeTags(
      listOf("#Launch", "launch", "  Promo ", "", "a".repeat(33), "ok"),
    )
    assertEquals(listOf("launch", "promo", "ok"), tags)
  }

  @Test
  fun shareParser_extractsUrlAndTitleAndStripsTrailingPunctuation() {
    val payload = ShareParser.extractSharedPayload(
      text = "Check out this post: https://example.com/article?id=42).",
      subject = "Great Read",
    )
    assertEquals("https://example.com/article?id=42", payload.url)
    assertEquals("Check out this post", payload.title)
  }

  @Test
  fun statsUtils_zeroFillsUtcRangeAndFormatsDimensions() {
    val filled = StatsUtils.zeroFillDailySeries(
      fromDay = "2026-01-01",
      toDay = "2026-01-03",
      daily = listOf(DailyClickPoint("2026-01-02", 7)),
    )
    assertEquals(3, filled.size)
    assertEquals(0, filled[0].clicks)
    assertEquals(7, filled[1].clicks)
    assertEquals(0, filled[2].clicks)

    assertEquals("Unknown", StatsUtils.formatCountryDisplay("XX"))
    assertFalse(StatsUtils.formatCountryDisplay("XX").contains("\uD83C"))
    assertEquals("Direct / none", StatsUtils.formatReferrerDisplay("(direct)"))
    assertEquals("Direct / none", StatsUtils.formatReferrerDisplay(""))
  }

  @Test
  fun linkPatch_serializesOnlyChangedFieldsAndPreservesExplicitNull() {
    val patch = LinkPatch(
      title = null,
      hasTitle = true,
      expiresAt = null,
      hasExpiresAt = true,
      isActive = false,
      hasIsActive = true,
    )
    val obj = patch.toJsonObject()
    assertEquals(3, obj.size)
    assertTrue(obj["title"] is JsonNull)
    assertTrue(obj["expiresAt"] is JsonNull)
    assertEquals("false", obj["isActive"]?.jsonPrimitive?.content)
    assertFalse("slug" in obj)
  }

  @Test
  fun fastPath_parsesExportEnvelopeWithOptionalSlug() {
    val raw = """
      {
        "version": 1,
        "exportedAt": "2026-01-01T00:00:00.000Z",
        "count": 2,
        "links": [
          { "slug": "docs", "targetUrl": "https://example.com/docs", "statusCode": 307 },
          { "targetUrl": "https://example.com/auto", "createdAt": "2026-01-02T00:00:00.000Z" }
        ]
      }
    """.trimIndent()
    val parsed = DynamicImporter.parseNativeV1FastPath(Json.parseToJsonElement(raw))
    assertNotNull(parsed)
    assertEquals(2, parsed!!.validItems.size)
    assertEquals(0, parsed.clientErrors.size)
    assertEquals("docs", parsed.validItems[0].link.slug)
    assertNull(parsed.validItems[1].link.slug)
    assertEquals("2026-01-02T00:00:00.000Z", parsed.validItems[1].link.createdAt)
  }

  @Test
  fun dateUtils_parsesAndChecksExpiry() {
    val past = "2020-01-01T00:00:00.000Z"
    assertTrue(DateUtils.isLinkExpired(past, nowMillis = 1_700_000_000_000L))
    val future = "2099-01-01T00:00:00.000Z"
    assertFalse(DateUtils.isLinkExpired(future, nowMillis = 1_700_000_000_000L))
  }

  @Test
  fun csvParser_parsesBomDelimitedQuotedMultilineAndHeaderlessRows() {
    val csv = "\uFEFFurl;short;note;tags\n" +
      "\"https://example.com/a\";\"alpha\";\"line1\nline2\";\"one,two\"\n" +
      "\"https://example.com/b\";\"beta\";\"said \"\"hi\"\"\";\"three\""
    val res = CsvAndTimestampParser.parseCsvContent(csv)
    assertTrue(res is CsvParseResult.Success)
    val success = res as CsvParseResult.Success
    assertEquals(';', success.delimiter)
    assertEquals(listOf("url", "short", "note", "tags"), success.headers)
    assertEquals(2, success.rows.size)
    assertEquals("line1\nline2", success.rows[0]["note"])
    assertEquals("said \"hi\"", success.rows[1]["note"])

    val singleRow = "https://example.com/only,onlyslug"
    assertTrue(CsvAndTimestampParser.parseCsvContent(singleRow, hasHeader = true) is CsvParseResult.Error)
    val headerless = CsvAndTimestampParser.parseCsvContent(singleRow, hasHeader = false) as CsvParseResult.Success
    assertEquals(listOf("Column 1", "Column 2"), headerless.headers)
    assertEquals("https://example.com/only", headerless.rows[0]["Column 1"])
  }

  @Test
  fun timestampParser_handlesMultiFormatAndRejectsInvalidCalendarDates() {
    assertEquals(
      "2026-02-28T00:00:00.000Z",
      CsvAndTimestampParser.parseImportTimestamp("2026-02-28").getOrNull(),
    )
    assertEquals(
      "2026-02-28T00:00:00.000Z",
      CsvAndTimestampParser.parseImportTimestamp("20260228").getOrNull(),
    )
    assertEquals(
      "2026-02-28T12:30:45.123Z",
      CsvAndTimestampParser.parseImportTimestamp("2026-02-28 12:30:45.123").getOrNull(),
    )
    assertEquals(
      "2026-02-28T06:30:00.000Z",
      CsvAndTimestampParser.parseImportTimestamp("2026-02-28T12:00:00+05:30").getOrNull(),
    )
    // Epoch seconds -> 1700000000 -> 2023-11-14T22:13:20.000Z
    assertEquals(
      "2023-11-14T22:13:20.000Z",
      CsvAndTimestampParser.parseImportTimestamp(1_700_000_000L).getOrNull(),
    )
    // Non-leap Feb 29 must fail
    assertTrue(CsvAndTimestampParser.parseImportTimestamp("2025-02-29").isFailure)
    assertTrue(CsvAndTimestampParser.parseImportTimestamp("20260230").isFailure)
  }

  @Test
  fun dynamicImporter_discoversArraysMapsFieldsTransformsAndMergesReport() {
    val rawJson = """
      {
        "meta": { "source": "bitly" },
        "data": {
          "records": [
            { "long_url": "https://example.com/1", "alias": "one", "tags": "docs,api", "enabled": "yes" },
            { "long_url": "not-a-url", "alias": "bad", "tags": "", "enabled": "no" },
            { "long_url": "https://example.com/2", "alias": "", "tags": ["launch"], "enabled": "false" }
          ]
        }
      }
    """.trimIndent()
    val root = Json.parseToJsonElement(rawJson)
    assertNull(DynamicImporter.parseNativeV1FastPath(root))
    val candidates = DynamicImporter.discoverJsonArrayPaths(root)
    assertEquals(1, candidates.size)
    assertEquals("data.records", candidates[0].path)

    val fields = DynamicImporter.discoverSourceFields(candidates[0].items)
    val baseMapping = DynamicImporter.autoDetectFieldMapping(fields) + mapOf(
      ImportTargetField.TARGET_URL to FieldMappingEntry(FieldMappingMode.FIELD, sourceField = "long_url"),
      ImportTargetField.SLUG to FieldMappingEntry(FieldMappingMode.FIELD, sourceField = "alias"),
      ImportTargetField.STATUS_CODE to FieldMappingEntry(FieldMappingMode.CONSTANT, constantValue = "301"),
      ImportTargetField.IS_ACTIVE to FieldMappingEntry(FieldMappingMode.FIELD, sourceField = "enabled"),
    )

    val transformed = DynamicImporter.transformMappedRows(
      rows = candidates[0].items,
      mapping = baseMapping,
      serverBaseUrl = "https://go.example.com",
    )
    assertEquals(2, transformed.validItems.size)
    assertEquals(0, transformed.validItems[0].originalIndex)
    assertEquals(2, transformed.validItems[1].originalIndex)
    assertNull(transformed.validItems[1].link.slug)
    assertEquals(301, transformed.validItems[0].link.statusCode)
    assertEquals(1, transformed.clientErrors.size)
    assertEquals(1, transformed.clientErrors[0].index)

    val serverReport = ImportLinksResponse(
      version = 1,
      onConflict = ImportConflictMode.SKIP.apiValue,
      summary = ImportSummary(total = 2, created = 1, updated = 0, skipped = 0, failed = 1),
      errors = listOf(ImportRowError(index = 1, slug = null, code = "conflict", message = "Server error")),
      results = listOf(
        ImportRowResult(index = 0, slug = "one", status = "created", id = "id-1"),
        ImportRowResult(index = 1, slug = null, status = "failed", reason = "conflict"),
      ),
    )

    val merged = DynamicImporter.mergeClientAndServerImportReport(
      serverReport = serverReport,
      validIndexMap = transformed.validItems.map { it.originalIndex },
      clientErrors = transformed.clientErrors,
      clientFailedResults = transformed.clientFailedResults,
      totalRows = 3,
    )
    assertEquals(3, merged.summary.total)
    assertEquals(1, merged.summary.created)
    assertEquals(2, merged.summary.failed)
    assertEquals(listOf(1, 2), merged.errors.map { it.index })
    assertEquals(listOf(0, 1, 2), merged.results.map { it.index })
  }

  @Test
  fun transferUtils_buildExportJsonAlwaysEncodesVersionOne() {
    val exported = TransferUtils.buildExportJson(
      links = listOf(ExportedLink(slug = "a", targetUrl = "https://example.com/a")),
      exportedAt = "2026-01-01T00:00:00.000Z",
    )
    val root = Json.parseToJsonElement(exported).jsonObject
    assertEquals(1, root["version"]?.jsonPrimitive?.int)
    assertEquals(1, root["count"]?.jsonPrimitive?.int)
  }

  @Test
  fun importLinksChunked_501Items_chunksInto200_200_101WithVersion1() = runBlocking {
    val capturedChunkSizes = mutableListOf<Int>()
    val fakeClient = OkHttpClient.Builder()
      .addInterceptor { chain ->
        val req = chain.request()
        val buf = Buffer()
        req.body!!.writeTo(buf)
        val bodyJson = Json.parseToJsonElement(buf.readUtf8()).jsonObject
        assertEquals(1, bodyJson["version"]?.jsonPrimitive?.int)
        assertEquals("skip", bodyJson["onConflict"]?.jsonPrimitive?.content)
        val linksArr = bodyJson["links"]!!.jsonArray
        capturedChunkSizes.add(linksArr.size)

        val resJson = """
          {
            "version": 1,
            "onConflict": "skip",
            "summary": { "total": ${linksArr.size}, "created": ${linksArr.size}, "updated": 0, "skipped": 0, "failed": 0 },
            "skippedSlugs": [],
            "createdSlugs": [],
            "updatedSlugs": [],
            "errors": [],
            "results": []
          }
        """.trimIndent()
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(resJson.toResponseBody("application/json".toMediaType()))
          .build()
      }
      .build()

    val api = RedirectApiClient(fakeClient)
    val items = (1..501).map { i ->
      ImportLinkInput(slug = "s-$i", targetUrl = "https://example.com/p/$i")
    }
    val report = api.importLinksChunked(
      config = ServerConfig(baseUrl = "https://go.example.com", apiToken = "tok"),
      links = items,
      onConflict = ImportConflictMode.SKIP,
    )

    assertEquals(listOf(200, 200, 101), capturedChunkSizes)
    assertEquals(501, report.summary.total)
    assertEquals(501, report.summary.created)
  }

  @Test
  fun fastPath_normalizesEmptyOptionalFieldsAndIsolatesInvalidAndDuplicateRows() {
    val raw = """
      {
        "version": 1,
        "links": [
          {
            "slug": "ok",
            "targetUrl": "https://example.com/1",
            "title": "   ",
            "expiresAt": "",
            "createdAt": "2026-01-01T02:00:00+02:00"
          },
          {
            "slug": "loop",
            "targetUrl": "https://go.example.com/other"
          },
          {
            "slug": "ok",
            "targetUrl": "https://example.com/duplicate-slug"
          }
        ]
      }
    """.trimIndent()
    val root = Json.parseToJsonElement(raw)
    val parsed = DynamicImporter.parseNativeV1FastPath(root, "https://go.example.com")
    assertNotNull(parsed)
    assertEquals(1, parsed!!.validItems.size)
    assertEquals(2, parsed.clientErrors.size)
    assertNull(parsed.validItems[0].link.title)
    assertNull(parsed.validItems[0].link.expiresAt)
    assertEquals("2026-01-01T00:00:00.000Z", parsed.validItems[0].link.createdAt)
    assertEquals("invalid_target_url", parsed.clientErrors[0].code)
    assertEquals(1, parsed.clientErrors[0].index)
    assertEquals("duplicate_in_batch", parsed.clientErrors[1].code)
    assertEquals(2, parsed.clientErrors[1].index)
  }
}
