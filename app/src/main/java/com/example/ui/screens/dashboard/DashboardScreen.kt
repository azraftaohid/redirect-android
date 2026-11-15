package com.example.ui.screens.dashboard

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.LinkSortOption
import com.example.domain.model.StatusFilter
import com.example.domain.util.ValidationUtils
import com.example.ui.components.LinkFormSheet
import com.example.ui.components.LinkRowCard
import com.example.ui.components.QrCodeModal
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  viewModel: DashboardViewModel,
  serverBaseUrl: String,
  snackbarHostState: SnackbarHostState,
  onNavigateToStats: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  val semantic = LocalSemanticColors.current

  var statusMenuExpanded by remember { mutableStateOf(false) }
  var sortMenuExpanded by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    viewModel.loadInitialIfNeeded()
  }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is DashboardEvent.LinkShortenedAndCopied -> {
          clipboard.setText(AnnotatedString(event.shortUrl))
          val res = snackbarHostState.showSnackbar(
            message = "Copied ${event.shortUrl}",
            actionLabel = "QR",
            duration = SnackbarDuration.Long,
          )
          if (res == SnackbarResult.ActionPerformed) {
            viewModel.openQrModal(event.link)
          }
        }
        is DashboardEvent.ShowMessage -> {
          snackbarHostState.showSnackbar(event.text)
        }
      }
    }
  }

  PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = { viewModel.refresh() },
    modifier = modifier.fillMaxSize(),
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .imePadding()
        .testTag("dashboard_links_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Quick-Shorten card
      item(key = "quick_shorten") {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          shape = MaterialTheme.shapes.large,
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedTextField(
              value = uiState.quickUrl,
              onValueChange = { viewModel.onQuickUrlChange(it) },
              placeholder = { Text(stringResource(R.string.links_quick_placeholder)) },
              singleLine = true,
              isError = uiState.quickError != null,
              supportingText = uiState.quickError?.let { err ->
                { Text(err, color = semantic.dangerText) }
              },
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
              ),
              keyboardActions = KeyboardActions(onDone = { viewModel.quickShorten() }),
              textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_shorten_input"),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = { viewModel.quickShorten() },
                enabled = !uiState.isQuickShortening && uiState.quickUrl.isNotBlank(),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("quick_shorten_button"),
              ) {
                if (uiState.isQuickShortening) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                  )
                } else {
                  Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(stringResource(R.string.links_shorten))
                }
              }

              OutlinedButton(
                onClick = { viewModel.openCreateForm() },
                modifier = Modifier
                  .height(48.dp)
                  .testTag("advanced_create_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Tune,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.links_advanced))
              }
            }
          }
        }
      }

      // Search + Filters bar
      item(key = "filter_bar") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            placeholder = { Text(stringResource(R.string.links_search_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearchNow() }),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
              if (uiState.searchQuery.isNotEmpty()) {
                IconButton(
                  onClick = { viewModel.clearSearch() },
                  modifier = Modifier.testTag("search_clear_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.links_clear_search),
                  )
                }
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("links_search_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box {
              FilterChip(
                selected = uiState.statusFilter != StatusFilter.ALL,
                onClick = { statusMenuExpanded = true },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(
                    when (uiState.statusFilter) {
                      StatusFilter.ALL -> stringResource(R.string.links_status_all)
                      StatusFilter.ACTIVE_ONLY -> stringResource(R.string.links_status_active)
                      StatusFilter.INACTIVE_ONLY -> stringResource(R.string.links_status_inactive)
                    },
                  )
                },
                modifier = Modifier.testTag("status_filter_chip"),
              )
              DropdownMenu(
                expanded = statusMenuExpanded,
                onDismissRequest = { statusMenuExpanded = false },
              ) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.links_status_all)) },
                  onClick = {
                    statusMenuExpanded = false
                    viewModel.onStatusFilterChange(StatusFilter.ALL)
                  },
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.links_status_active)) },
                  onClick = {
                    statusMenuExpanded = false
                    viewModel.onStatusFilterChange(StatusFilter.ACTIVE_ONLY)
                  },
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.links_status_inactive)) },
                  onClick = {
                    statusMenuExpanded = false
                    viewModel.onStatusFilterChange(StatusFilter.INACTIVE_ONLY)
                  },
                )
              }
            }

            Box {
              FilterChip(
                selected = uiState.sortOption != LinkSortOption.NEWEST,
                onClick = { sortMenuExpanded = true },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = {
                  Text(
                    when (uiState.sortOption) {
                      LinkSortOption.NEWEST -> stringResource(R.string.links_sort_newest)
                      LinkSortOption.OLDEST -> stringResource(R.string.links_sort_oldest)
                      LinkSortOption.RECENTLY_UPDATED -> stringResource(R.string.links_sort_updated)
                      LinkSortOption.MOST_CLICKS -> stringResource(R.string.links_sort_clicks)
                      LinkSortOption.SLUG_ASC -> stringResource(R.string.links_sort_slug)
                    },
                  )
                },
                modifier = Modifier.testTag("sort_filter_chip"),
              )
              DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
              ) {
                LinkSortOption.entries.forEach { option ->
                  DropdownMenuItem(
                    text = {
                      Text(
                        when (option) {
                          LinkSortOption.NEWEST -> stringResource(R.string.links_sort_newest)
                          LinkSortOption.OLDEST -> stringResource(R.string.links_sort_oldest)
                          LinkSortOption.RECENTLY_UPDATED -> stringResource(R.string.links_sort_updated)
                          LinkSortOption.MOST_CLICKS -> stringResource(R.string.links_sort_clicks)
                          LinkSortOption.SLUG_ASC -> stringResource(R.string.links_sort_slug)
                        },
                      )
                    },
                    onClick = {
                      sortMenuExpanded = false
                      viewModel.onSortOptionChange(option)
                    },
                  )
                }
              }
            }

            Spacer(Modifier.weight(1f))
            Text(
              text = "${uiState.total} total",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (uiState.activeTag != null) {
            InputChip(
              selected = true,
              onClick = { viewModel.onTagFilterSelected(null) },
              label = {
                Text(
                  text = "#${uiState.activeTag}",
                  style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                )
              },
              trailingIcon = {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = "Clear tag filter",
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier = Modifier.testTag("active_tag_filter_chip"),
            )
          }
        }
      }

      if (!uiState.error.isNullOrBlank()) {
        item(key = "error_banner") {
          Surface(
            color = semantic.dangerBg,
            border = BorderStroke(1.dp, semantic.dangerBorder),
            shape = MaterialTheme.shapes.medium,
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = uiState.error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = semantic.dangerText,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = { viewModel.refresh() }) {
                Text("Retry", color = semantic.dangerText)
              }
            }
          }
        }
      }

      if (uiState.isLoading) {
        item(key = "loading") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
      } else if (uiState.links.isEmpty()) {
        item(key = "empty") {
          val hasActiveFilters = uiState.searchQuery.isNotBlank() ||
            uiState.statusFilter != StatusFilter.ALL ||
            uiState.activeTag != null
          Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.LinkOff,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = if (hasActiveFilters) {
                  stringResource(R.string.links_no_results)
                } else {
                  stringResource(R.string.links_empty_title)
                },
                style = MaterialTheme.typography.titleMedium,
              )
              if (!hasActiveFilters) {
                Text(
                  text = stringResource(R.string.links_empty_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                OutlinedButton(onClick = { viewModel.resetAllFilters() }) {
                  Text("Reset Filters")
                }
              }
            }
          }
        }
      } else {
        items(
          items = uiState.links,
          key = { it.id },
        ) { link ->
          LinkRowCard(
            link = link,
            isToggling = link.id in uiState.togglingIds,
            onCopyShortUrl = {
              val shortUrl = ValidationUtils.buildShortUrl(serverBaseUrl, link.slug)
              clipboard.setText(AnnotatedString(shortUrl))
              scope.launch { snackbarHostState.showSnackbar("Copied $shortUrl") }
            },
            onToggleActive = { next -> viewModel.toggleLinkActive(link, next) },
            onTagClick = { tag -> viewModel.onTagFilterSelected(tag) },
            onViewStats = { onNavigateToStats(link.id) },
            onShowQr = { viewModel.openQrModal(link) },
            onEdit = { viewModel.openEditForm(link) },
            onDelete = { viewModel.requestDelete(link) },
          )
        }

        if (uiState.offset > 0 || uiState.hasMore) {
          item(key = "pagination") {
            val currentPage = (uiState.offset / ValidationUtils.PAGE_SIZE) + 1
            val totalPages = maxOf(1, (uiState.total + ValidationUtils.PAGE_SIZE - 1) / ValidationUtils.PAGE_SIZE)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedButton(
                onClick = { viewModel.previousPage() },
                enabled = uiState.offset > 0,
                modifier = Modifier.testTag("pagination_prev_button"),
              ) {
                Text(stringResource(R.string.links_previous))
              }
              Text(
                text = "Page $currentPage of $totalPages",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              OutlinedButton(
                onClick = { viewModel.nextPage() },
                enabled = uiState.hasMore,
                modifier = Modifier.testTag("pagination_next_button"),
              ) {
                Text(stringResource(R.string.links_next))
              }
            }
          }
        }
      }
    }
  }

  if (uiState.isFormOpen) {
    LinkFormSheet(
      existingLink = uiState.formTargetLink,
      initialTargetUrl = uiState.formInitialUrl,
      serverBaseUrl = serverBaseUrl,
      isSubmitting = uiState.isFormSubmitting,
      serverError = uiState.formError,
      onDismiss = { viewModel.closeForm() },
      onCreate = { req -> viewModel.createFromForm(req) },
      onPatch = { id, patch -> viewModel.patchFromForm(id, patch) },
    )
  }

  uiState.qrTargetLink?.let { qrLink ->
    QrCodeModal(
      link = qrLink,
      serverBaseUrl = serverBaseUrl,
      onDismiss = { viewModel.closeQrModal() },
    )
  }

  uiState.deleteTargetLink?.let { delLink ->
    AlertDialog(
      onDismissRequest = { if (!uiState.isDeleting) viewModel.cancelDelete() },
      title = { Text(stringResource(R.string.links_delete_title)) },
      text = { Text(stringResource(R.string.links_delete_message, delLink.slug)) },
      confirmButton = {
        Button(
          onClick = { viewModel.confirmDelete() },
          enabled = !uiState.isDeleting,
          colors = ButtonDefaults.buttonColors(
            containerColor = semantic.dangerText,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          modifier = Modifier.testTag("confirm_delete_link_button"),
        ) {
          Text(stringResource(R.string.links_confirm_delete))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { viewModel.cancelDelete() },
          enabled = !uiState.isDeleting,
        ) {
          Text(stringResource(R.string.links_cancel))
        }
      },
    )
  }
}
