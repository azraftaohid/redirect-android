package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ImportLinksResponse
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun ImportResultsSection(
  result: ImportLinksResponse,
  importError: String?,
  isWideLayout: Boolean,
  remainingRetryCount: Int = 0,
  onRetryFailed: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val semantic = LocalSemanticColors.current

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      color = if (importError == null) semantic.successBg else semantic.warningBg,
      border = BorderStroke(1.dp, if (importError == null) semantic.successBorder else semantic.warningBorder),
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(
            imageVector = if (importError == null) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (importError == null) semantic.successText else semantic.warningText,
            modifier = Modifier.size(20.dp),
          )
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = if (importError == null) "Import Complete" else "Import Interrupted",
              style = MaterialTheme.typography.titleSmall,
              color = if (importError == null) semantic.successText else semantic.warningText,
            )
            Text(
              text = importError ?: "Processed ${result.summary.total} link row(s).",
              style = MaterialTheme.typography.bodySmall,
              color = if (importError == null) semantic.successText else semantic.warningText,
            )
          }
        }
        if (importError != null && remainingRetryCount > 0 && onRetryFailed != null) {
          Button(
            onClick = onRetryFailed,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("import_retry_failed_button"),
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Retry Remaining ($remainingRetryCount)")
          }
        }
      }
    }

    if (isWideLayout) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        ImportStatTile("Created", result.summary.created, semantic.successBg, semantic.successText, Modifier.weight(1f))
        ImportStatTile("Updated", result.summary.updated, semantic.neutralBg, semantic.neutralText, Modifier.weight(1f))
        ImportStatTile("Skipped", result.summary.skipped, semantic.warningBg, semantic.warningText, Modifier.weight(1f))
        ImportStatTile("Failed", result.summary.failed, semantic.dangerBg, semantic.dangerText, Modifier.weight(1f))
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ImportStatTile("Created", result.summary.created, semantic.successBg, semantic.successText, Modifier.weight(1f))
          ImportStatTile("Updated", result.summary.updated, semantic.neutralBg, semantic.neutralText, Modifier.weight(1f))
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ImportStatTile("Skipped", result.summary.skipped, semantic.warningBg, semantic.warningText, Modifier.weight(1f))
          ImportStatTile("Failed", result.summary.failed, semantic.dangerBg, semantic.dangerText, Modifier.weight(1f))
        }
      }
    }

    val skippedRows = result.results.filter { it.status == "skipped" }
    val existingSlugs = skippedRows.filter { it.reason == "slug_exists" }.mapNotNull { it.slug?.takeIf(String::isNotBlank) }
    val dupBatchSlugs = skippedRows.filter { it.reason == "duplicate_in_batch" }.mapNotNull { it.slug?.takeIf(String::isNotBlank) }
    val fallbackSkipped = if (skippedRows.isEmpty()) result.skippedSlugs else emptyList()

    if (existingSlugs.isNotEmpty() || dupBatchSlugs.isNotEmpty() || fallbackSkipped.isNotEmpty()) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Skipped Slugs", style = MaterialTheme.typography.titleSmall)
          if (existingSlugs.isNotEmpty()) {
            Text(
              text = "Already exists (${existingSlugs.size}): ${existingSlugs.joinToString(", ")}",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            )
          }
          if (dupBatchSlugs.isNotEmpty()) {
            Text(
              text = "Duplicate in batch (${dupBatchSlugs.size}): ${dupBatchSlugs.joinToString(", ")}",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            )
          }
          if (fallbackSkipped.isNotEmpty()) {
            Text(
              text = "Skipped (${fallbackSkipped.size}): ${fallbackSkipped.joinToString(", ")}",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            )
          }
          val allSkipped = (existingSlugs + dupBatchSlugs + fallbackSkipped).distinct()
          OutlinedButton(
            onClick = {
              clipboard.setText(AnnotatedString(allSkipped.joinToString(", ")))
              Toast.makeText(context, "Skipped slugs copied", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.testTag("import_copy_skipped_slugs"),
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.import_copy_slugs))
          }
        }
      }
    }

    if (result.errors.isNotEmpty()) {
      Card(
        colors = CardDefaults.cardColors(containerColor = semantic.dangerBg),
        border = BorderStroke(1.dp, semantic.dangerBorder),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Row Errors (${result.errors.size})",
            style = MaterialTheme.typography.titleSmall,
            color = semantic.dangerText,
          )
          result.errors.take(25).forEachIndexed { idx, err ->
            if (idx > 0) HorizontalDivider(color = semantic.dangerBorder.copy(alpha = 0.4f))
            val slugSuffix = err.slug?.takeIf { it.isNotBlank() }?.let { " · /$it" }.orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = "Row #${err.index + 1}$slugSuffix",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = semantic.dangerText,
              )
              Text(
                text = err.message,
                style = MaterialTheme.typography.bodySmall,
                color = semantic.dangerText,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ImportStatTile(
  label: String,
  count: Int,
  bg: Color,
  fg: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    color = bg,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.headlineSmall.copy(
          fontFamily = MonoFontFamily,
          fontWeight = FontWeight.Bold,
        ),
        color = fg,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
      )
    }
  }
}
