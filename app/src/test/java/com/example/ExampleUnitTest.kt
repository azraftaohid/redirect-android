package com.example

import com.example.domain.model.DailyClickPoint
import com.example.domain.model.LinkPatch
import com.example.domain.util.DateUtils
import com.example.domain.util.ShareParser
import com.example.domain.util.StatsUtils
import com.example.domain.util.TransferUtils
import com.example.domain.util.ValidationUtils
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
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
  fun transferUtils_parsesExportEnvelope() {
    val json = """
      {
        "version": 1,
        "exportedAt": "2026-01-01T00:00:00.000Z",
        "count": 1,
        "links": [
          { "slug": "docs", "targetUrl": "https://example.com/docs", "statusCode": 307 }
        ]
      }
    """.trimIndent()
    val parsed = TransferUtils.parseImportLinks(json)
    assertEquals(1, parsed.size)
    assertEquals("docs", parsed[0].slug)
  }

  @Test
  fun dateUtils_parsesAndChecksExpiry() {
    val past = "2020-01-01T00:00:00.000Z"
    assertTrue(DateUtils.isLinkExpired(past, nowMillis = 1_700_000_000_000L))
    val future = "2099-01-01T00:00:00.000Z"
    assertFalse(DateUtils.isLinkExpired(future, nowMillis = 1_700_000_000_000L))
  }
}
