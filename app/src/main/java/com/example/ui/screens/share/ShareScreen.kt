package com.example.ui.screens.share

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.util.ValidationUtils
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch

@Composable
fun ShareScreen(
  viewModel: ShareViewModel,
  serverBaseUrl: String,
  snackbarHostState: SnackbarHostState,
  onNavigateToStats: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val semantic = LocalSemanticColors.current

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is ShareEvent.CreatedAndCopied -> {
          clipboard.setText(AnnotatedString(event.shortUrl))
          snackbarHostState.showSnackbar("Copied ${event.shortUrl}")
        }
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = stringResource(R.string.share_title),
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(
        text = stringResource(R.string.share_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    val created = uiState.createdLink
    if (created != null) {
      val shortUrl = ValidationUtils.buildShortUrl(serverBaseUrl, created.slug)
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("share_ready_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, semantic.successBorder),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = null,
              tint = semantic.successText,
            )
            Text(
              text = stringResource(R.string.share_ready_title),
              style = MaterialTheme.typography.titleMedium,
            )
          }

          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = shortUrl,
              style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold,
              ),
              modifier = Modifier.padding(12.dp),
            )
          }

          Text(
            text = created.targetUrl,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = {
                clipboard.setText(AnnotatedString(shortUrl))
                scope.launch { snackbarHostState.showSnackbar("Copied $shortUrl") }
              },
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("share_copy_ready_button"),
            ) {
              Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.share_copy_link))
            }
            OutlinedButton(
              onClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, shortUrl)
                }
                context.startActivity(Intent.createChooser(shareIntent, shortUrl))
              },
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("share_native_button"),
            ) {
              Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.share_native_share))
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            TextButton(
              onClick = { onNavigateToStats(created.id) },
              modifier = Modifier.testTag("share_view_stats_button"),
            ) {
              Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.share_view_analytics))
            }
            TextButton(
              onClick = { viewModel.resetForAnother() },
              modifier = Modifier.testTag("share_another_button"),
            ) {
              Text(stringResource(R.string.share_shorten_another))
            }
          }
        }
      }
      return
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      shape = MaterialTheme.shapes.large,
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (!uiState.error.isNullOrBlank()) {
          Surface(
            color = semantic.dangerBg,
            border = BorderStroke(1.dp, semantic.dangerBorder),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = uiState.error.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = semantic.dangerText,
              modifier = Modifier.padding(10.dp),
            )
          }
        }

        OutlinedTextField(
          value = uiState.url,
          onValueChange = { viewModel.onUrlChange(it) },
          label = { Text(stringResource(R.string.share_url_label)) },
          placeholder = { Text("https://example.com/article") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
          ),
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("share_url_input"),
        )

        uiState.existingMatch?.let { existing ->
          val existingShortUrl = ValidationUtils.buildShortUrl(serverBaseUrl, existing.slug)
          Surface(
            color = semantic.warningBg,
            border = BorderStroke(1.dp, semantic.warningBorder),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("share_existing_callout"),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Info,
                  contentDescription = null,
                  tint = semantic.warningText,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = stringResource(R.string.share_existing_found, existing.slug),
                  style = MaterialTheme.typography.titleSmall,
                  color = semantic.warningText,
                )
              }
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                TextButton(
                  onClick = {
                    clipboard.setText(AnnotatedString(existingShortUrl))
                    scope.launch { snackbarHostState.showSnackbar("Copied $existingShortUrl") }
                  },
                  modifier = Modifier.testTag("share_existing_copy_button"),
                ) {
                  Text("Copy", color = semantic.warningText)
                }
                TextButton(
                  onClick = { onNavigateToStats(existing.id) },
                  modifier = Modifier.testTag("share_existing_stats_button"),
                ) {
                  Text("Stats", color = semantic.warningText)
                }
              }
            }
          }
        }

        OutlinedTextField(
          value = uiState.title,
          onValueChange = { viewModel.onTitleChange(it) },
          label = { Text(stringResource(R.string.share_title_label)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("share_title_input"),
        )

        OutlinedTextField(
          value = uiState.customSlug,
          onValueChange = { viewModel.onSlugChange(it) },
          label = { Text(stringResource(R.string.share_slug_label)) },
          placeholder = { Text("Auto-generated if empty") },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
          trailingIcon = {
            IconButton(
              onClick = { viewModel.generateSlug() },
              modifier = Modifier.testTag("share_generate_slug_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Casino,
                contentDescription = stringResource(R.string.form_slug_generate),
              )
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("share_slug_input"),
        )

        Button(
          onClick = { viewModel.shorten() },
          enabled = !uiState.isSubmitting && uiState.url.isNotBlank(),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("share_submit_button"),
        ) {
          if (uiState.isSubmitting) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          } else {
            Text(stringResource(R.string.share_shorten_button))
          }
        }
      }
    }
  }
}
