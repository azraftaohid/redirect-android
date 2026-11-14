package com.example.ui.screens.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.RedirectRepository
import com.example.domain.model.CreateLinkRequest
import com.example.domain.model.LinkRecord
import com.example.domain.util.SharedPayload
import com.example.domain.util.ValidationUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareUiState(
  val url: String = "",
  val title: String = "",
  val customSlug: String = "",
  val existingMatch: LinkRecord? = null,
  val isCheckingDuplicate: Boolean = false,
  val isSubmitting: Boolean = false,
  val error: String? = null,
  val createdLink: LinkRecord? = null,
)

sealed interface ShareEvent {
  data class CreatedAndCopied(val shortUrl: String) : ShareEvent
}

class ShareViewModel(
  private val repository: RedirectRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(ShareUiState())
  val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

  private val _events = MutableSharedFlow<ShareEvent>()
  val events: SharedFlow<ShareEvent> = _events.asSharedFlow()

  private var duplicateJob: Job? = null

  fun applyIncomingShare(payload: SharedPayload) {
    _uiState.update {
      ShareUiState(
        url = payload.url,
        title = payload.title,
      )
    }
    if (payload.url.isNotBlank()) {
      scheduleDuplicateCheck(payload.url, immediate = true)
    }
  }

  fun onUrlChange(value: String) {
    _uiState.update { it.copy(url = value, error = null, existingMatch = null) }
    scheduleDuplicateCheck(value, immediate = false)
  }

  fun onTitleChange(value: String) {
    if (value.length <= ValidationUtils.MAX_TITLE_LENGTH) {
      _uiState.update { it.copy(title = value) }
    }
  }

  fun onSlugChange(value: String) {
    _uiState.update { it.copy(customSlug = value, error = null) }
  }

  fun generateSlug() {
    _uiState.update {
      it.copy(customSlug = ValidationUtils.generateValidRandomSlug(), error = null)
    }
  }

  fun resetForAnother() {
    duplicateJob?.cancel()
    _uiState.value = ShareUiState()
  }

  fun shorten() {
    val state = _uiState.value
    val serverBase = repository.session.value.config.baseUrl
    val normalized = ValidationUtils.normalizeTargetUrlInput(state.url)
    val urlErr = ValidationUtils.validateTargetUrl(normalized, serverBase)
    if (urlErr != null) {
      _uiState.update { it.copy(error = urlErr) }
      return
    }
    val slugTrimmed = state.customSlug.trim()
    if (slugTrimmed.isNotEmpty()) {
      val slugErr = ValidationUtils.validateSlug(slugTrimmed)
      if (slugErr != null) {
        _uiState.update { it.copy(error = slugErr) }
        return
      }
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isSubmitting = true, error = null) }
      try {
        val created = repository.createLink(
          CreateLinkRequest(
            targetUrl = normalized,
            slug = slugTrimmed.ifEmpty { null },
            title = state.title.trim().ifEmpty { null },
          ),
        )
        val shortUrl = ValidationUtils.buildShortUrl(serverBase, created.slug)
        _uiState.update {
          it.copy(
            isSubmitting = false,
            createdLink = created,
            error = null,
          )
        }
        _events.emit(ShareEvent.CreatedAndCopied(shortUrl))
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isSubmitting = false,
            error = e.message ?: "Failed to create short link.",
          )
        }
      }
    }
  }

  private fun scheduleDuplicateCheck(rawUrl: String, immediate: Boolean) {
    duplicateJob?.cancel()
    val normalized = ValidationUtils.normalizeTargetUrlInput(rawUrl)
    val serverBase = repository.session.value.config.baseUrl
    if (ValidationUtils.validateTargetUrl(normalized, serverBase) != null) {
      _uiState.update { it.copy(isCheckingDuplicate = false, existingMatch = null) }
      return
    }
    duplicateJob = viewModelScope.launch {
      if (!immediate) delay(350)
      _uiState.update { it.copy(isCheckingDuplicate = true) }
      try {
        val result = repository.listLinks(targetUrl = normalized, limit = 1)
        _uiState.update {
          it.copy(
            isCheckingDuplicate = false,
            existingMatch = result.data.firstOrNull(),
          )
        }
      } catch (_: Exception) {
        _uiState.update { it.copy(isCheckingDuplicate = false) }
      }
    }
  }
}
