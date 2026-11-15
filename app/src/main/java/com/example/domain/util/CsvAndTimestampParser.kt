package com.example.domain.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

enum class CsvDelimiterOption(val charValue: Char?, val label: String) {
  AUTO(null, "Auto-detect"),
  COMMA(',', "Comma (,)"),
  SEMICOLON(';', "Semicolon (;)"),
  TAB('\t', "Tab (\\t)"),
}

sealed interface CsvParseResult {
  data class Success(
    val delimiter: Char,
    val headers: List<String>,
    val rows: List<Map<String, String>>,
  ) : CsvParseResult

  data class Error(val message: String) : CsvParseResult
}

object CsvAndTimestampParser {
  private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")
  private val COMPACT_YYYYMMDD_REGEX =
    Regex("^(19[7-9]\\d|20\\d\\d|21\\d\\d)(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])$")
  private val NUMERIC_STRING_REGEX = Regex("^-?\\d+(\\.\\d+)?$")
  private val SQL_NAIVE_DATETIME_REGEX =
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})[T\\s]+(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d+))?)?$", RegexOption.IGNORE_CASE)
  private val DATE_ONLY_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
  private val ISO_WITH_TZ_REGEX =
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})[T\\s](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d+))?)?(Z|[+-]\\d{2}:?\\d{2})$", RegexOption.IGNORE_CASE)
  private val SLASH_DATETIME_REGEX =
    Regex("^(?:(\\d{4})/(\\d{1,2})/(\\d{1,2})|(\\d{1,2})/(\\d{1,2})/(\\d{4}))(?:\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:\\s*(GMT|UTC|[+-]\\d{2}:?\\d{2}))?)?$", RegexOption.IGNORE_CASE)
  private val MONTH_NAME_REGEX =
    Regex("\\b(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b", RegexOption.IGNORE_CASE)
  private val FOUR_DIGIT_YEAR_REGEX = Regex("\\b(?:19\\d\\d|2\\d{3})\\b")

  fun stripBom(text: String): String =
    if (text.isNotEmpty() && text[0] == '\uFEFF') text.substring(1) else text

  fun detectCsvDelimiter(rawText: String): Char {
    val clean = stripBom(rawText)
    var commas = 0
    var semicolons = 0
    var tabs = 0
    var inQuotes = false
    var seenNonWhitespace = false
    var i = 0
    while (i < clean.length) {
      val ch = clean[i]
      if (ch == '"') {
        if (inQuotes && i + 1 < clean.length && clean[i + 1] == '"') {
          i++
        } else {
          inQuotes = !inQuotes
        }
        seenNonWhitespace = true
        i++
        continue
      }
      if (!inQuotes && (ch == '\n' || ch == '\r')) {
        if (seenNonWhitespace) break
        i++
        continue
      }
      if (ch != ' ') seenNonWhitespace = true
      if (!inQuotes) {
        when (ch) {
          ',' -> commas++
          ';' -> semicolons++
          '\t' -> tabs++
        }
      }
      i++
    }
    return when {
      tabs >= semicolons && tabs > commas -> '\t'
      semicolons > commas && semicolons > 0 -> ';'
      else -> ','
    }
  }

  fun parseCsvContent(
    rawText: String,
    delimiter: Char? = null,
    hasHeader: Boolean = true,
  ): CsvParseResult {
    val clean = stripBom(rawText)
    if (clean.trim().isEmpty()) {
      return CsvParseResult.Error("CSV file is empty.")
    }

    val resolvedDelimiter = delimiter ?: detectCsvDelimiter(clean)
    val matrix = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var cellStarted = false

    var i = 0
    while (i < clean.length) {
      val ch = clean[i]
      if (inQuotes) {
        if (ch == '"') {
          if (i + 1 < clean.length && clean[i + 1] == '"') {
            cell.append('"')
            i++
          } else {
            inQuotes = false
          }
        } else {
          cell.append(ch)
        }
        i++
        continue
      }

      if (ch == '"' && !cellStarted) {
        inQuotes = true
        cellStarted = true
        i++
        continue
      }

      if (ch == resolvedDelimiter) {
        row.add(cell.toString())
        cell.setLength(0)
        cellStarted = false
        i++
        continue
      }

      if (ch == '\r' || ch == '\n') {
        if (ch == '\r' && i + 1 < clean.length && clean[i + 1] == '\n') {
          i++
        }
        row.add(cell.toString())
        matrix.add(row)
        row = mutableListOf()
        cell.setLength(0)
        cellStarted = false
        i++
        continue
      }

      cell.append(ch)
      cellStarted = true
      i++
    }

    if (inQuotes) {
      return CsvParseResult.Error("Invalid CSV: unclosed quoted field.")
    }

    if (cell.isNotEmpty() || cellStarted || row.isNotEmpty()) {
      row.add(cell.toString())
      matrix.add(row)
    }

    val nonBlankRows = matrix.filter { r -> r.any { it.trim().isNotEmpty() } }
    if (nonBlankRows.isEmpty()) {
      return CsvParseResult.Error("CSV file contains no data rows.")
    }

    val colCount = nonBlankRows.maxOfOrNull { it.size } ?: 0
    val headers = mutableListOf<String>()
    val dataStartIndex: Int

    if (hasHeader) {
      if (nonBlankRows.size < 2) {
        return CsvParseResult.Error("CSV file contains a header row but no data rows.")
      }
      val headerRow = nonBlankRows[0]
      val usedHeaders = mutableSetOf<String>()
      for (c in 0 until colCount) {
        val rawName = headerRow.getOrNull(c)?.trim().orEmpty()
        val baseName = rawName.ifEmpty { "Column ${c + 1}" }
        var uniqueName = baseName
        var suffix = 2
        while (uniqueName in usedHeaders) {
          uniqueName = "$baseName (${suffix++})"
        }
        usedHeaders.add(uniqueName)
        headers.add(uniqueName)
      }
      dataStartIndex = 1
    } else {
      for (c in 0 until colCount) {
        headers.add("Column ${c + 1}")
      }
      dataStartIndex = 0
    }

    val rows = mutableListOf<Map<String, String>>()
    for (r in dataStartIndex until nonBlankRows.size) {
      val rawRow = nonBlankRows[r]
      val record = linkedMapOf<String, String>()
      for (c in headers.indices) {
        record[headers[c]] = rawRow.getOrNull(c).orEmpty()
      }
      rows.add(record)
    }

    if (rows.isEmpty()) {
      return CsvParseResult.Error("CSV file contains no data rows.")
    }

    return CsvParseResult.Success(resolvedDelimiter, headers, rows)
  }

  private fun isValidYearRange(epochMillis: Long): Boolean {
    val cal = Calendar.getInstance(utcTimeZone).apply { timeInMillis = epochMillis }
    val year = cal.get(Calendar.YEAR)
    return year in 1900..9999
  }

  private fun buildValidatedUtcMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int = 0,
    minute: Int = 0,
    second: Int = 0,
    ms: Int = 0,
  ): Long? {
    if (year !in 1900..9999 || month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
      return null
    }
    val cal = Calendar.getInstance(utcTimeZone).apply {
      isLenient = false
      clear()
      set(year, month - 1, day, hour, minute, second)
      set(Calendar.MILLISECOND, ms)
    }
    return try {
      val millis = cal.timeInMillis
      if (
        cal.get(Calendar.YEAR) == year &&
        cal.get(Calendar.MONTH) == month - 1 &&
        cal.get(Calendar.DAY_OF_MONTH) == day &&
        cal.get(Calendar.HOUR_OF_DAY) == hour &&
        cal.get(Calendar.MINUTE) == minute &&
        cal.get(Calendar.SECOND) == second
      ) {
        millis
      } else {
        null
      }
    } catch (_: Exception) {
      null
    }
  }

  fun parseImportTimestamp(raw: Any?): Result<String?> {
    if (raw == null) return Result.success(null)
    if (raw is String && raw.trim().isEmpty()) return Result.success(null)

    if (raw is Number || (raw is String && NUMERIC_STRING_REGEX.matches(raw.trim()))) {
      val trimmed = (raw as? String)?.trim().orEmpty()
      val compactCandidate = when (raw) {
        is String -> trimmed
        is Int, is Long -> raw.toString()
        else -> ""
      }
      if (COMPACT_YYYYMMDD_REGEX.matches(compactCandidate)) {
        val y = compactCandidate.substring(0, 4).toInt()
        val m = compactCandidate.substring(4, 6).toInt()
        val d = compactCandidate.substring(6, 8).toInt()
        val millis = buildValidatedUtcMillis(y, m, d)
          ?: return Result.failure(IllegalArgumentException("Invalid calendar date \"$compactCandidate\"."))
        return Result.success(DateUtils.formatIsoUtc(millis))
      }

      val num = when (raw) {
        is Number -> raw.toDouble()
        else -> trimmed.toDoubleOrNull() ?: Double.NaN
      }
      if (!num.isFinite()) {
        return Result.failure(IllegalArgumentException("Invalid numeric timestamp \"${raw.toString().take(64)}\"."))
      }

      val absVal = abs(num)
      val ms = when {
        absVal < 1e11 -> (num * 1000.0).roundToLong()
        absVal < 1e14 -> num.roundToLong()
        absVal < 1e17 -> (num / 1000.0).roundToLong()
        else -> (num / 1_000_000.0).roundToLong()
      }
      if (!isValidYearRange(ms)) {
        return Result.failure(IllegalArgumentException("Epoch timestamp out of range \"${raw.toString().take(64)}\"."))
      }
      return Result.success(DateUtils.formatIsoUtc(ms))
    }

    if (raw !is String) {
      return Result.failure(IllegalArgumentException("Unsupported timestamp value."))
    }

    val trimmed = raw.trim()

    DATE_ONLY_REGEX.matchEntire(trimmed)?.let { m ->
      val millis = buildValidatedUtcMillis(
        m.groupValues[1].toInt(),
        m.groupValues[2].toInt(),
        m.groupValues[3].toInt(),
      ) ?: return Result.failure(IllegalArgumentException("Invalid calendar date \"${trimmed.take(64)}\"."))
      return Result.success(DateUtils.formatIsoUtc(millis))
    }

    SQL_NAIVE_DATETIME_REGEX.matchEntire(trimmed)?.let { m ->
      val fracStr = m.groupValues[7]
      val ms = if (fracStr.isNotEmpty()) fracStr.take(3).padEnd(3, '0').toInt() else 0
      val millis = buildValidatedUtcMillis(
        year = m.groupValues[1].toInt(),
        month = m.groupValues[2].toInt(),
        day = m.groupValues[3].toInt(),
        hour = m.groupValues[4].toInt(),
        minute = m.groupValues[5].toInt(),
        second = m.groupValues[6].ifEmpty { "0" }.toInt(),
        ms = ms,
      ) ?: return Result.failure(IllegalArgumentException("Invalid datetime \"${trimmed.take(64)}\"."))
      return Result.success(DateUtils.formatIsoUtc(millis))
    }

    ISO_WITH_TZ_REGEX.matchEntire(trimmed)?.let { m ->
      val validDate = buildValidatedUtcMillis(
        m.groupValues[1].toInt(),
        m.groupValues[2].toInt(),
        m.groupValues[3].toInt(),
      )
      if (validDate == null) {
        return Result.failure(IllegalArgumentException("Invalid calendar date \"${trimmed.take(64)}\"."))
      }
      val hour = m.groupValues[4].toInt()
      val min = m.groupValues[5].toInt()
      val sec = m.groupValues[6].ifEmpty { "0" }.toInt()
      val fracStr = m.groupValues[7]
      val ms = if (fracStr.isNotEmpty()) fracStr.take(3).padEnd(3, '0').toInt() else 0
      val baseUtc = buildValidatedUtcMillis(
        m.groupValues[1].toInt(),
        m.groupValues[2].toInt(),
        m.groupValues[3].toInt(),
        hour,
        min,
        sec,
        ms,
      ) ?: return Result.failure(IllegalArgumentException("Invalid timestamp \"${trimmed.take(64)}\"."))
      val tz = m.groupValues[8].uppercase(Locale.US)
      val offsetMs = if (tz == "Z") {
        0L
      } else {
        val sign = if (tz[0] == '-') -1L else 1L
        val digits = tz.substring(1).replace(":", "")
        val offHours = digits.substring(0, 2).toIntOrNull() ?: 0
        val offMins = digits.substring(2, 4).toIntOrNull() ?: 0
        sign * ((offHours * 60L) + offMins) * 60_000L
      }
      val finalMillis = baseUtc - offsetMs
      if (!isValidYearRange(finalMillis)) {
        return Result.failure(IllegalArgumentException("Invalid timestamp \"${trimmed.take(64)}\"."))
      }
      return Result.success(DateUtils.formatIsoUtc(finalMillis))
    }

    SLASH_DATETIME_REGEX.matchEntire(trimmed)?.let { m ->
      val y = (m.groupValues[1].ifEmpty { m.groupValues[6] }).toInt()
      val mo = (m.groupValues[2].ifEmpty { m.groupValues[4] }).toInt()
      val da = (m.groupValues[3].ifEmpty { m.groupValues[5] }).toInt()
      val h = m.groupValues[7].ifEmpty { "0" }.toInt()
      val mi = m.groupValues[8].ifEmpty { "0" }.toInt()
      val se = m.groupValues[9].ifEmpty { "0" }.toInt()
      val baseMillis = buildValidatedUtcMillis(y, mo, da, h, mi, se)
        ?: return Result.failure(IllegalArgumentException("Invalid calendar date \"${trimmed.take(64)}\"."))
      return Result.success(DateUtils.formatIsoUtc(baseMillis))
    }

    if (MONTH_NAME_REGEX.containsMatchIn(trimmed) && FOUR_DIGIT_YEAR_REGEX.containsMatchIn(trimmed)) {
      val patterns = listOf(
        "MMM d, yyyy HH:mm:ss z",
        "MMM d, yyyy HH:mm:ss",
        "MMM d, yyyy h:mm a",
        "MMM d, yyyy",
        "MMMM d, yyyy HH:mm:ss",
        "MMMM d, yyyy",
        "EEE, dd MMM yyyy HH:mm:ss z",
        "dd MMM yyyy HH:mm:ss z",
        "dd MMM yyyy",
      )
      for (pat in patterns) {
        try {
          val fmt = SimpleDateFormat(pat, Locale.US).apply {
            timeZone = utcTimeZone
            isLenient = false
          }
          val parsed: Date? = fmt.parse(trimmed)
          if (parsed != null && isValidYearRange(parsed.time)) {
            return Result.success(DateUtils.formatIsoUtc(parsed.time))
          }
        } catch (_: Exception) {
          // Try next format
        }
      }
    }

    return Result.failure(IllegalArgumentException("Invalid timestamp \"${trimmed.take(64)}\"."))
  }
}
