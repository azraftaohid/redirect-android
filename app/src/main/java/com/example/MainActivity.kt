package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.network.RedirectApiClient
import com.example.data.repository.RedirectRepository
import com.example.data.storage.SecurePreferences
import com.example.domain.util.ShareParser
import com.example.domain.util.SharedPayload
import com.example.ui.RedirectApp
import com.example.ui.theme.RedirectTheme

class MainActivity : ComponentActivity() {
  private lateinit var repository: RedirectRepository
  private var pendingSharePayload by mutableStateOf<SharedPayload?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    repository = RedirectRepository(
      prefs = SecurePreferences(applicationContext),
      api = RedirectApiClient(),
    )

    handleShareIntent(intent)

    setContent {
      RedirectTheme {
        RedirectApp(
          repository = repository,
          pendingShare = pendingSharePayload,
          onConsumePendingShare = { pendingSharePayload = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleShareIntent(intent)
  }

  private fun handleShareIntent(intent: Intent?) {
    if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
      val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
      val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
      val payload = ShareParser.extractSharedPayload(sharedText, sharedSubject)
      if (payload.url.isNotBlank() || payload.title.isNotBlank()) {
        pendingSharePayload = payload
      }
    }
  }
}
