package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.network.ImportChunkException
import com.example.data.repository.RedirectRepository
import com.example.domain.model.CreateTokenRequest
import com.example.domain.model.ImportConflictMode
import com.example.domain.model.ImportLinkInput
import com.example.domain.model.ImportLinksResponse
import com.example.domain.model.TokenInfo
import com.example.domain.util.TransferUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RevealedToken(
  val title: String,
  val token: String,
  val preview: String,
)

data class ImportDialogState(
  val isOpen: Boolean = false,
  val fileName: String = "",
  val parsedLinks: List<ImportLinkInput> = emptyList(),
  val parseError: String? = null,
  val conflictMode: ImportConflictMode = ImportConflictMode.SKIP,
  val isImporting: Boolean = false,
  val processedCount: Int = 0,
  val totalCount: Int = 0,
  val importError: String? = null,
  val result: ImportLinksResponse? = null,
)

data class SettingsUiState(
  val tokens: List<TokenInfo> = emptyList(),
  val isLoadingTokens: Boolean = true,
  val tokensError: String? = null,
  val isCreateTokenOpen: Boolean = false,
  val isCreatingToken: Boolean = false,
  val createTokenError: String? = null,
  val revealedToken: RevealedToken? = null,
  val showRotateConfirm: Boolean = false,
  val isRotating: Boolean = false,
  val revokeTarget: TokenInfo? = null,
  val isRevoking: Boolean = false,
  val isExporting: Boolean = false,
  val exportStatusMessage: String? = null,
  val importState: ImportDialogState = ImportDialogState(),
)

