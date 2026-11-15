package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.RedirectApiClient
import com.example.data.repository.RedirectRepository
import com.example.data.repository.SessionState
import com.example.data.storage.SecurePreferences
import com.example.domain.model.LinkRecord
import com.example.domain.model.Principal
import com.example.domain.model.ServerConfig
import com.example.domain.util.QrUtils
import com.example.ui.components.LinkRowCard
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.settings.ImportLinksDialog
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.share.ShareScreen
import com.example.ui.screens.share.ShareViewModel
import com.example.ui.theme.RedirectTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun readStringFromContext_returnsRedirect() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Redirect", appName)
  }

  @Test
  fun qrUtils_generatesBitmapAtRequestedResolution() {
    val bmp = QrUtils.generateQrBitmap("https://go.example.com/abc", sizePx = 256)
    assertEquals(256, bmp.width)
    assertEquals(256, bmp.height)
    assertTrue(bmp.byteCount > 0)
  }

  @Test
  fun authScreen_rendersAndExpandsZeroTrustAndKvHelp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = RedirectRepository(SecurePreferences(context), RedirectApiClient())

    composeTestRule.setContent {
      RedirectTheme {
        AuthScreen(
          session = SessionState(isBootstrapping = false),
          repository = repo,
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auth_server_url_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auth_token_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auth_connect_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("auth_zero_trust_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auth_cf_client_id_input").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("auth_kv_help_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auth_copy_wrangler_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun authScreen_focusingCfClientIdInCompactViewport_keepsFieldAndConnectButtonVisible() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = RedirectRepository(SecurePreferences(context), RedirectApiClient())
    var viewportHeight by mutableStateOf(800.dp)

    composeTestRule.setContent {
      RedirectTheme {
        Box(modifier = Modifier.fillMaxWidth().height(viewportHeight)) {
          AuthScreen(
            session = SessionState(isBootstrapping = false),
            repository = repo,
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auth_zero_trust_toggle").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auth_cf_client_id_input").performClick()
    composeTestRule.waitForIdle()

    // Simulate the on-screen keyboard opening and shrinking the available viewport.
    viewportHeight = 420.dp
    composeTestRule.mainClock.advanceTimeBy(300)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("auth_cf_client_id_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auth_connect_button").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun shareScreen_focusingCustomSlugInCompactViewport_keepsFieldAndSubmitButtonVisible() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = RedirectRepository(SecurePreferences(context), RedirectApiClient())
    val vm = ShareViewModel(repo)
    var viewportHeight by mutableStateOf(800.dp)

    composeTestRule.setContent {
      RedirectTheme {
        Box(modifier = Modifier.fillMaxWidth().height(viewportHeight)) {
          ShareScreen(
            viewModel = vm,
            serverBaseUrl = "https://go.example.com",
            snackbarHostState = SnackbarHostState(),
            onNavigateToStats = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("share_slug_input").performClick()
    composeTestRule.waitForIdle()

    // Simulate the on-screen keyboard opening and shrinking the available viewport.
    viewportHeight = 380.dp
    composeTestRule.mainClock.advanceTimeBy(300)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("share_slug_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("share_submit_button").assertIsDisplayed()
  }

  @Test
  fun linkRowCard_clickingSlugOrCardOpensStats_whileCopyDoesNot() {
    var statsClicks = 0
    var copyClicks = 0
    val sampleLink = LinkRecord(
      id = "id-1",
      slug = "imported-xxal",
      targetUrl = "https://example.com/some/very/long/destination/path",
      title = "Imported Link",
      statusCode = 307,
      tags = listOf("docs"),
      isActive = true,
      forwardQuery = true,
      clickCount = 12,
      createdAt = "2026-01-01T00:00:00.000Z",
      updatedAt = "2026-01-01T00:00:00.000Z",
    )

    composeTestRule.setContent {
      RedirectTheme {
        LinkRowCard(
          link = sampleLink,
          isToggling = false,
          onCopyShortUrl = { copyClicks++ },
          onToggleActive = {},
          onTagClick = {},
          onViewStats = { statsClicks++ },
          onShowQr = {},
          onEdit = {},
          onDelete = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("link_slug_imported-xxal", useUnmergedTree = true).assertIsDisplayed()

    composeTestRule.onNodeWithTag("copy_slug_imported-xxal").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, copyClicks)
    assertEquals(0, statsClicks)

    composeTestRule.onNodeWithTag("link_card_imported-xxal").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, statsClicks)
  }

  @Test
  fun settingsScreen_customToken_showsRestrictedNoticeAndHidesManagementButtons() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = RedirectRepository(SecurePreferences(context), RedirectApiClient())
    val vm = SettingsViewModel(repo)
    val customSession = SessionState(
      isBootstrapping = false,
      isAuthenticated = true,
      config = ServerConfig(baseUrl = "https://go.example.com", apiToken = "tok-custom"),
      principal = Principal(kind = "token", id = "tok_1", name = "Mobile Client"),
    )

    composeTestRule.setContent {
      RedirectTheme {
        SettingsScreen(
          viewModel = vm,
          session = customSession,
          repository = repo,
          snackbarHostState = SnackbarHostState(),
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_list").performScrollToIndex(1)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_tokens_restricted_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_create_token_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("settings_rotate_initial_button").assertDoesNotExist()
  }

  @Test
  fun settingsScreen_envKeySession_showsLockdownBannerAndHidesCreationButtons() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = RedirectRepository(SecurePreferences(context), RedirectApiClient())
    val vm = SettingsViewModel(repo)
    val envSession = SessionState(
      isBootstrapping = false,
      isAuthenticated = true,
      config = ServerConfig(baseUrl = "https://go.example.com", apiToken = "master-secret"),
      principal = Principal(kind = "env", id = "env", name = "API_KEY"),
    )

    composeTestRule.setContent {
      RedirectTheme {
        SettingsScreen(
          viewModel = vm,
          session = envSession,
          repository = repo,
          snackbarHostState = SnackbarHostState(),
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_list").performScrollToIndex(1)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_lockdown_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_create_token_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("settings_rotate_initial_button").assertDoesNotExist()
  }

  @Test
  fun settingsViewModel_loadsCsvIntoDynamicImporterAndRendersMapping() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = RedirectRepository(SecurePreferences(context), RedirectApiClient())
    val vm = SettingsViewModel(repo)
    vm.openImportDialog()
    vm.loadImportFile(
      fileName = "links.csv",
      rawText = "url,slug\nhttps://example.com/a,alpha\nhttps://example.com/b,beta",
    )

    val state = vm.uiState.value.importState
    composeTestRule.setContent {
      RedirectTheme {
        ImportLinksDialog(
          state = state,
          onDismiss = {},
          onConflictModeChange = {},
          onFileLoaded = { _, _ -> },
          onToggleCustomizeV1 = {},
          onSelectJsonArrayPath = {},
          onCsvOptionsChange = { _, _ -> },
          onFieldModeChange = { _, _ -> },
          onSourceFieldChange = { _, _ -> },
          onConstantChange = { _, _ -> },
          onStartImport = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("import_csv_has_header_checkbox").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("import_ready_count").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Ready to import 2 of 2 row(s)").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mapping_core_summary").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("import_converted_preview").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("mapping_core_edit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("mapping_source_targetUrl").performScrollTo().assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w900dp-h900dp")
  fun importLinksDialog_tabletWidth_rendersTwoPaneMappingAndPreviewSimultaneously() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = RedirectRepository(SecurePreferences(context), RedirectApiClient())
    val vm = SettingsViewModel(repo)
    vm.openImportDialog()
    vm.loadImportFile(
      fileName = "links.csv",
      rawText = "url,slug\nhttps://example.com/a,alpha\nhttps://example.com/b,beta",
    )

    val state = vm.uiState.value.importState
    composeTestRule.setContent {
      RedirectTheme {
        ImportLinksDialog(
          state = state,
          onDismiss = {},
          onConflictModeChange = {},
          onFileLoaded = { _, _ -> },
          onToggleCustomizeV1 = {},
          onSelectJsonArrayPath = {},
          onCsvOptionsChange = { _, _ -> },
          onFieldModeChange = { _, _ -> },
          onSourceFieldChange = { _, _ -> },
          onConstantChange = { _, _ -> },
          onStartImport = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("import_csv_has_header_checkbox").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mapping_optional_toggle").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("import_converted_preview").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("import_start_button").assertIsDisplayed()
  }
}
