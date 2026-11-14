package com.example.domain.util

import com.example.domain.model.DailyClickPoint
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object StatsUtils {
  fun zeroFillDailySeries(
    fromDay: String,
    toDay: String,
    daily: List<DailyClickPoint>,
  ): List<DailyClickPoint> {
    val startMillis = DateUtils.parseUtcDay(fromDay) ?: return daily
    val endMillis = DateUtils.parseUtcDay(toDay) ?: return daily
    if (startMillis > endMillis) return daily

    val byDay = daily.associateBy({ it.day }, { it.clicks })
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      timeInMillis = startMillis
    }
    val out = mutableListOf<DailyClickPoint>()
    var guard = 0
    while (cal.timeInMillis <= endMillis && guard < 400) {
      val day = DateUtils.formatUtcDay(cal.timeInMillis)
      out.add(DailyClickPoint(day = day, clicks = byDay[day] ?: 0))
      cal.add(Calendar.DAY_OF_MONTH, 1)
      guard++
    }
    return out
  }

  fun formatCountryDisplay(rawCode: String): String {
    val code = rawCode.trim().uppercase(Locale.US)
    if (code.isEmpty() || code == "XX") return "Unknown"
    if (code.length == 2 && code[0] in 'A'..'Z' && code[1] in 'A'..'Z') {
      val name = Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }
      val flag = countryCodeToFlagEmoji(code)
      return "$flag $name"
    }
    return rawCode
  }

  private fun countryCodeToFlagEmoji(code: String): String {
    val first = Character.codePointAt(code, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(code, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
  }

  fun formatReferrerDisplay(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed == "(direct)" || trimmed.equals("direct", ignoreCase = true)) {
      return "Direct / none"
    }
    return trimmed
  }
}
