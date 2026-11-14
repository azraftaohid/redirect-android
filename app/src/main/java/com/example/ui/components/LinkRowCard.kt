package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.LinkRecord
import com.example.domain.util.DateUtils
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkRowCard(
  link: LinkRecord,
  isToggling: Boolean,
  onCopyShortUrl: () -> Unit,
  onToggleActive: (Boolean) -> Unit,
  onTagClick: (String) -> Unit,
  onViewStats: () -> Unit,
  onShowQr: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalSemanticColors.current
  val isExpired = remember(link.expiresAt) { DateUtils.isLinkExpired(link.expiresAt) }
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
    onClick = onViewStats,
    modifier = modifier
      .fillMaxWidth()
      .testTag("link_card_${link.slug}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Top row: full-width /slug + copy button for maximum slug visibility
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "/${link.slug}",
          style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.SemiBold,
          ),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(1f)
            .testTag("link_slug_${link.slug}"),
        )
        IconButton(
          onClick = onCopyShortUrl,
          modifier = Modifier
            .size(48.dp)
            .testTag("copy_slug_${link.slug}"),
        ) {
          Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = stringResource(R.string.links_copy_short_url),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Status, redirect code, and +query badges on dedicated row
      StatusBadgesRow(
        isExpired = isExpired,
        isActive = link.isActive,
        statusCode = link.statusCode,
        forwardQuery = link.forwardQuery,
      )

      // Title + targetUrl
      if (!link.title.isNullOrBlank()) {
        Text(
          text = link.title,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = link.targetUrl,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      // Tags
      if (link.tags.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          link.tags.forEach { tag ->
            Surface(
              color = semantic.neutralBg,
              border = BorderStroke(1.dp, semantic.neutralBorder),
              shape = MaterialTheme.shapes.extraSmall,
              modifier = Modifier
                .clickable { onTagClick(tag) }
                .testTag("tag_chip_${link.slug}_$tag"),
            ) {
              Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = semantic.neutralText,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }
      }

      // Expiration meta
      if (!link.expiresAt.isNullOrBlank()) {
        val formatted = DateUtils.formatLocalDateTime(link.expiresAt)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isExpired) semantic.dangerText else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = if (isExpired) "Expired $formatted" else "Expires $formatted",
            style = MaterialTheme.typography.bodySmall,
            color = if (isExpired) semantic.dangerText else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Bottom action bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Switch(
          checked = link.isActive,
          onCheckedChange = onToggleActive,
          enabled = !isExpired && !isToggling,
          modifier = Modifier.testTag("link_toggle_${link.slug}"),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = when {
            isExpired -> "Expired"
            link.isActive -> "Active"
            else -> "Paused"
          },
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
          onClick = onViewStats,
          modifier = Modifier.testTag("link_stats_${link.slug}"),
        ) {
          Icon(
            imageVector = Icons.Filled.BarChart,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = "${link.clickCount} clicks",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
          )
        }
        Box {
          IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier
              .size(48.dp)
              .testTag("link_menu_${link.slug}"),
          ) {
            Icon(
              imageVector = Icons.Filled.MoreVert,
              contentDescription = stringResource(R.string.links_more_actions),
            )
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
          ) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.links_action_stats)) },
              leadingIcon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onViewStats()
              },
              modifier = Modifier.testTag("menu_stats_${link.slug}"),
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.links_action_qr)) },
              leadingIcon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onShowQr()
              },
              modifier = Modifier.testTag("menu_qr_${link.slug}"),
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.links_action_edit)) },
              leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onEdit()
              },
              modifier = Modifier.testTag("menu_edit_${link.slug}"),
            )
            DropdownMenuItem(
              text = {
                Text(
                  text = stringResource(R.string.links_action_delete),
                  color = semantic.dangerText,
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Delete,
                  contentDescription = null,
                  tint = semantic.dangerText,
                )
              },
              onClick = {
                menuExpanded = false
                onDelete()
              },
              modifier = Modifier.testTag("menu_delete_${link.slug}"),
            )
          }
        }
      }
    }
  }
}

@Composable
fun StatusBadgesRow(
  isExpired: Boolean,
  isActive: Boolean,
  statusCode: Int,
  forwardQuery: Boolean,
) {
  val semantic = LocalSemanticColors.current
  val isPermanent = statusCode == 301 || statusCode == 308

  Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Primary status badge
    val (bg, fg, border, label) = when {
      isExpired -> Quad(semantic.dangerBg, semantic.dangerText, semantic.dangerBorder, "Expired")
      isActive -> Quad(semantic.successBg, semantic.successText, semantic.successBorder, "Active")
      else -> Quad(semantic.neutralBg, semantic.neutralText, semantic.neutralBorder, "Inactive")
    }
    StatusPill(label = label, bg = bg, fg = fg, border = border)

    // HTTP redirect code badge
    StatusPill(
      label = statusCode.toString(),
      bg = if (isPermanent) semantic.warningBg else semantic.neutralBg,
      fg = if (isPermanent) semantic.warningText else semantic.neutralText,
      border = if (isPermanent) semantic.warningBorder else semantic.neutralBorder,
      mono = true,
    )

    if (forwardQuery) {
      StatusPill(
        label = "+query",
        bg = semantic.neutralBg,
        fg = semantic.neutralText,
        border = semantic.neutralBorder,
        mono = true,
      )
    }
  }
}

@Composable
fun StatusPill(
  label: String,
  bg: androidx.compose.ui.graphics.Color,
  fg: androidx.compose.ui.graphics.Color,
  border: androidx.compose.ui.graphics.Color,
  mono: Boolean = false,
) {
  Surface(
    color = bg,
    border = BorderStroke(1.dp, border),
    shape = MaterialTheme.shapes.extraSmall,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.let {
        if (mono) it.copy(fontFamily = MonoFontFamily) else it
      },
      color = fg,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
