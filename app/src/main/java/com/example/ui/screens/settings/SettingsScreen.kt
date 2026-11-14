package com.example.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.repository.RedirectRepository
import com.example.data.repository.SessionState
import com.example.domain.util.DateUtils
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  session: SessionState,
  repository: RedirectRepository,
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val semantic = LocalSemanticColors.current

  var revealStoredToken by remember { mutableStateOf(false) }
  var showZeroTrustEditDialog by remember { mutableStateOf(false) }

  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json"),
  ) { uri ->
    if (uri != null) {
      scope.launch {
        val result = viewModel.buildExportJsonString()
        result.fold(
          onSuccess = { jsonText ->
            val ok = withContext(Dispatchers.IO) {
              runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                  out.write(jsonText.toByteArray(Charsets.UTF_8))
                }
              }.isSuccess
            }
            snackbarHostState.showSnackbar(
              if (ok) "Links exported to JSON" else "Failed to write export file",
            )
          },
          onFailure = { err ->
            snackbarHostState.showSnackbar(err.message ?: "Export failed")
          },
        )
      }
    }
  }

  LaunchedEffect(Unit) {
    viewModel.loadTokensIfNeeded()
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Current Session card
    item(key = "session") {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = stringResource(R.string.settings_session_title),
            style = MaterialTheme.typography.titleLarge,
          )

          SessionDetailRow(
            label = stringResource(R.string.settings_server_url),
            value = session.config.baseUrl,
            mono = true,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.settings_zero_trust),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = stringResource(
                  if (session.config.hasZeroTrust) {
                    R.string.settings_zero_trust_enabled
                  } else {
                    R.string.settings_zero_trust_disabled
                  },
                ),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
            TextButton(
              onClick = { showZeroTrustEditDialog = true },
              modifier = Modifier.testTag("settings_edit_zero_trust_button"),
            ) {
              Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Configure")
            }
          }

          val principalLabel = when (session.principal?.kind) {
            "env" -> "Master Secret (API_KEY)"
            "initial" -> "Initial Token"
            else -> session.principal?.name ?: "Verified Token"
          }
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = stringResource(R.string.settings_principal),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusPill(
              label = principalLabel,
              bg = semantic.successBg,
              fg = semantic.successText,
              border = semantic.successBorder,
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = stringResource(R.string.settings_stored_token),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainer,
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                val rawTok = session.config.apiToken
                val masked = if (rawTok.length <= 8) "••••••••" else "${rawTok.take(4)}••••••••${rawTok.takeLast(4)}"
                Text(
                  text = if (revealStoredToken) rawTok else masked,
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                  modifier = Modifier.weight(1f),
                )
                IconButton(
                  onClick = { revealStoredToken = !revealStoredToken },
                  modifier = Modifier.testTag("settings_toggle_token_visibility"),
                ) {
                  Icon(
                    imageVector = if (revealStoredToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle token visibility",
                    modifier = Modifier.size(18.dp),
                  )
                }
                IconButton(
                  onClick = {
                    clipboard.setText(AnnotatedString(rawTok))
                    Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
                  },
                  modifier = Modifier.testTag("settings_copy_stored_token"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy token",
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }

          OutlinedButton(
            onClick = { repository.logout() },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = semantic.dangerText),
            border = BorderStroke(1.dp, semantic.dangerBorder),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("settings_sign_out_button"),
          ) {
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_sign_out))
          }
        }
      }
    }

    // Active API Tokens card
    item(key = "tokens") {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.settings_tokens_title),
              style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = { viewModel.refreshTokens() }) {
              Text("Refresh")
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = { viewModel.openCreateToken() },
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("settings_create_token_button"),
            ) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.settings_create_token), maxLines = 1)
            }
            OutlinedButton(
              onClick = { viewModel.requestRotateInitial() },
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("settings_rotate_initial_button"),
            ) {
              Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.settings_rotate_initial), maxLines = 1)
            }
          }

          if (!uiState.tokensError.isNullOrBlank()) {
            Surface(
              color = semantic.dangerBg,
              border = BorderStroke(1.dp, semantic.dangerBorder),
              shape = MaterialTheme.shapes.small,
            ) {
              Text(
                text = uiState.tokensError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = semantic.dangerText,
                modifier = Modifier.padding(10.dp),
              )
            }
          }

          if (uiState.isLoadingTokens) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
          } else if (uiState.tokens.isEmpty()) {
            Text(
              text = "No managed KV tokens found.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            uiState.tokens.forEachIndexed { idx, tok ->
              if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = tok.name,
                      style = MaterialTheme.typography.titleSmall,
                    )
                    if (tok.isInitial == true || tok.id == "initial") {
                      StatusPill(
                        label = "Initial",
                        bg = semantic.warningBg,
                        fg = semantic.warningText,
                        border = semantic.warningBorder,
                      )
                    }
                  }
                  Text(
                    text = "${tok.preview} · Created ${DateUtils.formatLocalDate(tok.createdAt)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = tok.expiresAt?.let { "Expires ${DateUtils.formatLocalDateTime(it)}" }
                      ?: "No expiration",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                if (tok.id != "initial" && tok.isInitial != true) {
                  IconButton(
                    onClick = { viewModel.requestRevoke(tok) },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("revoke_token_${tok.id}"),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Delete,
                      contentDescription = stringResource(R.string.settings_revoke),
                      tint = semantic.dangerText,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // Data Portability card
    item(key = "portability") {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = stringResource(R.string.settings_data_title),
            style = MaterialTheme.typography.titleLarge,
          )
          OutlinedButton(
            onClick = {
              val today = DateUtils.formatUtcDay(System.currentTimeMillis())
              exportLauncher.launch("redirect-links-$today.json")
            },
            enabled = !uiState.isExporting,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("settings_export_button"),
          ) {
            if (uiState.isExporting) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
              Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_export_json))
          }

          if (!uiState.exportStatusMessage.isNullOrBlank()) {
            Text(
              text = uiState.exportStatusMessage.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          OutlinedButton(
            onClick = { viewModel.openImportDialog() },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("settings_import_button"),
          ) {
            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_import_json))
          }
        }
      }
    }
  }

  if (uiState.isCreateTokenOpen) {
    CreateTokenDialog(
      isSubmitting = uiState.isCreatingToken,
      error = uiState.createTokenError,
      onDismiss = { viewModel.closeCreateToken() },
      onCreate = { name, exp -> viewModel.createToken(name, exp) },
    )
  }

  uiState.revealedToken?.let { revealed ->
    AlertDialog(
      onDismissRequest = { viewModel.dismissRevealedToken() },
      title = { Text(revealed.title) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Copy this token now. For security, the full token is only shown once.",
            style = MaterialTheme.typography.bodySmall,
            color = semantic.warningText,
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = revealed.token,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
              modifier = Modifier
                .padding(12.dp)
                .testTag("revealed_token_value"),
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            clipboard.setText(AnnotatedString(revealed.token))
            Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.testTag("copy_revealed_token_button"),
        ) {
          Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Copy Token")
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissRevealedToken() }) {
          Text("Done")
        }
      },
    )
  }

  if (uiState.showRotateConfirm) {
    AlertDialog(
      onDismissRequest = { if (!uiState.isRotating) viewModel.cancelRotateInitial() },
      title = { Text("Rotate Initial Token?") },
      text = {
        Text("This generates a new initial token, invalidates the previous initial token, and updates your saved session token automatically.")
      },
      confirmButton = {
        Button(
          onClick = { viewModel.confirmRotateInitial() },
          enabled = !uiState.isRotating,
          modifier = Modifier.testTag("confirm_rotate_initial_button"),
        ) {
          Text("Rotate")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { viewModel.cancelRotateInitial() },
          enabled = !uiState.isRotating,
        ) {
          Text(stringResource(R.string.links_cancel))
        }
      },
    )
  }

  uiState.revokeTarget?.let { target ->
    val isCurrentSessionToken = target.id == session.principal?.id
    AlertDialog(
      onDismissRequest = { if (!uiState.isRevoking) viewModel.cancelRevoke() },
      title = { Text("Revoke Token \"${target.name}\"?") },
      text = {
        Text(
          if (isCurrentSessionToken) {
            "Warning: This is your currently active session token. Revoking it will immediately sign you out."
          } else {
            "Any clients or scripts using this token (${target.preview}) will lose API access."
          },
        )
      },
      confirmButton = {
        Button(
          onClick = { viewModel.confirmRevoke(session.principal?.id) },
          enabled = !uiState.isRevoking,
          colors = ButtonDefaults.buttonColors(
            containerColor = semantic.dangerText,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          modifier = Modifier.testTag("confirm_revoke_token_button"),
        ) {
          Text(stringResource(R.string.settings_revoke))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { viewModel.cancelRevoke() },
          enabled = !uiState.isRevoking,
        ) {
          Text(stringResource(R.string.links_cancel))
        }
      },
    )
  }

  if (uiState.importState.isOpen) {
    ImportLinksDialog(
      state = uiState.importState,
      onDismiss = { viewModel.closeImportDialog() },
      onConflictModeChange = { viewModel.setImportConflictMode(it) },
      onFileLoaded = { name, content -> viewModel.loadImportJson(name, content) },
      onStartImport = { viewModel.startImport() },
    )
  }

  if (showZeroTrustEditDialog) {
    var cfId by remember(session.config.cfAccessClientId) {
      mutableStateOf(session.config.cfAccessClientId)
    }
    var cfSecret by remember(session.config.cfAccessClientSecret) {
      mutableStateOf(session.config.cfAccessClientSecret)
    }
    AlertDialog(
      onDismissRequest = { showZeroTrustEditDialog = false },
      title = { Text(stringResource(R.string.auth_zero_trust_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = cfId,
            onValueChange = { cfId = it },
            label = { Text(stringResource(R.string.auth_cf_client_id)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = cfSecret,
            onValueChange = { cfSecret = it },
            label = { Text(stringResource(R.string.auth_cf_client_secret)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            repository.updateZeroTrustHeaders(cfId, cfSecret)
            showZeroTrustEditDialog = false
          },
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showZeroTrustEditDialog = false }) {
          Text(stringResource(R.string.links_cancel))
        }
      },
    )
  }
}

@Composable
private fun SessionDetailRow(
  label: String,
  value: String,
  mono: Boolean = false,
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.let {
        if (mono) it.copy(fontFamily = MonoFontFamily) else it
      },
    )
  }
}

@Composable
private fun CreateTokenDialog(
  isSubmitting: Boolean,
  error: String?,
  onDismiss: () -> Unit,
  onCreate: (name: String, expiresAtIso: String?) -> Unit,
) {
  val semantic = LocalSemanticColors.current
  var name by remember { mutableStateOf("") }
  var expiryOption by remember { mutableStateOf<Int?>(null) } // null = never, else days

  AlertDialog(
    onDismissRequest = { if (!isSubmitting) onDismiss() },
    title = { Text(stringResource(R.string.settings_create_token)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!error.isNullOrBlank()) {
          Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = semantic.dangerText,
          )
        }
        OutlinedTextField(
          value = name,
          onValueChange = { if (it.length <= 64) name = it },
          label = { Text("Token Name") },
          placeholder = { Text("e.g. Android Pixel") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("create_token_name_input"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Expiration", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(null to "Never", 7 to "7d", 30 to "30d", 90 to "90d").forEach { (days, label) ->
              FilterChip(
                selected = expiryOption == days,
                onClick = { expiryOption = days },
                label = { Text(label) },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val exp = expiryOption?.let { days ->
            DateUtils.formatIsoUtc(System.currentTimeMillis() + days * 86_400_000L)
          }
          onCreate(name, exp)
        },
        enabled = !isSubmitting && name.isNotBlank(),
        modifier = Modifier.testTag("confirm_create_token_button"),
      ) {
        Text("Create")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isSubmitting) {
        Text(stringResource(R.string.links_cancel))
      }
    },
  )
}