class SettingsViewModel(
  private val repository: RedirectRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  fun loadTokensIfNeeded() {
    if (_uiState.value.tokens.isEmpty() && _uiState.value.isLoadingTokens) {
      refreshTokens()
    }
  }

  fun refreshTokens() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingTokens = true, tokensError = null) }
      try {
        val list = repository.listTokens()
        _uiState.update { it.copy(tokens = list, isLoadingTokens = false) }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isLoadingTokens = false,
            tokensError = e.message ?: "Failed to load API tokens.",
          )
        }
      }
    }
  }

  fun openCreateToken() {
    _uiState.update { it.copy(isCreateTokenOpen = true, createTokenError = null) }
  }

  fun closeCreateToken() {
    _uiState.update {
      it.copy(isCreateTokenOpen = false, createTokenError = null, isCreatingToken = false)
    }
  }

  fun createToken(name: String, expiresAtIso: String?) {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed.length > 64) {
      _uiState.update { it.copy(createTokenError = "Token name must be 1–64 characters.") }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isCreatingToken = true, createTokenError = null) }
      try {
        val created = repository.createToken(CreateTokenRequest(name = trimmed, expiresAt = expiresAtIso))
        _uiState.update {
          it.copy(
            isCreateTokenOpen = false,
            isCreatingToken = false,
            revealedToken = RevealedToken(
              title = "Token Created: ${created.name}",
              token = created.token,
              preview = created.preview,
            ),
          )
        }
        refreshTokens()
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isCreatingToken = false,
            createTokenError = e.message ?: "Failed to create token.",
          )
        }
      }
    }
  }

  fun dismissRevealedToken() {
    _uiState.update { it.copy(revealedToken = null) }
  }

  fun requestRotateInitial() {
    _uiState.update { it.copy(showRotateConfirm = true) }
  }

  fun cancelRotateInitial() {
    _uiState.update { it.copy(showRotateConfirm = false) }
  }

  fun confirmRotateInitial() {
    viewModelScope.launch {
      _uiState.update { it.copy(isRotating = true) }
      try {
        val rotated = repository.rotateInitialToken()
        _uiState.update {
          it.copy(
            showRotateConfirm = false,
            isRotating = false,
            revealedToken = RevealedToken(
              title = "Initial Token Rotated",
              token = rotated.token,
              preview = rotated.preview,
            ),
          )
        }
        refreshTokens()
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            showRotateConfirm = false,
            isRotating = false,
            tokensError = e.message ?: "Failed to rotate initial token.",
          )
        }
      }
    }
  }

  fun requestRevoke(token: TokenInfo) {
    _uiState.update { it.copy(revokeTarget = token) }
  }

  fun cancelRevoke() {
    _uiState.update { it.copy(revokeTarget = null, isRevoking = false) }
  }

  fun confirmRevoke(activePrincipalId: String?) {
    val target = _uiState.value.revokeTarget ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isRevoking = true) }
      try {
        repository.revokeToken(target.id)
        _uiState.update { it.copy(revokeTarget = null, isRevoking = false) }
        if (target.id == activePrincipalId) {
          repository.logout()
        } else {
          refreshTokens()
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            revokeTarget = null,
            isRevoking = false,
            tokensError = e.message ?: "Failed to revoke token.",
          )
        }
      }
    }
  }

  suspend fun buildExportJsonString(): Result<String> {
    _uiState.update { it.copy(isExporting = true, exportStatusMessage = "Fetching links…") }
    return try {
      val (links, exportedAt) = repository.exportAllLinks { fetched ->
        _uiState.update { it.copy(exportStatusMessage = "Fetched $fetched links…") }
      }
      val jsonText = TransferUtils.buildExportJson(links, exportedAt)
      _uiState.update {
        it.copy(
          isExporting = false,
          exportStatusMessage = "Exported ${links.size} links.",
        )
      }
      Result.success(jsonText)
    } catch (e: Exception) {
      _uiState.update {
        it.copy(
          isExporting = false,
          exportStatusMessage = e.message ?: "Export failed.",
        )
      }
      Result.failure(e)
    }
  }

  fun openImportDialog() {
    _uiState.update { it.copy(importState = ImportDialogState(isOpen = true)) }
  }

  fun closeImportDialog() {
    if (_uiState.value.importState.isImporting) return
    _uiState.update { it.copy(importState = ImportDialogState(isOpen = false)) }
  }

  fun setImportConflictMode(mode: ImportConflictMode) {
    _uiState.update {
      it.copy(importState = it.importState.copy(conflictMode = mode))
    }
  }

  fun loadImportJson(fileName: String, rawJson: String) {
    try {
      val parsed = TransferUtils.parseImportLinks(rawJson)
      _uiState.update {
        it.copy(
          importState = it.importState.copy(
            fileName = fileName,
            parsedLinks = parsed,
            parseError = null,
            result = null,
            importError = null,
          ),
        )
      }
    } catch (e: Exception) {
      _uiState.update {
        it.copy(
          importState = it.importState.copy(
            fileName = fileName,
            parsedLinks = emptyList(),
            parseError = e.message ?: "Invalid JSON file.",
            result = null,
          ),
        )
      }
    }
  }

  fun startImport() {
    val imp = _uiState.value.importState
    if (imp.parsedLinks.isEmpty() || imp.isImporting) return
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          importState = it.importState.copy(
            isImporting = true,
            processedCount = 0,
            totalCount = imp.parsedLinks.size,
            importError = null,
            result = null,
          ),
        )
      }
      try {
        val result = repository.importLinksChunked(
          links = imp.parsedLinks,
          onConflict = imp.conflictMode,
          onProgress = { processed, total ->
            _uiState.update {
              it.copy(
                importState = it.importState.copy(
                  processedCount = processed,
                  totalCount = total,
                ),
              )
            }
          },
        )
        _uiState.update {
          it.copy(
            importState = it.importState.copy(
              isImporting = false,
              result = result,
            ),
          )
        }
      } catch (e: ImportChunkException) {
        _uiState.update {
          it.copy(
            importState = it.importState.copy(
              isImporting = false,
              importError = e.message,
              result = e.partialResponse,
            ),
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            importState = it.importState.copy(
              isImporting = false,
              importError = e.message ?: "Import failed.",
            ),
          )
        }
      }
    }
  }
}
