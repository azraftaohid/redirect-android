package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.RedirectApiClient
import com.example.data.repository.RedirectRepository
import com.example.data.repository.SessionState
import com.example.data.storage.SecurePreferences
import com.example.domain.model.LinkRecord
import com.example.domain.util.QrUtils
import com.example.ui.components.LinkRowCard
import com.example.ui.screens.auth.AuthScreen
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
}
