package com.example.domain.util

import com.example.domain.model.ExportedLink
import com.example.domain.model.LinksExportEnvelope
import kotlinx.serialization.json.Json

object TransferUtils {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    prettyPrint = true
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
