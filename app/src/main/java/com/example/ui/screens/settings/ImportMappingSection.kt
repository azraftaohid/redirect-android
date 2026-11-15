package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.util.DynamicImporter
import com.example.domain.util.FieldMappingEntry
import com.example.domain.util.FieldMappingMode
import com.example.domain.util.ImportTargetField
import com.example.domain.util.TransformMappedRowsResult
import com.example.domain.util.ValidationUtils
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.serialization.json.JsonElement

private val PRIMARY_IMPORT_FIELDS = listOf(
  ImportTargetField.TARGET_URL,
  ImportTargetField.SLUG,
  ImportTargetField.TITLE,
)

private val OPTIONAL_IMPORT_FIELDS = ImportTargetField.entries.filterNot { it in PRIMARY_IMPORT_FIELDS }

@Composable
fun ImportFieldMappingCard(
  sourceFields: List<String>,
  firstRow: JsonElement?,
  fieldMapping: Map<ImportTargetField, FieldMappingEntry>,
  isImporting: Boolean,
  autoCollapseCoreWhenClean: Boolean = false,
  optionalInitiallyExpanded: Boolean = false,
  onModeChange: (ImportTargetField, FieldMappingMode) -> Unit,
  onSourceFieldChange: (ImportTargetField, String) -> Unit,
  onConstantChange: (ImportTargetField, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalSemanticColors.current

  val isCoreAutoMappedCleanly = remember(fieldMapping) {
    val target = fieldMapping[ImportTargetField.TARGET_URL]
    target != null &&
      target.mode != FieldMappingMode.OMIT &&
      (target.mode != FieldMappingMode.FIELD || target.sourceField.isNotEmpty())
  }

  var isCoreExpanded by rememberSaveable(autoCollapseCoreWhenClean, isCoreAutoMappedCleanly) {
    mutableStateOf(!autoCollapseCoreWhenClean || !isCoreAutoMappedCleanly)
  }

  var showOptionalFields by rememberSaveable(optionalInitiallyExpanded) {
    mutableStateOf(optionalInitiallyExpanded)
  }

  val activeOptionalCount = remember(fieldMapping) {
    OPTIONAL_IMPORT_FIELDS.count { field ->
      val mode = fieldMapping[field]?.mode ?: FieldMappingMode.OMIT
      mode != FieldMappingMode.OMIT
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
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
          modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isImporting && isCoreAutoMappedCleanly) {
              isCoreExpanded = !isCoreExpanded
            }
            .testTag("mapping_core_toggle"),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Core Field Mapping",
                style = MaterialTheme.typography.titleMedium,
              )
              if (isCoreAutoMappedCleanly && !isCoreExpanded) {
                Surface(
                  color = semantic.successBg,
                  border = BorderStroke(1.dp, semantic.successBorder),
                  shape = MaterialTheme.shapes.extraSmall,
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Filled.CheckCircleOutline,
                      contentDescription = null,
                      tint = semantic.successText,
                      modifier = Modifier.size(12.dp),
                    )
                    Text(
                      text = "Auto-mapped",
                      style = MaterialTheme.typography.labelSmall,
                      color = semantic.successText,
                    )
                  }
                }
              }
            }
            Text(
              text = if (isCoreExpanded) {
                "Match your file columns to destination URL, optional slug, and title."
              } else {
                "Columns automatically matched. Tap Edit to customize."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (isCoreAutoMappedCleanly) {
            TextButton(
              onClick = { isCoreExpanded = !isCoreExpanded },
              enabled = !isImporting,
              modifier = Modifier.testTag("mapping_core_edit_button"),
            ) {
              Icon(
                imageVector = if (isCoreExpanded) Icons.Filled.ExpandLess else Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(if (isCoreExpanded) "Collapse" else "Edit")
            }
          }
        }

        if (!isCoreExpanded) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("mapping_core_summary"),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              PRIMARY_IMPORT_FIELDS.forEach { field ->
                val entry = fieldMapping[field] ?: FieldMappingEntry(FieldMappingMode.OMIT)
                val mappingValueLabel = when (entry.mode) {
                  FieldMappingMode.OMIT -> if (field == ImportTargetField.SLUG) "(Auto-generate)" else "(Omitted)"
                  FieldMappingMode.FIELD -> entry.sourceField.ifEmpty { "(Unassigned)" }
                  FieldMappingMode.CONSTANT -> "\"${entry.constantValue}\""
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = field.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = mappingValueLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                      fontFamily = MonoFontFamily,
                      fontWeight = FontWeight.Medium,
                    ),
                    color = if (entry.mode == FieldMappingMode.OMIT) {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                      MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }

        AnimatedVisibility(visible = isCoreExpanded) {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PRIMARY_IMPORT_FIELDS.forEachIndexed { index, field ->
              if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              val entry = fieldMapping[field] ?: FieldMappingEntry(
                mode = if (field.required) FieldMappingMode.FIELD else FieldMappingMode.OMIT,
              )
              FieldMappingItem(
                field = field,
                entry = entry,
                sourceFields = sourceFields,
                firstRow = firstRow,
                enabled = !isImporting,
                onModeChange = { onModeChange(field, it) },
                onSourceFieldChange = { onSourceFieldChange(field, it) },
                onConstantChange = { onConstantChange(field, it) },
              )
            }
          }
        }
      }
    }

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      shape = MaterialTheme.shapes.large,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showOptionalFields = !showOptionalFields }
            .padding(vertical = 4.dp)
            .testTag("mapping_optional_toggle"),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = "Options & Timestamps",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = if (activeOptionalCount > 0) {
                "$activeOptionalCount of ${OPTIONAL_IMPORT_FIELDS.size} mapped (status, tags, active, dates)"
              } else {
                "Optional: status code, tags, active state, expiry & dates"
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(
            imageVector = if (showOptionalFields) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (showOptionalFields) "Collapse optional fields" else "Expand optional fields",
          )
        }

        AnimatedVisibility(visible = showOptionalFields) {
          Column(
            modifier = Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            OPTIONAL_IMPORT_FIELDS.forEachIndexed { index, field ->
              if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              val entry = fieldMapping[field] ?: FieldMappingEntry(FieldMappingMode.OMIT)
              FieldMappingItem(
                field = field,
                entry = entry,
                sourceFields = sourceFields,
                firstRow = firstRow,
                enabled = !isImporting,
                onModeChange = { onModeChange(field, it) },
                onSourceFieldChange = { onSourceFieldChange(field, it) },
                onConstantChange = { onConstantChange(field, it) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun ImportPreflightAndPreviewCard(
  transformedResult: TransformMappedRowsResult,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalSemanticColors.current
  var showAllWarnings by remember { mutableStateOf(false) }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (transformedResult.clientErrors.isNotEmpty()) {
      Surface(
        color = semantic.warningBg,
        border = BorderStroke(1.dp, semantic.warningBorder),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("import_preflight_warnings"),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.WarningAmber,
              contentDescription = null,
              tint = semantic.warningText,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "${transformedResult.clientErrors.size} row${if (transformedResult.clientErrors.size == 1) "" else "s"} failed validation and will be skipped",
              style = MaterialTheme.typography.labelLarge,
              color = semantic.warningText,
            )
          }
          val visibleErrors = if (showAllWarnings) {
            transformedResult.clientErrors.take(25)
          } else {
            transformedResult.clientErrors.take(3)
          }
          visibleErrors.forEach { err ->
            Text(
              text = "Row #${err.index + 1}: ${err.message}",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
              color = semantic.warningText,
            )
          }
          if (transformedResult.clientErrors.size > 3) {
            TextButton(
              onClick = { showAllWarnings = !showAllWarnings },
              modifier = Modifier.align(Alignment.End),
            ) {
              Text(
                if (showAllWarnings) {
                  "Show fewer"
                } else {
                  "Show more (${transformedResult.clientErrors.size} total)"
                },
              )
            }
          }
        }
      }
    }

    if (transformedResult.previewRows.isNotEmpty()) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("import_converted_preview"),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Preview (first ${transformedResult.previewRows.size})",
            style = MaterialTheme.typography.titleMedium,
          )
          transformedResult.previewRows.forEach { item ->
            val link = item.link
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              shape = MaterialTheme.shapes.medium,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "/${link.slug ?: "(auto-generated)"}",
                    style = MaterialTheme.typography.labelLarge.copy(
                      fontFamily = MonoFontFamily,
                      fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  Spacer(Modifier.width(8.dp))
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraSmall,
                  ) {
                    Text(
                      text = "${link.statusCode ?: 307}",
                      style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                }
                Text(
                  text = link.targetUrl,
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                val metaParts = buildList {
                  link.title?.let { add(it) }
                  if (!link.tags.isNullOrEmpty()) add(link.tags.joinToString(", ") { "#$it" })
                  link.isActive?.let { add(if (it) "Active" else "Inactive") }
                  link.expiresAt?.let { add("Exp: $it") }
                }
                if (metaParts.isNotEmpty()) {
                  Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldMappingItem(
  field: ImportTargetField,
  entry: FieldMappingEntry,
  sourceFields: List<String>,
  firstRow: JsonElement?,
  enabled: Boolean,
  onModeChange: (FieldMappingMode) -> Unit,
  onSourceFieldChange: (String) -> Unit,
  onConstantChange: (String) -> Unit,
) {
  val modeOptions = remember(field) {
    buildList {
      if (!field.required) add(FieldMappingMode.OMIT to "Omit")
      add(FieldMappingMode.FIELD to "Column")
      if (field.allowConstant) add(FieldMappingMode.CONSTANT to "Fixed")
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = if (field.required) "${field.label} *" else field.label,
        style = MaterialTheme.typography.titleSmall,
      )
      Text(
        text = field.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      modeOptions.forEachIndexed { idx, (mode, label) ->
        val tagSuffix = when (mode) {
          FieldMappingMode.OMIT -> "omit"
          FieldMappingMode.FIELD -> "field"
          FieldMappingMode.CONSTANT -> "constant"
        }
        SegmentedButton(
          selected = entry.mode == mode,
          onClick = { if (enabled) onModeChange(mode) },
          shape = SegmentedButtonDefaults.itemShape(index = idx, count = modeOptions.size),
          enabled = enabled,
          modifier = Modifier.testTag("mapping_mode_${field.key}_$tagSuffix"),
        ) {
          Text(label, maxLines = 1)
        }
      }
    }

    when (entry.mode) {
      FieldMappingMode.OMIT -> Unit
      FieldMappingMode.FIELD -> {
        var expanded by remember { mutableStateOf(false) }
        val sampleText = remember(firstRow, entry.sourceField) {
          firstRow?.let { row ->
            DynamicImporter.formatSamplePreview(DynamicImporter.getFieldByPath(row, entry.sourceField))
          }.orEmpty()
        }
        Box {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .clickable(enabled = enabled && sourceFields.isNotEmpty()) { expanded = true }
              .testTag("mapping_source_${field.key}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = entry.sourceField.ifEmpty { "Select source column…" },
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Medium,
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (sampleText.isNotEmpty()) {
                  Text(
                    text = "Sample: $sampleText",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select column")
            }
          }
          DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
          ) {
            sourceFields.forEach { sf ->
              val preview = firstRow?.let { row ->
                DynamicImporter.formatSamplePreview(DynamicImporter.getFieldByPath(row, sf))
              }.orEmpty()
              DropdownMenuItem(
                text = {
                  Column {
                    Text(
                      text = sf,
                      style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                    )
                    if (preview.isNotEmpty()) {
                      Text(
                        text = preview,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                },
                onClick = {
                  onSourceFieldChange(sf)
                  expanded = false
                },
              )
            }
          }
        }
      }
      FieldMappingMode.CONSTANT -> {
        when (field) {
          ImportTargetField.STATUS_CODE -> {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              ValidationUtils.REDIRECT_STATUS_CODES.forEachIndexed { idx, code ->
                SegmentedButton(
                  selected = entry.constantValue == code.toString(),
                  onClick = { if (enabled) onConstantChange(code.toString()) },
                  shape = SegmentedButtonDefaults.itemShape(
                    index = idx,
                    count = ValidationUtils.REDIRECT_STATUS_CODES.size,
                  ),
                  enabled = enabled,
                ) {
                  Text(code.toString())
                }
              }
            }
          }
          ImportTargetField.IS_ACTIVE, ImportTargetField.FORWARD_QUERY -> {
            val boolOpts = listOf("true" to "Enabled (true)", "false" to "Disabled (false)")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              boolOpts.forEachIndexed { idx, (valStr, lbl) ->
                SegmentedButton(
                  selected = entry.constantValue.lowercase() == valStr,
                  onClick = { if (enabled) onConstantChange(valStr) },
                  shape = SegmentedButtonDefaults.itemShape(index = idx, count = boolOpts.size),
                  enabled = enabled,
                ) {
                  Text(lbl, maxLines = 1)
                }
              }
            }
          }
          else -> {
            OutlinedTextField(
              value = entry.constantValue,
              onValueChange = { if (enabled) onConstantChange(it) },
              singleLine = true,
              enabled = enabled,
              placeholder = { Text("Enter fixed value") },
              textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("mapping_constant_${field.key}"),
            )
          }
        }
      }
    }
  }
}
