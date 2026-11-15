package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.CreateLinkRequest
import com.example.domain.model.LinkPatch
import com.example.domain.model.LinkRecord
import com.example.domain.util.DateUtils
import com.example.domain.util.ValidationUtils
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFontFamily
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LinkFormSheet(
  existingLink: LinkRecord?,
  initialTargetUrl: String = "",
  serverBaseUrl: String,
  isSubmitting: Boolean,
  serverError: String?,
  onDismiss: () -> Unit,
  onCreate: (CreateLinkRequest) -> Unit,
  onPatch: (id: String, patch: LinkPatch) -> Unit,
) {
  val isEdit = existingLink != null
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val semantic = LocalSemanticColors.current

  var targetUrl by remember(existingLink, initialTargetUrl) {
    mutableStateOf(existingLink?.targetUrl ?: initialTargetUrl)
  }
  var title by remember(existingLink) { mutableStateOf(existingLink?.title.orEmpty()) }
  var slug by remember(existingLink) { mutableStateOf(existingLink?.slug.orEmpty()) }
  var statusCode by remember(existingLink) {
    mutableIntStateOf(existingLink?.statusCode ?: ValidationUtils.DEFAULT_STATUS_CODE)
  }
  var isActive by remember(existingLink) { mutableStateOf(existingLink?.isActive ?: true) }
  var forwardQuery by remember(existingLink) { mutableStateOf(existingLink?.forwardQuery ?: true) }
  var tags by remember(existingLink) { mutableStateOf(existingLink?.tags ?: emptyList()) }
  var tagDraft by remember { mutableStateOf("") }
  var expiresAtIso by remember(existingLink) { mutableStateOf(existingLink?.expiresAt) }
  var localError by remember { mutableStateOf<String?>(null) }

  var showDatePicker by remember { mutableStateOf(false) }
  var pendingSelectedUtcDayMillis by remember { mutableStateOf<Long?>(null) }

  val addTagFromDraft: () -> Unit = {
    val normalized = ValidationUtils.normalizeTag(tagDraft)
    if (normalized.isNotEmpty() && normalized.length <= ValidationUtils.MAX_TAG_LENGTH) {
      if (normalized !in tags && tags.size < ValidationUtils.MAX_TAGS) {
        tags = tags + normalized
      }
      tagDraft = ""
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = stringResource(if (isEdit) R.string.form_edit_title else R.string.form_create_title),
        style = MaterialTheme.typography.headlineSmall,
      )

      val displayedError = localError ?: serverError
      if (!displayedError.isNullOrBlank()) {
        Surface(
          color = semantic.dangerBg,
          border = BorderStroke(1.dp, semantic.dangerBorder),
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = displayedError,
            style = MaterialTheme.typography.bodySmall,
            color = semantic.dangerText,
            modifier = Modifier.padding(10.dp),
          )
        }
      }

      OutlinedTextField(
        value = targetUrl,
        onValueChange = {
          targetUrl = it
          localError = null
        },
        label = { Text(stringResource(R.string.form_target_url_label)) },
        placeholder = { Text("https://example.com/article") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Uri,
          imeAction = ImeAction.Next,
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("form_target_url_input"),
      )

      OutlinedTextField(
        value = title,
        onValueChange = {
          if (it.length <= ValidationUtils.MAX_TITLE_LENGTH) title = it
        },
        label = { Text(stringResource(R.string.form_title_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("form_title_input"),
      )

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
          value = slug,
          onValueChange = {
            slug = it
            localError = null
          },
          label = { Text(stringResource(R.string.form_slug_label)) },
          placeholder = { Text("Auto-generated if empty") },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
          trailingIcon = {
            IconButton(
              onClick = {
                slug = ValidationUtils.generateValidRandomSlug()
                localError = null
              },
              modifier = Modifier.testTag("form_generate_slug_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Casino,
                contentDescription = stringResource(R.string.form_slug_generate),
              )
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("form_slug_input"),
        )
        if (isEdit && existingLink != null && slug.trim() != existingLink.slug) {
          Surface(
            color = semantic.warningBg,
            border = BorderStroke(1.dp, semantic.warningBorder),
            shape = MaterialTheme.shapes.small,
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.Top,
            ) {
              Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = semantic.warningText,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.form_slug_rename_warning),
                style = MaterialTheme.typography.bodySmall,
                color = semantic.warningText,
              )
            }
          }
        }
      }

      // Redirect status code chips
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.form_status_code_label),
          style = MaterialTheme.typography.labelLarge,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ValidationUtils.REDIRECT_STATUS_CODES.forEach { code ->
            FilterChip(
              selected = statusCode == code,
              onClick = { statusCode = code },
              label = {
                Text(
                  text = code.toString(),
                  style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                )
              },
              modifier = Modifier.testTag("form_status_$code"),
            )
          }
        }
        if (statusCode == 301 || statusCode == 308) {
          Surface(
            color = semantic.warningBg,
            border = BorderStroke(1.dp, semantic.warningBorder),
            shape = MaterialTheme.shapes.small,
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.Top,
            ) {
              Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = semantic.warningText,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.form_permanent_redirect_warning),
                style = MaterialTheme.typography.bodySmall,
                color = semantic.warningText,
              )
            }
          }
        }
      }

      // Switches
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(stringResource(R.string.form_active_label), style = MaterialTheme.typography.bodyLarge)
        Switch(
          checked = isActive,
          onCheckedChange = { isActive = it },
          modifier = Modifier.testTag("form_active_switch"),
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(end = 12.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = stringResource(R.string.form_forward_query_label),
            style = MaterialTheme.typography.bodyLarge,
          )
          Text(
            text = stringResource(R.string.form_forward_query_helper),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = forwardQuery,
          onCheckedChange = { forwardQuery = it },
          modifier = Modifier.testTag("form_forward_query_switch"),
        )
      }

      // Tags editor
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.form_tags_label),
          style = MaterialTheme.typography.labelLarge,
        )
        if (tags.isNotEmpty()) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            tags.forEach { tag ->
              InputChip(
                selected = false,
                onClick = { tags = tags.filterNot { it == tag } },
                label = {
                  Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                  )
                },
                trailingIcon = {
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove $tag",
                    modifier = Modifier.size(16.dp),
                  )
                },
              )
            }
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = tagDraft,
            onValueChange = { tagDraft = it },
            placeholder = { Text(stringResource(R.string.form_tags_placeholder)) },
            singleLine = true,
            enabled = tags.size < ValidationUtils.MAX_TAGS,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addTagFromDraft() }),
            modifier = Modifier
              .weight(1f)
              .testTag("form_tag_input"),
          )
          IconButton(
            onClick = addTagFromDraft,
            enabled = tagDraft.isNotBlank() && tags.size < ValidationUtils.MAX_TAGS,
            modifier = Modifier
              .size(48.dp)
              .testTag("form_add_tag_button"),
          ) {
            Icon(Icons.Filled.Add, contentDescription = "Add tag")
          }
        }
      }

      // Expiry picker
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.form_expiry_label),
          style = MaterialTheme.typography.labelLarge,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier
              .weight(1f)
              .testTag("form_expiry_picker_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Schedule,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = expiresAtIso?.let { DateUtils.formatLocalDateTime(it) }
                ?: stringResource(R.string.form_expiry_set),
              maxLines = 1,
            )
          }
          if (expiresAtIso != null) {
            TextButton(
              onClick = { expiresAtIso = null },
              modifier = Modifier.testTag("form_expiry_clear_button"),
            ) {
              Text(stringResource(R.string.form_expiry_clear))
            }
          }
        }
      }

      // Submit button
      Button(
        onClick = {
          val normalizedTarget = ValidationUtils.normalizeTargetUrlInput(targetUrl)
          val urlErr = ValidationUtils.validateTargetUrl(normalizedTarget, serverBaseUrl)
          if (urlErr != null) {
            localError = urlErr
            return@Button
          }
          val trimmedSlug = slug.trim()
          if (trimmedSlug.isNotEmpty() || isEdit) {
            val slugErr = ValidationUtils.validateSlug(trimmedSlug)
            if (slugErr != null) {
              localError = slugErr
              return@Button
            }
          }

          val finalTags = if (tagDraft.isNotBlank()) {
            ValidationUtils.normalizeTags(tags + tagDraft)
          } else {
            ValidationUtils.normalizeTags(tags)
          }

          if (!isEdit) {
            onCreate(
              CreateLinkRequest(
                targetUrl = normalizedTarget,
                slug = trimmedSlug.ifEmpty { null },
                title = title.trim().ifEmpty { null },
                statusCode = statusCode,
                tags = finalTags,
                isActive = isActive,
                forwardQuery = forwardQuery,
                expiresAt = expiresAtIso,
              ),
            )
          } else {
            val orig = existingLink!!
            val trimmedTitle = title.trim().ifEmpty { null }
            val origTitle = orig.title?.trim()?.ifEmpty { null }
            val patch = LinkPatch(
              slug = trimmedSlug,
              hasSlug = trimmedSlug != orig.slug,
              targetUrl = normalizedTarget,
              hasTargetUrl = normalizedTarget != orig.targetUrl,
              title = trimmedTitle,
              hasTitle = trimmedTitle != origTitle,
              statusCode = statusCode,
              hasStatusCode = statusCode != orig.statusCode,
              tags = finalTags,
              hasTags = finalTags != orig.tags,
              isActive = isActive,
              hasIsActive = isActive != orig.isActive,
              forwardQuery = forwardQuery,
              hasForwardQuery = forwardQuery != orig.forwardQuery,
              expiresAt = expiresAtIso,
              hasExpiresAt = expiresAtIso != orig.expiresAt,
            )
            if (patch.isEmpty) {
              onDismiss()
            } else {
              onPatch(orig.id, patch)
            }
          }
        },
        enabled = !isSubmitting,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("form_submit_button"),
      ) {
        if (isSubmitting) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        } else {
          Text(stringResource(if (isEdit) R.string.form_save else R.string.form_create))
        }
      }

      Spacer(Modifier.height(16.dp))
    }
  }

  if (showDatePicker) {
    val initialMillis = DateUtils.parseIsoUtc(expiresAtIso)
      ?: (System.currentTimeMillis() + 86_400_000L)
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            pendingSelectedUtcDayMillis = datePickerState.selectedDateMillis
            showDatePicker = false
          },
        ) {
          Text("Next")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text(stringResource(R.string.links_cancel))
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  val pendingDayMillis = pendingSelectedUtcDayMillis
  if (pendingDayMillis != null) {
    val existingCal = Calendar.getInstance().apply {
      timeInMillis = DateUtils.parseIsoUtc(expiresAtIso) ?: System.currentTimeMillis()
    }
    val timePickerState = rememberTimePickerState(
      initialHour = existingCal.get(Calendar.HOUR_OF_DAY),
      initialMinute = existingCal.get(Calendar.MINUTE),
    )
    AlertDialog(
      onDismissRequest = { pendingSelectedUtcDayMillis = null },
      title = { Text("Select Expiration Time") },
      text = { TimePicker(state = timePickerState) },
      confirmButton = {
        TextButton(
          onClick = {
            val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
              timeInMillis = pendingDayMillis
            }
            val localCal = Calendar.getInstance().apply {
              set(Calendar.YEAR, utcDate.get(Calendar.YEAR))
              set(Calendar.MONTH, utcDate.get(Calendar.MONTH))
              set(Calendar.DAY_OF_MONTH, utcDate.get(Calendar.DAY_OF_MONTH))
              set(Calendar.HOUR_OF_DAY, timePickerState.hour)
              set(Calendar.MINUTE, timePickerState.minute)
              set(Calendar.SECOND, 0)
              set(Calendar.MILLISECOND, 0)
            }
            val chosenMillis = localCal.timeInMillis
            if (chosenMillis <= System.currentTimeMillis()) {
              localError = "Expiration time must be in the future."
            } else {
              expiresAtIso = DateUtils.formatIsoUtc(chosenMillis)
              localError = null
            }
            pendingSelectedUtcDayMillis = null
          },
        ) {
          Text("Set")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingSelectedUtcDayMillis = null }) {
          Text(stringResource(R.string.links_cancel))
        }
      },
    )
  }
}
