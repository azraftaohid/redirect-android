package com.example.domain.util

import com.example.domain.model.ExportedLink
import com.example.domain.model.ImportLinkInput
import com.example.domain.model.LinksExportEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object TransferUtils {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    prettyPrint = true
  }

  fun parseImportLinks(rawJson: String): List<ImportLinkInput> {
    val trimmed = rawJson.trim()
    if (trimmed.isEmpty()) throw IllegalArgumentException("Selected file is empty.")
    val element = json.parseToJsonElement(trimmed)
    val linksArray: JsonArray = when (element) {
      is JsonObject -> {
        val version = element["version"]?.jsonPrimitive?.intOrNull
        if (version != null && version != 1) {
          throw IllegalArgumentException("Unsupported export version: $version (expected version 1).")
        }
        element["links"]?.jsonArray
          ?: throw IllegalArgumentException("JSON object is missing a \"links\" array.")
      }
      is JsonArray -> element
      else -> throw IllegalArgumentException("Expected a Redirect JSON export object or array.")
    }

    if (linksArray.isEmpty()) {
      throw IllegalArgumentException("No links found in the selected JSON file.")
    }

    return linksArray.mapIndexed { idx, item ->
      try {
        val parsed = json.decodeFromJsonElement(ImportLinkInput.serializer(), item)
        if (parsed.slug.isBlank() || parsed.targetUrl.isBlank()) {
          throw IllegalArgumentException("Row ${idx + 1} is missing slug or targetUrl.")
        }
        parsed
      } catch (e: IllegalArgumentException) {
        throw e
      } catch (e: Exception) {
        throw IllegalArgumentException("Invalid link entry at index $idx: ${e.message}")
      }
    }
  }

  fun buildExportJson(links: List<ExportedLink>, exportedAt: String): String {
    val envelope = LinksExportEnvelope(
      version = 1,
      exportedAt = exportedAt,
      count = links.size,
      links = links,
      pagination = null,
    )
    return json.encodeToString(LinksExportEnvelope.serializer(), envelope)
  }
}
