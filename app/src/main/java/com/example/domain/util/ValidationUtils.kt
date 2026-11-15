package com.example.domain.util

import java.net.URI
import java.security.SecureRandom

object ValidationUtils {
  val RESERVED_SLUGS = setOf("api", "app")
  val SLUG_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")
  val REDIRECT_STATUS_CODES = listOf(307, 302, 308, 301)
  const val DEFAULT_STATUS_CODE = 307
  const val MAX_URL_LENGTH = 2048
  const val MAX_TAGS = 10
  const val MAX_TAG_LENGTH = 32
  const val MAX_TITLE_LENGTH = 200
  const val DEFAULT_SLUG_LENGTH = 6
  const val PAGE_SIZE = 20
  const val EXPORT_DEFAULT_LIMIT = 500
  const val IMPORT_CHUNK_SIZE = 200
  const val KV_PROPAGATION_GRACE_MS = 60_000L
  const val MIN_TOKEN_EXPIRY_LEAD_MS = 120_000L
  private const val BASE62_ALPHABET =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

  private val random = SecureRandom()

  fun normalizeServerBaseUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    val withScheme = if (
      trimmed.startsWith("http://", ignoreCase = true) ||
      trimmed.startsWith("https://", ignoreCase = true)
    ) {
      trimmed
    } else {
      "https://$trimmed"
    }
    return withScheme.trimEnd('/')
  }

  fun validateServerBaseUrl(raw: String): String? {
    val normalized = normalizeServerBaseUrl(raw)
    if (normalized.isEmpty()) return "Server URL is required."
    val uri = try {
      URI(normalized)
    } catch (_: Exception) {
      return "Enter a valid http:// or https:// server URL."
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
      return "Server URL must use http:// or https://."
    }
    if (uri.host.isNullOrBlank()) {
      return "Server URL must include a valid hostname."
    }
    return null
  }

  private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
  private val UNSAFE_URL_CHARS_REGEX = Regex("[\\u0000-\\u0020\\u007F-\\u009F\\u2028\\u2029]")

  fun normalizeTargetUrlInput(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val hasScheme = SCHEME_REGEX.containsMatchIn(trimmed)
    return if (hasScheme) trimmed else "https://$trimmed"
  }

  fun validateTargetUrl(
    rawUrl: String,
    serverBaseUrl: String? = null,
    precomputedServerHost: String? = null,
  ): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return "Destination URL is required."
    if (trimmed.length > MAX_URL_LENGTH) {
      return "Destination URL must be $MAX_URL_LENGTH characters or fewer."
    }
    if (UNSAFE_URL_CHARS_REGEX.containsMatchIn(trimmed)) {
      return "Destination URL must not contain control characters or unencoded spaces."
    }
    val uri = try {
      URI(trimmed)
    } catch (_: Exception) {
      return "Enter a valid URL starting with https:// or http://."
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
      return "URL must start with https:// or http://."
    }
    val targetHost = uri.host?.lowercase()?.trimEnd('.')
    if (targetHost.isNullOrBlank()) {
      return "URL must include a valid hostname."
    }
    val serverHost = precomputedServerHost
      ?: serverBaseUrl?.takeIf { it.isNotBlank() }?.let(::extractHostname)
    if (serverHost != null && serverHost == targetHost) {
      return "Destination URL cannot point to this Redirect server ($serverHost)."
    }
    return null
  }

  fun extractHostname(url: String): String? {
    return try {
      URI(url.trim()).host?.lowercase()?.trimEnd('.')
    } catch (_: Exception) {
      null
    }
  }

  fun validateSlug(rawSlug: String): String? {
    val slug = rawSlug.trim()
    if (slug.isEmpty()) return "Slug cannot be empty."
    if (!SLUG_REGEX.matches(slug)) {
      return "Use 1–64 letters, numbers, hyphens, or underscores."
    }
    if (slug.lowercase() in RESERVED_SLUGS) {
      return "\"$slug\" is a reserved system path."
    }
    return null
  }

  fun generateValidRandomSlug(length: Int = DEFAULT_SLUG_LENGTH): String {
    while (true) {
      val sb = StringBuilder(length)
      repeat(length) {
        sb.append(BASE62_ALPHABET[random.nextInt(BASE62_ALPHABET.length)])
      }
      val candidate = sb.toString()
      if (candidate.lowercase() !in RESERVED_SLUGS) {
        return candidate
      }
    }
  }

  fun normalizeTag(raw: String): String {
    return raw.trim().removePrefix("#").trim().lowercase()
  }

  fun normalizeTags(tags: List<String>): List<String> {
    return tags
      .map(::normalizeTag)
      .filter { it.isNotEmpty() && it.length <= MAX_TAG_LENGTH }
      .distinct()
      .take(MAX_TAGS)
  }

  fun buildShortUrl(serverBaseUrl: String, slug: String): String {
    val base = normalizeServerBaseUrl(serverBaseUrl)
    return if (base.isEmpty()) "/$slug" else "$base/$slug"
  }
}
