package com.example.ui.screens.settings

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.model.ImportConflictMode
import com.example.domain.util.CsvDelimiterOption
import com.example.domain.util.FieldMappingMode
import com.example.domain.util.ImportTargetField
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportLinksDialog(
  state: ImportDialogState,
  onDismiss: () -> Unit,
  onConflictModeChange: (ImportConflictMode) -> Unit,
  onFileLoaded: (fileName: String, content: String) -> Unit,
  onToggleCustomizeV1: () -> Unit,
  onSelectJsonArrayPath: (String) -> Unit,
  onCsvOptionsChange: (CsvDelimiterOption, Boolean) -> Unit,
  onFieldModeChange: (ImportTargetField, FieldMappingMode) -> Unit,
  onSourceFieldChange: (ImportTargetField, String) -> Unit,
  onConstantChange: (ImportTargetField, String) -> Unit,
  onStartImport: () -> Unit,
  onCancelImport: () -> Unit = {},
  onRetryFailed: () -> Unit = {},
  onParsingStateChange: (Boolean) -> Unit = {},
) {
  val context = LocalContext.current
  val semantic = LocalSemanticColors.current
  val scope = rememberCoroutineScope()

  val openDocLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      scope.launch {
        onParsingStateChange(true)
        val (name, content) = withContext(Dispatchers.IO) {
          var resolvedName = "import.json"
          context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
              resolvedName = cursor.getString(idx) ?: resolvedName
            }
          }
          val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
            it.readText()
          }.orEmpty()
          resolvedName to text
        }
        onFileLoaded(name, content)
      }
    }
  }

  val launchPicker: () -> Unit = {
    openDocLauncher.launch(
      arrayOf(
        "application/json",
        "text/csv",
        "text/comma-separated-values",
        "text/tab-separated-values",
        "text/plain",
        "*/*",
      ),
    )
  }

  Dialog(
    onDismissRequest = { if (!state.isImporting) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    BoxWithConstraints(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      val isTabletModal = maxWidth >= 600.dp
      val useTwoPane = maxWidth >= 760.dp

      Surface(
        modifier = if (isTabletModal) {
          Modifier
            .widthIn(max = 960.dp)
            .fillMaxWidth(0.92f)
            .heightIn(max = 820.dp)
        } else {
          Modifier.fillMaxSize()
        },
        shape = if (isTabletModal) MaterialTheme.shapes.extraLarge else RectangleShape,
        color = MaterialTheme.colorScheme.background,
        tonalElevation = if (isTabletModal) 4.dp else 0.dp,
        shadowElevation = if (isTabletModal) 8.dp else 0.dp,
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          // Top header bar
          Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = if (isTabletModal) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().statusBarsPadding(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                onClick = onDismiss,
                enabled = !state.isImporting,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("import_close_button"),
              ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.qr_close))
              }
              Column(
                modifier = Modifier
                  .weight(1f)
                  .padding(horizontal = 4.dp),
              ) {
                Text(
                  text = stringResource(R.string.import_title),
                  style = MaterialTheme.typography.titleLarge,
                )
                Text(
                  text = state.fileName.ifEmpty { "JSON exports or CSV spreadsheets" },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              if (state.sourceFormat != ImportSourceFormat.NONE) {
                val formatLabel = when (state.sourceFormat) {
                  ImportSourceFormat.JSON_V1 -> "Redirect JSON"
                  ImportSourceFormat.JSON_DYNAMIC -> "JSON"
                  ImportSourceFormat.CSV -> "CSV"
                  ImportSourceFormat.NONE -> ""
                }
                Surface(
                  color = semantic.neutralBg,
                  shape = MaterialTheme.shapes.small,
                  modifier = Modifier.padding(end = 8.dp),
                ) {
                  Text(
                    text = formatLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = semantic.neutralText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          // Body
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
          ) {
            val result = state.result
            val showMapping = state.isUsingMappingUi &&
              state.rawRows.isNotEmpty() &&
              state.parseError.isNullOrBlank()

            if (state.isParsingFile) {
              Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  CircularProgressIndicator()
                  Text(
                    text = "Reading and validating ${state.fileName.ifEmpty { "file" }}…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            } else if (result != null) {
              Column(
                modifier = Modifier
                  .widthIn(max = 760.dp)
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                ImportResultsSection(
                  result = result,
                  importError = state.importError,
                  isWideLayout = isTabletModal,
                  remainingRetryCount = state.remainingRetryItems.size,
                  onRetryFailed = onRetryFailed,
                )
              }
            } else if (useTwoPane && showMapping) {
              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
              ) {
                Column(
                  modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                  verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                  SourceFileCard(
                    state = state,
                    onPickFile = launchPicker,
                    onToggleCustomizeV1 = onToggleCustomizeV1,
                  )
                  FormatOptionsCard(
                    state = state,
                    onSelectJsonArrayPath = onSelectJsonArrayPath,
                    onCsvOptionsChange = onCsvOptionsChange,
                  )
                  StatusAndErrorsBanners(
                    state = state,
                    onRetryFailed = onRetryFailed,
                  )
                  ConflictResolutionCard(
                    state = state,
                    onConflictModeChange = onConflictModeChange,
                  )
                  state.activeTransformResult?.let { transformed ->
                    ImportPreflightAndPreviewCard(transformedResult = transformed)
                  }
                }
                Column(
                  modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                  verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                  ImportFieldMappingCard(
                    sourceFields = state.sourceFields,
                    firstRow = state.rawRows.firstOrNull(),
                    fieldMapping = state.fieldMapping,
                    isImporting = state.isImporting,
                    autoCollapseCoreWhenClean = false,
                    optionalInitiallyExpanded = true,
                    onModeChange = onFieldModeChange,
                    onSourceFieldChange = onSourceFieldChange,
                    onConstantChange = onConstantChange,
                  )
                }
              }
            } else {
              Column(
                modifier = Modifier
                  .widthIn(max = 640.dp)
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                SourceFileCard(
                  state = state,
                  onPickFile = launchPicker,
                  onToggleCustomizeV1 = onToggleCustomizeV1,
                )
                FormatOptionsCard(
                  state = state,
                  onSelectJsonArrayPath = onSelectJsonArrayPath,
                  onCsvOptionsChange = onCsvOptionsChange,
                )
                StatusAndErrorsBanners(
                  state = state,
                  onRetryFailed = onRetryFailed,
                )
                if (showMapping) {
                  ImportFieldMappingCard(
                    sourceFields = state.sourceFields,
                    firstRow = state.rawRows.firstOrNull(),
                    fieldMapping = state.fieldMapping,
                    isImporting = state.isImporting,
                    autoCollapseCoreWhenClean = true,
                    optionalInitiallyExpanded = isTabletModal,
                    onModeChange = onFieldModeChange,
                    onSourceFieldChange = onSourceFieldChange,
                    onConstantChange = onConstantChange,
                  )
                }
                if (state.parseError.isNullOrBlank()) {
                  state.activeTransformResult?.let { transformed ->
                    ImportPreflightAndPreviewCard(transformedResult = transformed)
                  }
                }
                if (state.fileName.isNotEmpty() || state.sourceFormat != ImportSourceFormat.NONE) {
                  ConflictResolutionCard(
                    state = state,
                    onConflictModeChange = onConflictModeChange,
                  )
                }
              }
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          // Pinned bottom action bar
          Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = if (isTabletModal) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().navigationBarsPadding(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (state.isImporting) {
                val progress = if (state.totalCount > 0) {
                  state.processedCount.toFloat() / state.totalCount.toFloat()
                } else {
                  0f
                }
                LinearProgressIndicator(
                  progress = { progress },
                  modifier = Modifier.fillMaxWidth(),
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "Importing ${state.processedCount} of ${state.totalCount} links…",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                  )
                  OutlinedButton(
                    onClick = onCancelImport,
                    modifier = Modifier.testTag("import_cancel_button"),
                  ) {
                    Text(stringResource(R.string.links_cancel))
                  }
                }
              } else if (state.result != null) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  OutlinedButton(
                    onClick = launchPicker,
                    modifier = Modifier
                      .weight(1f)
                      .height(48.dp),
                  ) {
                    Text("Import Another")
                  }
                  Button(
                    onClick = onDismiss,
                    modifier = Modifier
                      .weight(1f)
                      .height(48.dp),
                  ) {
                    Text("Done")
                  }
                }
              } else {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                      text = when {
                        state.fileName.isEmpty() -> "No file selected"
                        !state.parseError.isNullOrBlank() -> "Fix file options"
                        else -> "${state.readyLinkCount} link${if (state.readyLinkCount == 1) "" else "s"} ready"
                      },
                      style = MaterialTheme.typography.titleSmall,
                    )
                    if (state.totalSourceRowCount > 0 && state.parseError.isNullOrBlank()) {
                      Text(
                        text = "${state.totalSourceRowCount} total rows · batches of 200",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  Button(
                    onClick = onStartImport,
                    enabled = state.readyLinkCount > 0 && !state.isImporting && !state.isParsingFile && state.parseError.isNullOrBlank(),
                    modifier = Modifier
                      .height(48.dp)
                      .testTag("import_start_button"),
                  ) {
                    Text(stringResource(R.string.import_start))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SourceFileCard(
  state: ImportDialogState,
  onPickFile: () -> Unit,
  onToggleCustomizeV1: () -> Unit,
) {
  val semantic = LocalSemanticColors.current

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.size(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (state.fileName.isEmpty()) Icons.Filled.UploadFile else Icons.Filled.Description,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = state.fileName.ifEmpty { "Select Import File" },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = if (state.fileName.isEmpty()) {
              "Supports Redirect JSON exports, generic JSON arrays, and CSV files."
            } else {
              "${state.totalSourceRowCount} row(s) detected"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      OutlinedButton(
        onClick = onPickFile,
        enabled = !state.isImporting && !state.isParsingFile,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("import_pick_file_button"),
      ) {
        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          text = if (state.fileName.isNotEmpty()) "Change File" else stringResource(R.string.import_select_file),
          maxLines = 1,
        )
      }

      if (state.sourceFormat == ImportSourceFormat.JSON_V1) {
        val v1Ready = state.v1FastPathResult?.validItems?.size ?: 0
        val v1Skipped = state.v1FastPathResult?.clientErrors?.size ?: 0
        Surface(
          color = semantic.successBg,
          border = BorderStroke(1.dp, semantic.successBorder),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.CheckCircleOutline,
                contentDescription = null,
                tint = semantic.successText,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = if (v1Skipped > 0) {
                  "$v1Ready ready · $v1Skipped invalid skipped (Redirect v1 export)"
                } else {
                  "$v1Ready links ready (Redirect v1 export)"
                },
                style = MaterialTheme.typography.labelLarge,
                color = semantic.successText,
              )
            }
            TextButton(
              onClick = onToggleCustomizeV1,
              enabled = !state.isImporting,
              modifier = Modifier.testTag("import_toggle_customize_v1"),
            ) {
              Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(
                if (state.isCustomizingV1Mapping) {
                  "Use standard export mapping"
                } else {
                  "Customize mapping & constants"
                },
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatOptionsCard(
  state: ImportDialogState,
  onSelectJsonArrayPath: (String) -> Unit,
  onCsvOptionsChange: (CsvDelimiterOption, Boolean) -> Unit,
) {
  val showJsonSelector = state.isJsonObjectRoot &&
    state.jsonArrayCandidates.isNotEmpty() &&
    state.isUsingMappingUi
  val showCsvOptions = state.sourceFormat == ImportSourceFormat.CSV

  if (!showJsonSelector && !showCsvOptions) return

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (showJsonSelector) {
        var arrayDropdownExpanded by remember { mutableStateOf(false) }
        val selectedLabel = state.jsonArrayCandidates.firstOrNull {
          it.path == state.selectedArrayPath
        }?.label ?: state.selectedArrayPath
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Array Property in JSON Object", style = MaterialTheme.typography.titleSmall)
          Box {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              shape = MaterialTheme.shapes.small,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(enabled = !state.isImporting) { arrayDropdownExpanded = true }
                .testTag("import_json_array_selector"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = selectedLabel,
                  style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                  modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose array property")
              }
            }
            DropdownMenu(
              expanded = arrayDropdownExpanded,
              onDismissRequest = { arrayDropdownExpanded = false },
            ) {
              state.jsonArrayCandidates.forEach { candidate ->
                DropdownMenuItem(
                  text = {
                    Text(
                      text = candidate.label,
                      style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                    )
                  },
                  onClick = {
                    onSelectJsonArrayPath(candidate.path)
                    arrayDropdownExpanded = false
                  },
                )
              }
            }
          }
        }
      }

      if (showCsvOptions) {
        Text("CSV Parsing Options", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          CsvDelimiterOption.entries.forEachIndexed { idx, option ->
            val shortLabel = when (option) {
              CsvDelimiterOption.AUTO -> {
                val d = state.detectedCsvDelimiter
                if (d != null) "Auto (${if (d == '\t') "\\t" else d})" else "Auto"
              }
              CsvDelimiterOption.COMMA -> ","
              CsvDelimiterOption.SEMICOLON -> ";"
              CsvDelimiterOption.TAB -> "\\t"
            }
            SegmentedButton(
              selected = state.csvDelimiterOption == option,
              onClick = { if (!state.isImporting) onCsvOptionsChange(option, state.csvHasHeader) },
              shape = SegmentedButtonDefaults.itemShape(
                index = idx,
                count = CsvDelimiterOption.entries.size,
              ),
              enabled = !state.isImporting,
              modifier = Modifier.testTag("import_csv_delimiter_${option.name.lowercase()}"),
            ) {
              Text(shortLabel, maxLines = 1)
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.isImporting) {
              onCsvOptionsChange(state.csvDelimiterOption, !state.csvHasHeader)
            }
            .padding(vertical = 4.dp)
            .testTag("import_csv_has_header_row"),
        ) {
          Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = "First row contains headers",
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              text = "Turn off if your spreadsheet starts directly with link data.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = state.csvHasHeader,
            onCheckedChange = { checked ->
              if (!state.isImporting) {
                onCsvOptionsChange(state.csvDelimiterOption, checked)
              }
            },
            modifier = Modifier.testTag("import_csv_has_header_checkbox"),
          )
        }
      }
    }
  }
}

@Composable
private fun StatusAndErrorsBanners(
  state: ImportDialogState,
  onRetryFailed: () -> Unit,
) {
  val semantic = LocalSemanticColors.current

  if (!state.parseError.isNullOrBlank()) {
    Surface(
      color = semantic.dangerBg,
      border = BorderStroke(1.dp, semantic.dangerBorder),
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = state.parseError,
        style = MaterialTheme.typography.bodySmall,
        color = semantic.dangerText,
        modifier = Modifier.padding(14.dp),
      )
    }
  }

  if (state.totalSourceRowCount > 0 && state.isUsingMappingUi && state.parseError.isNullOrBlank()) {
    Surface(
      color = semantic.neutralBg,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = "Ready to import ${state.readyLinkCount} of ${state.totalSourceRowCount} row(s)",
        style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
        color = semantic.neutralText,
        modifier = Modifier
          .padding(horizontal = 14.dp, vertical = 10.dp)
          .testTag("import_ready_count"),
      )
    }
  }

  if (!state.importError.isNullOrBlank()) {
    Surface(
      color = semantic.dangerBg,
      border = BorderStroke(1.dp, semantic.dangerBorder),
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = state.importError,
          style = MaterialTheme.typography.bodySmall,
          color = semantic.dangerText,
        )
        if (state.canRetryFailedImport) {
          OutlinedButton(
            onClick = onRetryFailed,
            modifier = Modifier.testTag("import_retry_failed_button"),
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry Remaining (${state.remainingRetryItems.size})")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConflictResolutionCard(
  state: ImportDialogState,
  onConflictModeChange: (ImportConflictMode) -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("On Slug Conflict", style = MaterialTheme.typography.titleSmall)
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
          selected = state.conflictMode == ImportConflictMode.SKIP,
          onClick = { if (!state.isImporting) onConflictModeChange(ImportConflictMode.SKIP) },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
          enabled = !state.isImporting,
          modifier = Modifier.testTag("import_mode_skip"),
        ) {
          Text(stringResource(R.string.import_conflict_skip), maxLines = 1)
        }
        SegmentedButton(
          selected = state.conflictMode == ImportConflictMode.OVERWRITE,
          onClick = { if (!state.isImporting) onConflictModeChange(ImportConflictMode.OVERWRITE) },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
          enabled = !state.isImporting,
          modifier = Modifier.testTag("import_mode_overwrite"),
        ) {
          Text(stringResource(R.string.import_conflict_overwrite), maxLines = 1)
        }
      }
      Text(
        text = if (state.conflictMode == ImportConflictMode.SKIP) {
          "Existing short slugs remain untouched; only new slugs are created."
        } else {
          "Matching slugs on the server are updated with destination URLs and metadata from this file."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
