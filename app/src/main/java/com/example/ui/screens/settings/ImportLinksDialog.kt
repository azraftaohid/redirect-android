package com.example.ui.screens.settings

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ImportConflictMode
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun ImportLinksDialog(
  state: ImportDialogState,
  onDismiss: () -> Unit,
  onConflictModeChange: (ImportConflictMode) -> Unit,
  onFileLoaded: (fileName: String, content: String) -> Unit,
  onStartImport: () -> Unit,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val semantic = LocalSemanticColors.current

  val openDocLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      var name = "import.json"
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
          name = cursor.getString(idx) ?: name
        }
      }
      val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
        it.readText()
      }.orEmpty()
      onFileLoaded(name, content)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.import_title)) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 440.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = { openDocLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
          enabled = !state.isImporting,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("import_pick_file_button"),
        ) {
          Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = if (state.fileName.isNotEmpty()) state.fileName else stringResource(R.string.import_select_file),
            maxLines = 1,
          )
        }

        if (state.parsedLinks.isNotEmpty()) {
          Text(
            text = "${state.parsedLinks.size} links ready to import",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (!state.parseError.isNullOrBlank()) {
          Surface(
            color = semantic.dangerBg,
            border = BorderStroke(1.dp, semantic.dangerBorder),
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = state.parseError,
              style = MaterialTheme.typography.bodySmall,
              color = semantic.dangerText,
              modifier = Modifier.padding(10.dp),
            )
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("On Slug Conflict", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = state.conflictMode == ImportConflictMode.SKIP,
              onClick = { if (!state.isImporting) onConflictModeChange(ImportConflictMode.SKIP) },
              label = { Text(stringResource(R.string.import_conflict_skip)) },
              modifier = Modifier.testTag("import_mode_skip"),
            )
            FilterChip(
              selected = state.conflictMode == ImportConflictMode.OVERWRITE,
              onClick = { if (!state.isImporting) onConflictModeChange(ImportConflictMode.OVERWRITE) },
              label = { Text(stringResource(R.string.import_conflict_overwrite)) },
              modifier = Modifier.testTag("import_mode_overwrite"),
            )
          }
        }

        if (state.isImporting) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val progress = if (state.totalCount > 0) {
              state.processedCount.toFloat() / state.totalCount.toFloat()
            } else {
              0f
            }
            LinearProgressIndicator(
              progress = { progress },
              modifier = Modifier.fillMaxWidth(),
            )
            Text(
              text = "Importing ${state.processedCount} / ${state.totalCount}…",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (!state.importError.isNullOrBlank()) {
          Surface(
            color = semantic.dangerBg,
            border = BorderStroke(1.dp, semantic.dangerBorder),
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = state.importError,
              style = MaterialTheme.typography.bodySmall,
              color = semantic.dangerText,
              modifier = Modifier.padding(10.dp),
            )
          }
        }

        state.result?.let { res ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            SummaryStatPill("Created", res.summary.created, semantic.successBg, semantic.successText, Modifier.weight(1f))
            SummaryStatPill("Updated", res.summary.updated, semantic.neutralBg, semantic.neutralText, Modifier.weight(1f))
            SummaryStatPill("Skipped", res.summary.skipped, semantic.warningBg, semantic.warningText, Modifier.weight(1f))
            SummaryStatPill("Failed", res.summary.failed, semantic.dangerBg, semantic.dangerText, Modifier.weight(1f))
          }

          val skippedRows = res.results.filter { it.status == "skipped" }
          val existingSlugs = skippedRows.filter { it.reason == "slug_exists" }.map { it.slug }
          val dupBatchSlugs = skippedRows.filter { it.reason == "duplicate_in_batch" }.map { it.slug }
          val fallbackSkipped = if (skippedRows.isEmpty()) res.skippedSlugs else emptyList()

          if (existingSlugs.isNotEmpty() || dupBatchSlugs.isNotEmpty() || fallbackSkipped.isNotEmpty()) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                if (existingSlugs.isNotEmpty()) {
                  Text(
                    text = "Already exists (${existingSlugs.size}): ${existingSlugs.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                  )
                }
                if (dupBatchSlugs.isNotEmpty()) {
                  Text(
                    text = "Duplicate in batch (${dupBatchSlugs.size}): ${dupBatchSlugs.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                  )
                }
                if (fallbackSkipped.isNotEmpty()) {
                  Text(
                    text = "Skipped (${fallbackSkipped.size}): ${fallbackSkipped.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                  )
                }
                val allSkipped = (existingSlugs + dupBatchSlugs + fallbackSkipped).distinct()
                TextButton(
                  onClick = {
                    clipboard.setText(AnnotatedString(allSkipped.joinToString(", ")))
                    Toast.makeText(context, "Skipped slugs copied", Toast.LENGTH_SHORT).show()
                  },
                  modifier = Modifier.testTag("import_copy_skipped_slugs"),
                ) {
                  Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text(stringResource(R.string.import_copy_slugs))
                }
              }
            }
          }

          if (res.errors.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "Row Errors (${res.errors.size})",
                style = MaterialTheme.typography.labelMedium,
                color = semantic.dangerText,
              )
              res.errors.take(25).forEach { err ->
                Text(
                  text = "#${err.index + 1} (${err.slug}): ${err.message}",
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                  color = semantic.dangerText,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onStartImport,
        enabled = state.parsedLinks.isNotEmpty() && !state.isImporting,
        modifier = Modifier.testTag("import_start_button"),
      ) {
        Text(stringResource(R.string.import_start))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !state.isImporting,
        modifier = Modifier.testTag("import_close_button"),
      ) {
        Text(stringResource(R.string.qr_close))
      }
    },
  )
}

@Composable
private fun SummaryStatPill(
  label: String,
  count: Int,
  bg: androidx.compose.ui.graphics.Color,
  fg: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    color = bg,
    shape = MaterialTheme.shapes.small,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.titleSmall.copy(
          fontFamily = MonoFontFamily,
          fontWeight = FontWeight.Bold,
        ),
        color = fg,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        maxLines = 1,
      )
    }
  }
}
