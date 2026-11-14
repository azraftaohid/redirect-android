package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalSemanticColors

@Composable
fun AccessExpiredBanner(
  onReconnect: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalSemanticColors.current
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("access_expired_banner"),
    color = semantic.warningBg,
    border = BorderStroke(1.dp, semantic.warningBorder),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.WarningAmber,
          contentDescription = null,
          tint = semantic.warningText,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = stringResource(R.string.auth_access_expired_banner),
          style = MaterialTheme.typography.bodySmall,
          color = semantic.warningText,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("dismiss_access_banner_button"),
        ) {
          Text("Dismiss", color = semantic.warningText)
        }
        TextButton(
          onClick = onReconnect,
          modifier = Modifier.testTag("reconnect_access_banner_button"),
        ) {
          Text("Reconnect", color = semantic.warningText)
        }
      }
    }
  }
}

@Composable
fun KvGraceWarningBanner(
  message: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalSemanticColors.current
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("kv_grace_warning_banner"),
    color = semantic.warningBg,
    border = BorderStroke(1.dp, semantic.warningBorder),
    shape = MaterialTheme.shapes.medium,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.WarningAmber,
        contentDescription = null,
        tint = semantic.warningText,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = semantic.warningText,
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("dismiss_kv_warning_button"),
      ) {
        Text("OK", color = semantic.warningText)
      }
    }
  }
}
