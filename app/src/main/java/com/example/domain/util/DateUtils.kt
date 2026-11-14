package com.example.domain.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
  private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

  private val isoMillisFormat: SimpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
      timeZone = utcTimeZone
      isLenient = false
    }

  private val isoSecondsFormat: SimpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
      timeZone = utcTimeZone
      isLenient = false
    }

  private val isoOffsetFormat: SimpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
      isLenient = false
    }

  private val localDisplayFormat: SimpleDateFormat
    get() = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())

  private val localDateOnlyFormat: SimpleDateFormat
    get() = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

  private val utcDayFormat: SimpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
      timeZone = utcTimeZone
      isLenient = false
    }

  fun parseIsoUtc(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    val trimmed = iso.trim()
    val candidates = listOf(isoMillisFormat, isoSecondsFormat, isoOffsetFormat)
    for (fmt in candidates) {
      try {
        return fmt.parse(trimmed)?.time
      } catch (_: Exception) {
        // Try next format
      }
    }
    return null
  }

  fun formatIsoUtc(epochMillis: Long): String {
    return isoMillisFormat.format(Date(epochMillis))
  }

  fun formatUtcDay(epochMillis: Long): String {
    return utcDayFormat.format(Date(epochMillis))
  }

  fun parseUtcDay(day: String): Long? {
    return try {
      utcDayFormat.parse(day.trim())?.time
    } catch (_: Exception) {
      null
    }
  }

  fun formatLocalDateTime(iso: String?): String {
    val millis = parseIsoUtc(iso) ?: return ""
    return localDisplayFormat.format(Date(millis))
  }

  fun formatLocalDateTime(epochMillis: Long): String {
    return localDisplayFormat.format(Date(epochMillis))
  }

  fun formatLocalDate(iso: String?): String {
    val millis = parseIsoUtc(iso) ?: return ""
    return localDateOnlyFormat.format(Date(millis))
  }

  fun isLinkExpired(expiresAt: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val expMillis = parseIsoUtc(expiresAt) ?: return false
    return expMillis <= nowMillis
  }
}
