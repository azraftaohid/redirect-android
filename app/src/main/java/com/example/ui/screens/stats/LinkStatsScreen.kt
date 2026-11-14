package com.example.ui.screens.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.DimensionRow
import com.example.domain.util.DateUtils
import com.example.domain.util.StatsUtils
import com.example.domain.util.ValidationUtils
import com.example.ui.components.DailyChart
import com.example.ui.components.LinkFormSheet
import com.example.ui.components.QrCodeModal
import com.example.ui.components.StatusBadgesRow
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch

private val STATS_PERIODS = listOf(7, 30, 90, 365)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkStatsScreen(
  viewModel: LinkStatsViewModel,
  serverBaseUrl: String,
  snackbarHostState: SnackbarHostState,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  val semantic = LocalSemanticColors.current

  Column(modifier = modifier.fillMaxSize()) {
    TopAppBar(
      windowInsets = WindowInsets(0, 0, 0, 0),
      title = {
        Text(
          text = uiState.stats?.link?.let { "/${it.slug}" } ?: stringResource(R.string.stats_title),
          style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      navigationIcon = {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("stats_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.stats_back),
          )
        }
      },
      actions = {
        IconButton(
          onClick = { viewModel.refresh() },
          modifier = Modifier.testTag("stats_refresh_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.stats_refresh),
          )
        }
      },
      colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
      ),
    )

    if (uiState.isLoading && uiState.stats == null) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
      return
    }

    val error = uiState.error
    val stats = uiState.stats
    if (error != null && stats == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = semantic.dangerText,
          )
          OutlinedButton(onClick = { viewModel.refresh() }) {
            Text("Retry")
          }
        }
      }
      return
    }

    if (stats == null) return
    val link = stats.link
    val isExpired = DateUtils.isLinkExpired(link.expiresAt)

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Link header card
      item(key = "header") {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          shape = MaterialTheme.shapes.large,
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "/${link.slug}",
                style = MaterialTheme.typography.titleLarge.copy(
                  fontFamily = MonoFontFamily,
                  fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                  .weight(1f)
                  .testTag("stats_header_slug"),
              )
              IconButton(
                onClick = {
                  val shortUrl = ValidationUtils.buildShortUrl(serverBaseUrl, link.slug)
                  clipboard.setText(AnnotatedString(shortUrl))
                  scope.launch { snackbarHostState.showSnackbar("Copied $shortUrl") }
                },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("stats_copy_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.ContentCopy,
                  contentDescription = stringResource(R.string.links_copy_short_url),
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            StatusBadgesRow(
              isExpired = isExpired,
              isActive = link.isActive,
              statusCode = link.statusCode,
              forwardQuery = link.forwardQuery,
            )

            if (!link.title.isNullOrBlank()) {
              Text(
                text = link.title,
                style = MaterialTheme.typography.titleSmall,
              )
            }
            Text(
              text = link.targetUrl,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = { viewModel.openQr() },
                modifier = Modifier
                  .weight(1f)
                  .testTag("stats_qr_button"),
              ) {
                Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.links_action_qr))
              }
              OutlinedButton(
                onClick = { viewModel.openEdit() },
                modifier = Modifier
                  .weight(1f)
                  .testTag("stats_edit_button"),
              ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.links_action_edit))
              }
            }
          }
        }
      }

      // 2x2 KPI Grid
      item(key = "kpi_grid") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            KpiCard(
              label = stringResource(R.string.stats_total_clicks),
              value = link.clickCount.toString(),
              modifier = Modifier.weight(1f),
            )
            KpiCard(
              label = stringResource(R.string.stats_period_clicks),
              value = uiState.periodClicks.toString(),
              modifier = Modifier.weight(1f),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            KpiCard(
              label = stringResource(R.string.stats_peak_day),
              value = uiState.peakDay?.let { "${it.day} (${it.clicks})" } ?: "—",
              smallValue = true,
              modifier = Modifier.weight(1f),
            )
            KpiCard(
              label = stringResource(R.string.stats_last_clicked),
              value = link.lastClickedAt?.let { DateUtils.formatLocalDateTime(it) }
                ?: stringResource(R.string.stats_never),
              smallValue = true,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      // Period selector + Daily chart
      item(key = "daily_chart") {
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
                text = stringResource(R.string.stats_daily_chart_title),
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "${stats.range.from} → ${stats.range.to}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              STATS_PERIODS.forEach { days ->
                FilterChip(
                  selected = uiState.selectedDays == days,
                  onClick = { viewModel.selectPeriod(days) },
                  label = {
                    Text(
                      text = "${days}d",
                      style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    )
                  },
                  modifier = Modifier.testTag("stats_period_${days}d"),
                )
              }
            }

            DailyChart(points = uiState.zeroFilledDaily)
          }
        }
      }

      // Optional Top Countries & Top Referrers breakdowns
      val countries = stats.dimensions.country
      val referrers = stats.dimensions.referrer
      if (countries != null) {
        item(key = "countries") {
          DimensionBreakdownCard(
            title = stringResource(R.string.stats_top_countries),
            rows = countries,
            labelFormatter = StatsUtils::formatCountryDisplay,
          )
        }
      }
      if (referrers != null) {
        item(key = "referrers") {
          DimensionBreakdownCard(
            title = stringResource(R.string.stats_top_referrers),
            rows = referrers,
            labelFormatter = StatsUtils::formatReferrerDisplay,
          )
        }
      }
      if (countries == null && referrers == null) {
        item(key = "dimensions_note") {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = stringResource(R.string.stats_dimensions_disabled),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      }
    }
  }

  val modalStats = uiState.stats
  if (uiState.isEditOpen && modalStats != null) {
    LinkFormSheet(
      existingLink = modalStats.link,
      serverBaseUrl = serverBaseUrl,
      isSubmitting = uiState.isEditSubmitting,
      serverError = uiState.editError,
      onDismiss = { viewModel.closeEdit() },
      onCreate = {},
      onPatch = { id, patch -> viewModel.saveEdit(id, patch) },
    )
  }

  if (uiState.showQrModal && modalStats != null) {
    QrCodeModal(
      link = modalStats.link,
      serverBaseUrl = serverBaseUrl,
      onDismiss = { viewModel.closeQr() },
    )
  }
}

@Composable
private fun KpiCard(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  smallValue: Boolean = false,
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = (if (smallValue) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall)
          .copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun DimensionBreakdownCard(
  title: String,
  rows: List<DimensionRow>,
  labelFormatter: (String) -> String,
) {
  val total = rows.sumOf { it.clicks }.coerceAtLeast(1)
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = MaterialTheme.shapes.large,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      if (rows.isEmpty()) {
        Text(
          text = "No dimension data recorded in this period.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        rows.forEach { row ->
          val fraction = (row.clicks.toFloat() / total.toFloat()).coerceIn(0f, 1f)
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = labelFormatter(row.value),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = "${row.clicks}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
              )
            }
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth(fraction)
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp))
                  .background(MaterialTheme.colorScheme.primary),
              )
            }
          }
        }
      }
    }
  }
}
