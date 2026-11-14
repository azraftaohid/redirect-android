package com.example.domain.util

data class SharedPayload(
  val url: String = "",
  val title: String = "",
)

object ShareParser {
  private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
  private val TRAILING_PUNCT_REGEX = Regex("""[.,;:!?)>\]"']+$""")

  fun extractSharedPayload(text: String?, subject: String?): SharedPayload {
    val safeText = text?.trim().orEmpty()
    val safeSubject = subject?.trim().orEmpty()

    val matchFromText = URL_REGEX.find(safeText)
    val matchFromSubject = if (matchFromText == null) URL_REGEX.find(safeSubject) else null
    val match = matchFromText ?: matchFromSubject

    val extractedUrl = match?.value
      ?.replace(TRAILING_PUNCT_REGEX, "")
      .orEmpty()

    val rawTitle = when {
      matchFromText != null -> {
        val remainder = safeText.removeRange(matchFromText.range)
          .replace(Regex("""\s+"""), " ")
          .trim()
          .trim('-', '|', ':')
          .trim()
        remainder.ifEmpty { safeSubject }
      }
      matchFromSubject != null -> {
        safeSubject.removeRange(matchFromSubject.range)
          .replace(Regex("""\s+"""), " ")
          .trim()
          .trim('-', '|', ':')
          .trim()
      }
      else -> safeSubject
    }

    return SharedPayload(
      url = extractedUrl,
      title = rawTitle.take(ValidationUtils.MAX_TITLE_LENGTH),
    )
  }
}
