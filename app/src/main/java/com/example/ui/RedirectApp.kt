package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.repository.RedirectRepository
import com.example.domain.util.SharedPayload
import com.example.domain.util.ValidationUtils
import com.example.ui.components.AccessExpiredBanner
import com.example.ui.components.AppBrandHeader
import com.example.ui.components.KvGraceWarningBanner
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.share.ShareScreen
import com.example.ui.screens.share.ShareViewModel
import com.example.ui.screens.stats.LinkStatsScreen
import com.example.ui.screens.stats.LinkStatsViewModel
import com.example.ui.theme.MonoFontFamily
import kotlinx.serialization.Serializable

@Serializable
data object LinksRoute

@Serializable
data object ShareRoute

@Serializable
data object SettingsRoute

@Serializable
data class StatsRoute(val linkId: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedirectApp(
  repository: RedirectRepository,
  pendingShare: SharedPayload?,
  onConsumePendingShare: () -> Unit,
) {
  val session by repository.session.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    repository.bootstrapVerify()
  }

  if (session.isBootstrapping) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding(),
      contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator()
    }
    return
  }

  if (!session.isAuthenticated) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
      AuthScreen(
        session = session,
        repository = repository,
        modifier = Modifier
          .padding(innerPadding)
          .consumeWindowInsets(innerPadding),
      )
    }
    return
  }

  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }

  val vmFactory = remember(repository) {
    object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
          DashboardViewModel::class.java -> DashboardViewModel(repository) as T
          ShareViewModel::class.java -> ShareViewModel(repository) as T
          SettingsViewModel::class.java -> SettingsViewModel(repository) as T
          else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
      }
    }
  }

  val dashboardVm: DashboardViewModel = viewModel(factory = vmFactory)
  val shareVm: ShareViewModel = viewModel(factory = vmFactory)
  val settingsVm: SettingsViewModel = viewModel(factory = vmFactory)

  LaunchedEffect(pendingShare) {
    if (pendingShare != null) {
      shareVm.applyIncomingShare(pendingShare)
      navController.navigate(ShareRoute) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
      }
      onConsumePendingShare()
    }
  }

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val isOnStats = currentDestination?.hasRoute<StatsRoute>() == true

  val navigateToTab: (Any) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  val serverHostLabel = remember(session.config.baseUrl) {
    ValidationUtils.extractHostname(session.config.baseUrl) ?: session.config.baseUrl
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        if (!isOnStats) {
          TopAppBar(
            title = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                AppBrandHeader()
                if (serverHostLabel.isNotBlank()) {
                  Text(
                    text = "  ·  $serverHostLabel",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background,
            ),
          )
        }
      },
      bottomBar = {
        if (!useRail && !isOnStats) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            NavigationBarItem(
              selected = currentDestination?.hasRoute<LinksRoute>() == true,
              onClick = { navigateToTab(LinksRoute) },
              icon = {
                Icon(
                  imageVector = if (currentDestination?.hasRoute<LinksRoute>() == true) {
                    Icons.Filled.Link
                  } else {
                    Icons.Outlined.Link
                  },
                  contentDescription = stringResource(R.string.nav_links),
                )
              },
              label = { Text(stringResource(R.string.nav_links)) },
              modifier = Modifier.testTag("nav_tab_links"),
            )
            NavigationBarItem(
              selected = currentDestination?.hasRoute<ShareRoute>() == true,
              onClick = { navigateToTab(ShareRoute) },
              icon = {
                Icon(
                  imageVector = if (currentDestination?.hasRoute<ShareRoute>() == true) {
                    Icons.Filled.Share
                  } else {
                    Icons.Outlined.Share
                  },
                  contentDescription = stringResource(R.string.nav_share),
                )
              },
              label = { Text(stringResource(R.string.nav_share)) },
              modifier = Modifier.testTag("nav_tab_share"),
            )
            NavigationBarItem(
              selected = currentDestination?.hasRoute<SettingsRoute>() == true,
              onClick = { navigateToTab(SettingsRoute) },
              icon = {
                Icon(
                  imageVector = if (currentDestination?.hasRoute<SettingsRoute>() == true) {
                    Icons.Filled.Settings
                  } else {
                    Icons.Outlined.Settings
                  },
                  contentDescription = stringResource(R.string.nav_settings),
                )
              },
              label = { Text(stringResource(R.string.nav_settings)) },
              modifier = Modifier.testTag("nav_tab_settings"),
            )
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .consumeWindowInsets(innerPadding),
      ) {
        if (useRail && !isOnStats) {
          NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            NavigationRailItem(
              selected = currentDestination?.hasRoute<LinksRoute>() == true,
              onClick = { navigateToTab(LinksRoute) },
              icon = { Icon(Icons.Filled.Link, contentDescription = stringResource(R.string.nav_links)) },
              label = { Text(stringResource(R.string.nav_links)) },
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<ShareRoute>() == true,
              onClick = { navigateToTab(ShareRoute) },
              icon = { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.nav_share)) },
              label = { Text(stringResource(R.string.nav_share)) },
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<SettingsRoute>() == true,
              onClick = { navigateToTab(SettingsRoute) },
              icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings)) },
              label = { Text(stringResource(R.string.nav_settings)) },
            )
          }
        }

        Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          if (session.accessExpired) {
            AccessExpiredBanner(
              onReconnect = { navigateToTab(SettingsRoute) },
              onDismiss = { repository.dismissAccessBanner() },
              modifier = Modifier
                .widthIn(max = 840.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            )
          }
          session.kvPropagationWarning?.let { warning ->
            KvGraceWarningBanner(
              message = warning,
              onDismiss = { repository.dismissKvWarning() },
              modifier = Modifier
                .widthIn(max = 840.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            )
          }

          Box(
            modifier = Modifier
              .fillMaxSize()
              .widthIn(max = 840.dp),
          ) {
            NavHost(
              navController = navController,
              startDestination = LinksRoute,
              modifier = Modifier.fillMaxSize(),
            ) {
              composable<LinksRoute> {
                DashboardScreen(
                  viewModel = dashboardVm,
                  serverBaseUrl = session.config.baseUrl,
                  snackbarHostState = snackbarHostState,
                  onNavigateToStats = { linkId -> navController.navigate(StatsRoute(linkId)) },
                )
              }
              composable<ShareRoute> {
                ShareScreen(
                  viewModel = shareVm,
                  serverBaseUrl = session.config.baseUrl,
                  snackbarHostState = snackbarHostState,
                  onNavigateToStats = { linkId -> navController.navigate(StatsRoute(linkId)) },
                )
              }
              composable<SettingsRoute> {
                SettingsScreen(
                  viewModel = settingsVm,
                  session = session,
                  repository = repository,
                  snackbarHostState = snackbarHostState,
                )
              }
              composable<StatsRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<StatsRoute>()
                val statsVm: LinkStatsViewModel = viewModel(
                  key = "stats_${args.linkId}",
                  factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                      return LinkStatsViewModel(args.linkId, repository) as T
                    }
                  },
                )
                LinkStatsScreen(
                  viewModel = statsVm,
                  serverBaseUrl = session.config.baseUrl,
                  snackbarHostState = snackbarHostState,
                  onBack = { navController.popBackStack() },
                )
              }
            }
          }
        }
      }
    }
  }
}
