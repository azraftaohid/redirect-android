package com.example.ui.screens.auth

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.RedirectRepository
import com.example.data.repository.SessionState
import com.example.ui.components.AccessExpiredBanner
import com.example.ui.components.AppLogoMark
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch

private const val WRANGLER_KV_CMD =
  "pnpm --filter @redirect/api exec wrangler kv key get initial_token --binding AUTH_TOKENS --local"

@Composable
fun AuthScreen(
  session: SessionState,
  repository: RedirectRepository,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  val semantic = LocalSemanticColors.current

  var baseUrl by remember(session.config.baseUrl) { mutableStateOf(session.config.baseUrl) }
  var apiToken by remember(session.config.apiToken) { mutableStateOf(session.config.apiToken) }
  var showToken by remember { mutableStateOf(false) }
  var zeroTrustExpanded by remember(session.config.hasZeroTrust) {
    mutableStateOf(session.config.hasZeroTrust)
  }
  var cfClientId by remember(session.config.cfAccessClientId) {
    mutableStateOf(session.config.cfAccessClientId)
  }
  var cfClientSecret by remember(session.config.cfAccessClientSecret) {
    mutableStateOf(session.config.cfAccessClientSecret)
  }
  var kvHelpExpanded by remember { mutableStateOf(false) }
  var isPinging by remember { mutableStateOf(false) }
  var pingStatusMessage by remember { mutableStateOf<String?>(null) }

  val submitConnect: () -> Unit = {
    scope.launch {
      repository.connect(baseUrl, apiToken, cfClientId, cfClientSecret)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 520.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      AppLogoMark(size = 56.dp)

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = stringResource(R.string.auth_title),
          style = MaterialTheme.typography.headlineMedium,
          textAlign = TextAlign.Center,
        )
        Text(
          text = stringResource(R.string.auth_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }

      if (session.accessExpired) {
        AccessExpiredBanner(
          onReconnect = { zeroTrustExpanded = true },
          onDismiss = { repository.dismissAccessBanner() },
        )
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          if (!session.authError.isNullOrBlank()) {
            Surface(
              color = semantic.dangerBg,
              border = BorderStroke(1.dp, semantic.dangerBorder),
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = session.authError,
                style = MaterialTheme.typography.bodySmall,
                color = semantic.dangerText,
                modifier = Modifier.padding(10.dp),
              )
            }
          }

          OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(stringResource(R.string.auth_server_url_label)) },
            placeholder = { Text(stringResource(R.string.auth_server_url_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Uri,
              imeAction = ImeAction.Next,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("auth_server_url_input"),
          )

          OutlinedTextField(
            value = apiToken,
            onValueChange = { apiToken = it },
            label = { Text(stringResource(R.string.auth_token_label)) },
            placeholder = { Text(stringResource(R.string.auth_token_placeholder)) },
            singleLine = true,
            visualTransformation = if (showToken) {
              VisualTransformation.None
            } else {
              PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Password,
              imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submitConnect() }),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
            trailingIcon = {
              IconButton(
                onClick = { showToken = !showToken },
                modifier = Modifier.testTag("auth_toggle_token_visibility"),
              ) {
                Icon(
                  imageVector = if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription = stringResource(
                    if (showToken) R.string.auth_hide_token else R.string.auth_show_token,
                  ),
                )
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("auth_token_input"),
          )

          // Expandable Cloudflare Zero Trust section
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.medium,
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              TextButton(
                onClick = { zeroTrustExpanded = !zeroTrustExpanded },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("auth_zero_trust_toggle"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Security,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = stringResource(R.string.auth_zero_trust_title),
                  style = MaterialTheme.typography.labelLarge,
                  modifier = Modifier.weight(1f),
                )
                Icon(
                  imageVector = if (zeroTrustExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                  contentDescription = null,
                )
              }

              AnimatedVisibility(visible = zeroTrustExpanded) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  OutlinedTextField(
                    value = cfClientId,
                    onValueChange = { cfClientId = it },
                    label = { Text(stringResource(R.string.auth_cf_client_id)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("auth_cf_client_id_input"),
                  )
                  OutlinedTextField(
                    value = cfClientSecret,
                    onValueChange = { cfClientSecret = it },
                    label = { Text(stringResource(R.string.auth_cf_client_secret)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("auth_cf_client_secret_input"),
                  )
                }
              }
            }
          }

          Button(
            onClick = submitConnect,
            enabled = !session.isConnecting,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("auth_connect_button"),
          ) {
            if (session.isConnecting) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.auth_connecting))
            } else {
              Text(stringResource(R.string.auth_connect))
            }
          }
        }
      }

      // First-time KV initial_token bootstrap card
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          TextButton(
            onClick = { kvHelpExpanded = !kvHelpExpanded },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("auth_kv_help_toggle"),
          ) {
            Icon(
              imageVector = Icons.Filled.Key,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.auth_kv_help_title),
              style = MaterialTheme.typography.labelLarge,
              modifier = Modifier.weight(1f),
            )
            Icon(
              imageVector = if (kvHelpExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
              contentDescription = null,
            )
          }

          AnimatedVisibility(visible = kvHelpExpanded) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = stringResource(R.string.auth_kv_help_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = WRANGLER_KV_CMD,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    modifier = Modifier.weight(1f),
                  )
                  IconButton(
                    onClick = {
                      clipboard.setText(AnnotatedString(WRANGLER_KV_CMD))
                      Toast.makeText(context, "Wrangler command copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("auth_copy_wrangler_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.ContentCopy,
                      contentDescription = stringResource(R.string.auth_copy_wrangler_cmd),
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
              }

              OutlinedButton(
                onClick = {
                  scope.launch {
                    isPinging = true
                    pingStatusMessage = null
                    val res = repository.pingForInitialToken(baseUrl, cfClientId, cfClientSecret)
                    isPinging = false
                    pingStatusMessage = res.fold(
                      onSuccess = { context.getString(R.string.auth_ping_sent) },
                      onFailure = { it.message ?: "Ping failed." },
                    )
                  }
                },
                enabled = !isPinging && baseUrl.isNotBlank(),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("auth_ping_bootstrap_button"),
              ) {
                if (isPinging) {
                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                  Icon(
                    imageVector = Icons.Filled.NetworkPing,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.auth_ping_server))
              }

              if (!pingStatusMessage.isNullOrBlank()) {
                Text(
                  text = pingStatusMessage.orEmpty(),
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}
