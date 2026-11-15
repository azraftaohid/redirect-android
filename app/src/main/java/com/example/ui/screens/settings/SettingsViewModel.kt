package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.network.ImportChunkException
import com.example.data.repository.RedirectRepository
import com.example.domain.model.CreateTokenRequest
import com.example.domain.model.ImportConflictMode
import com.example.domain.model.ImportLinksResponse
import com.example.domain.model.ImportSummary
import com.example.domain.model.TokenInfo
import com.example.domain.util.CsvAndTimestampParser
import com.example.domain.util.CsvDelimiterOption
import com.example.domain.util.CsvParseResult
import com.example.domain.util.DynamicImporter
import com.example.domain.util.FieldMappingEntry
import com.example.domain.util.FieldMappingMode
import com.example.domain.util.ImportTargetField
import com.example.domain.util.JsonArrayCandidate
import com.example.domain.util.TransferUtils
import com.example.domain.util.TransformMappedRowsResult
import com.example.domain.util.ValidTransformedImportItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class RevealedToken(
  val title: String,
  val token: String,
  val preview: String,
)

enum class ImportSourceFormat {
  NONE,
  JSON_V1,
  JSON_DYNAMIC,
  CSV,
}

data class ImportDialogState(
  val isOpen: Boolean = false,
  val isParsingFile: Boolean = false,
  val fileName: String = "",
  val rawContent: String = "",
  val sourceFormat: ImportSourceFormat = ImportSourceFormat.NONE,
  val jsonArrayCandidates: List<JsonArrayCandidate> = emptyList(),
  val selectedArrayPath: String = "",
  val isJsonObjectRoot: Boolean = false,
  val csvDelimiterOption: CsvDelimiterOption = CsvDelimiterOption.AUTO,
  val detectedCsvDelimiter: Char? = null,
  val csvHasHeader: Boolean = true,
  val v1FastPathResult: TransformMappedRowsResult? = null,
  val isCustomizingV1Mapping: Boolean = false,
  val rawRows: List<JsonElement> = emptyList(),
  val sourceFields: List<String> = emptyList(),
  val fieldMapping: Map<ImportTargetField, FieldMappingEntry> = emptyMap(),
  val transformedResult: TransformMappedRowsResult? = null,
  val parseError: String? = null,
  val conflictMode: ImportConflictMode = ImportConflictMode.SKIP,
  val isImporting: Boolean = false,
  val processedCount: Int = 0,
  val totalCount: Int = 0,
  val importError: String? = null,
  val result: ImportLinksResponse? = null,
  val remainingRetryItems: List<ValidTransformedImportItem> = emptyList(),
) {
  val isUsingMappingUi: Boolean
    get() = sourceFormat == ImportSourceFormat.CSV ||
      sourceFormat == ImportSourceFormat.JSON_DYNAMIC ||
      (sourceFormat == ImportSourceFormat.JSON_V1 && isCustomizingV1Mapping)

  val activeTransformResult: TransformMappedRowsResult?
    get() = if (sourceFormat == ImportSourceFormat.JSON_V1 && !isCustomizingV1Mapping) {
      v1FastPathResult
    } else {
      transformedResult
    }

  val readyLinkCount: Int
    get() = activeTransformResult?.validItems?.size ?: 0

  val totalSourceRowCount: Int
    get() = if (sourceFormat == ImportSourceFormat.JSON_V1 && !isCustomizingV1Mapping) {
      v1FastPathResult?.totalRows ?: 0
    } else {
      rawRows.size
    }

  val canRetryFailedImport: Boolean
    get() = !isImporting && remainingRetryItems.isNotEmpty()
}

data class SettingsUiState(
  val tokens: List<TokenInfo> = emptyList(),
  val isLoadingTokens: Boolean = true,
  val tokensError: String? = null,
  val tokensBannerMessage: String? = null,
  val isCreateTokenOpen: Boolean = false,
  val isCreatingToken: Boolean = false,
  val createTokenError: String? = null,
  val revealedToken: RevealedToken? = null,
  val showRotateConfirm: Boolean = false,
  val isRotating: Boolean = false,
  val revokeTarget: TokenInfo? = null,
  val isRevoking: Boolean = false,
  val showRevokeAllConfirm: Boolean = false,
  val isRevokingAll: Boolean = false,
  val isExporting: Boolean = false,
  val exportStatusMessage: String? = null,
  val importState: ImportDialogState = ImportDialogState(),
)

class SettingsViewModel(
  private val repository: RedirectRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  private val jsonParser = Json { ignoreUnknownKeys = true; explicitNulls = false }
  private var constantDebounceJob: Job? = null
  private var activeImportJob: Job? = null

  fun loadTokensIfNeeded(canManageTokens: Boolean = true) {
    if (!canManageTokens) {
      _uiState.update {
        it.copy(isLoadingTokens = false, tokens = emptyList(), tokensError = null)
      }
      return
    }
    if (_uiState.value.tokens.isEmpty() && _uiState.value.isLoadingTokens) {
      refreshTokens(canManageTokens = true)
    }
  }

  fun refreshTokens(canManageTokens: Boolean = true) {
    if (!canManageTokens) {
      _uiState.update { it.copy(isLoadingTokens = false, tokensError = null) }
      return
    }
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

  fun dismissTokensBanner() {
    _uiState.update { it.copy(tokensBannerMessage = null) }
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
        refreshTokens(canManageTokens = true)
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
        refreshTokens(canManageTokens = true)
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
          refreshTokens(canManageTokens = true)
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

  fun requestRevokeAll() {
    _uiState.update { it.copy(showRevokeAllConfirm = true) }
  }

  fun cancelRevokeAll() {
    _uiState.update { it.copy(showRevokeAllConfirm = false, isRevokingAll = false) }
  }

  fun confirmRevokeAll() {
    viewModelScope.launch {
      _uiState.update { it.copy(isRevokingAll = true, tokensError = null) }
      try {
        val res = repository.revokeAllTokens()
        val msg = if (res.truncated) {
          "Revoked ${res.count} custom tokens. Additional tokens remain; run Revoke All Custom again to clear them."
        } else {
          "Revoked ${res.count} custom token${if (res.count == 1) "" else "s"}."
        }
        _uiState.update {
          it.copy(
            showRevokeAllConfirm = false,
            isRevokingAll = false,
            tokensBannerMessage = msg,
          )
        }
        refreshTokens(canManageTokens = true)
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            showRevokeAllConfirm = false,
            isRevokingAll = false,
            tokensError = e.message ?: "Failed to revoke custom tokens.",
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
    constantDebounceJob?.cancel()
    _uiState.update { it.copy(importState = ImportDialogState(isOpen = true)) }
  }

  fun closeImportDialog() {
    if (_uiState.value.importState.isImporting) return
    constantDebounceJob?.cancel()
    _uiState.update { it.copy(importState = ImportDialogState(isOpen = false)) }
  }

  fun setImportConflictMode(mode: ImportConflictMode) {
    _uiState.update {
      it.copy(importState = it.importState.copy(conflictMode = mode))
    }
  }

  fun setParsingImportFile(isParsing: Boolean) {
    _uiState.update {
      it.copy(importState = it.importState.copy(isParsingFile = isParsing))
    }
  }

  fun loadImportFile(fileName: String, rawText: String) {
    constantDebounceJob?.cancel()
    val conflictMode = _uiState.value.importState.conflictMode
    val serverBase = repository.session.value.config.baseUrl
    if (rawText.length >= LARGE_FILE_CHAR_THRESHOLD) {
      _uiState.update {
        it.copy(
          importState = it.importState.copy(
            isOpen = true,
            isParsingFile = true,
            fileName = fileName,
            parseError = null,
            importError = null,
            result = null,
          ),
        )
      }
      viewModelScope.launch {
        val nextState = withContext(Dispatchers.Default) {
          computeParsedImportState(fileName, rawText, conflictMode, serverBase)
        }
        _uiState.update { it.copy(importState = nextState) }
      }
    } else {
      val nextState = computeParsedImportState(fileName, rawText, conflictMode, serverBase)
      _uiState.update { it.copy(importState = nextState) }
    }
  }

  private fun computeParsedImportState(
    fileName: String,
    rawText: String,
    conflictMode: ImportConflictMode,
    serverBase: String,
  ): ImportDialogState {
    val clean = CsvAndTimestampParser.stripBom(rawText)
    val trimmed = clean.trim()
    val lower = fileName.lowercase()
    val treatAsCsv = when {
      lower.endsWith(".csv") || lower.endsWith(".tsv") -> true
      lower.endsWith(".json") -> false
      trimmed.startsWith("{") || trimmed.startsWith("[") -> false
      else -> true
    }

    if (trimmed.isEmpty()) {
      return ImportDialogState(
        isOpen = true,
        fileName = fileName,
        rawContent = if (treatAsCsv) rawText else "",
        sourceFormat = if (treatAsCsv) ImportSourceFormat.CSV else ImportSourceFormat.JSON_DYNAMIC,
        conflictMode = conflictMode,
        parseError = "Selected file is empty.",
      )
    }

    if (treatAsCsv) {
      return computeCsvImportState(
        fileName = fileName,
        rawText = rawText,
        delimiterOption = CsvDelimiterOption.AUTO,
        hasHeader = true,
        conflictMode = conflictMode,
        serverBase = serverBase,
      )
    }

    val root = try {
      jsonParser.parseToJsonElement(clean)
    } catch (e: Exception) {
      return ImportDialogState(
        isOpen = true,
        fileName = fileName,
        sourceFormat = ImportSourceFormat.JSON_DYNAMIC,
        conflictMode = conflictMode,
        parseError = "Invalid JSON: ${e.message ?: "Parse error"}",
      )
    }

    val v1Result = DynamicImporter.parseNativeV1FastPath(root, serverBase)
    val candidates = DynamicImporter.discoverJsonArrayPaths(root)
    val isObjRoot = root is JsonObject

    if (v1Result != null) {
      return ImportDialogState(
        isOpen = true,
        fileName = fileName,
        sourceFormat = ImportSourceFormat.JSON_V1,
        jsonArrayCandidates = candidates,
        selectedArrayPath = "links",
        isJsonObjectRoot = isObjRoot,
        v1FastPathResult = v1Result,
        isCustomizingV1Mapping = false,
        conflictMode = conflictMode,
      )
    }

    if (candidates.isEmpty()) {
      return ImportDialogState(
        isOpen = true,
        fileName = fileName,
        sourceFormat = ImportSourceFormat.JSON_DYNAMIC,
        isJsonObjectRoot = isObjRoot,
        conflictMode = conflictMode,
        parseError = "No JSON arrays found to import.",
      )
    }

    val first = candidates.first()
    val mappingBundle = buildMappingForRows(first.items, serverBase)

    return ImportDialogState(
      isOpen = true,
      fileName = fileName,
      sourceFormat = ImportSourceFormat.JSON_DYNAMIC,
      jsonArrayCandidates = candidates,
      selectedArrayPath = first.path,
      isJsonObjectRoot = isObjRoot,
      rawRows = first.items,
      sourceFields = mappingBundle.sourceFields,
      fieldMapping = mappingBundle.mapping,
      transformedResult = mappingBundle.transformed,
      parseError = if (first.items.isEmpty()) "Selected array \"${first.label}\" is empty." else null,
      conflictMode = conflictMode,
    )
  }

  fun toggleCustomizeV1Mapping() {
    val imp = _uiState.value.importState
    if (imp.sourceFormat != ImportSourceFormat.JSON_V1) return
    constantDebounceJob?.cancel()
    if (imp.isCustomizingV1Mapping) {
      _uiState.update {
        it.copy(
          importState = imp.copy(
            isCustomizingV1Mapping = false,
            parseError = null,
            importError = null,
            result = null,
            remainingRetryItems = emptyList(),
          ),
        )
      }
    } else {
      val targetCandidate = imp.jsonArrayCandidates.firstOrNull { it.path == imp.selectedArrayPath }
        ?: imp.jsonArrayCandidates.firstOrNull { it.path == "links" }
        ?: imp.jsonArrayCandidates.firstOrNull()
      val rows = targetCandidate?.items ?: emptyList()
      val serverBase = repository.session.value.config.baseUrl
      val bundle = buildMappingForRows(rows, serverBase)
      _uiState.update {
        it.copy(
          importState = imp.copy(
            isCustomizingV1Mapping = true,
            selectedArrayPath = targetCandidate?.path ?: "",
            rawRows = rows,
            sourceFields = bundle.sourceFields,
            fieldMapping = bundle.mapping,
            transformedResult = bundle.transformed,
            parseError = if (rows.isEmpty()) "Selected array is empty." else null,
            importError = null,
            result = null,
            remainingRetryItems = emptyList(),
          ),
        )
      }
    }
  }

  fun selectJsonArrayPath(path: String) {
    val imp = _uiState.value.importState
    val candidate = imp.jsonArrayCandidates.firstOrNull { it.path == path } ?: return
    constantDebounceJob?.cancel()
    val serverBase = repository.session.value.config.baseUrl
    val bundle = buildMappingForRows(candidate.items, serverBase)
    _uiState.update {
      it.copy(
        importState = imp.copy(
          selectedArrayPath = path,
          rawRows = candidate.items,
          sourceFields = bundle.sourceFields,
          fieldMapping = bundle.mapping,
          transformedResult = bundle.transformed,
          parseError = if (candidate.items.isEmpty()) "Selected array \"${candidate.label}\" is empty." else null,
          importError = null,
          result = null,
          remainingRetryItems = emptyList(),
        ),
      )
    }
  }

  fun updateCsvOptions(delimiterOption: CsvDelimiterOption, hasHeader: Boolean) {
    val imp = _uiState.value.importState
    if (imp.sourceFormat != ImportSourceFormat.CSV) return
    constantDebounceJob?.cancel()
    val serverBase = repository.session.value.config.baseUrl
    val nextState = computeCsvImportState(
      fileName = imp.fileName,
      rawText = imp.rawContent,
      delimiterOption = delimiterOption,
      hasHeader = hasHeader,
      conflictMode = imp.conflictMode,
      serverBase = serverBase,
    )
    _uiState.update { it.copy(importState = nextState) }
  }

  private fun computeCsvImportState(
    fileName: String,
    rawText: String,
    delimiterOption: CsvDelimiterOption,
    hasHeader: Boolean,
    conflictMode: ImportConflictMode,
    serverBase: String,
  ): ImportDialogState {
    return when (val res = CsvAndTimestampParser.parseCsvContent(rawText, delimiterOption.charValue, hasHeader)) {
      is CsvParseResult.Error -> {
        ImportDialogState(
          isOpen = true,
          fileName = fileName,
          rawContent = rawText,
          sourceFormat = ImportSourceFormat.CSV,
          csvDelimiterOption = delimiterOption,
          csvHasHeader = hasHeader,
          conflictMode = conflictMode,
          parseError = res.message,
        )
      }
      is CsvParseResult.Success -> {
        val rows = DynamicImporter.csvRowsToJsonElements(res.rows)
        val bundle = buildMappingForRows(rows, serverBase, explicitFields = res.headers)
        ImportDialogState(
          isOpen = true,
          fileName = fileName,
          rawContent = rawText,
          sourceFormat = ImportSourceFormat.CSV,
          csvDelimiterOption = delimiterOption,
          detectedCsvDelimiter = res.delimiter,
          csvHasHeader = hasHeader,
          rawRows = rows,
          sourceFields = bundle.sourceFields,
          fieldMapping = bundle.mapping,
          transformedResult = bundle.transformed,
          parseError = null,
          conflictMode = conflictMode,
        )
      }
    }
  }

  fun updateFieldMode(field: ImportTargetField, mode: FieldMappingMode) {
    val imp = _uiState.value.importState
    val prev = imp.fieldMapping[field] ?: FieldMappingEntry(FieldMappingMode.OMIT)
    val nextEntry = when (mode) {
      FieldMappingMode.OMIT -> prev.copy(mode = FieldMappingMode.OMIT)
      FieldMappingMode.FIELD -> prev.copy(
        mode = FieldMappingMode.FIELD,
        sourceField = prev.sourceField.ifEmpty { imp.sourceFields.firstOrNull().orEmpty() },
      )
      FieldMappingMode.CONSTANT -> prev.copy(
        mode = FieldMappingMode.CONSTANT,
        constantValue = prev.constantValue.ifEmpty { DynamicImporter.defaultConstantValue(field) },
      )
    }
    applyUpdatedMapping(imp.fieldMapping + (field to nextEntry), debounce = false)
  }

  fun updateSourceField(field: ImportTargetField, sourceField: String) {
    val imp = _uiState.value.importState
    val prev = imp.fieldMapping[field] ?: FieldMappingEntry(FieldMappingMode.FIELD)
    applyUpdatedMapping(
      imp.fieldMapping + (field to prev.copy(mode = FieldMappingMode.FIELD, sourceField = sourceField)),
      debounce = false,
    )
  }

  fun updateConstantValue(field: ImportTargetField, value: String) {
    val imp = _uiState.value.importState
    val prev = imp.fieldMapping[field] ?: FieldMappingEntry(FieldMappingMode.CONSTANT)
    val newMapping = imp.fieldMapping + (field to prev.copy(mode = FieldMappingMode.CONSTANT, constantValue = value))
    applyUpdatedMapping(newMapping, debounce = imp.rawRows.size > DEBOUNCE_ROW_THRESHOLD)
  }

  private fun applyUpdatedMapping(
    newMapping: Map<ImportTargetField, FieldMappingEntry>,
    debounce: Boolean,
  ) {
    constantDebounceJob?.cancel()
    val imp = _uiState.value.importState
    val rows = imp.rawRows
    val serverBase = repository.session.value.config.baseUrl

    if (!debounce) {
      val transformed = DynamicImporter.transformMappedRows(rows, newMapping, serverBase)
      _uiState.update {
        it.copy(
          importState = imp.copy(
            fieldMapping = newMapping,
            transformedResult = transformed,
            result = null,
            importError = null,
            remainingRetryItems = emptyList(),
          ),
        )
      }
      return
    }

    // Immediate field update for responsive typing, followed by debounced background transformation
    _uiState.update {
      it.copy(
        importState = imp.copy(
          fieldMapping = newMapping,
          result = null,
          importError = null,
          remainingRetryItems = emptyList(),
        ),
      )
    }
    constantDebounceJob = viewModelScope.launch {
      delay(220L)
      val transformed = withContext(Dispatchers.Default) {
        DynamicImporter.transformMappedRows(rows, newMapping, serverBase)
      }
      _uiState.update {
        it.copy(
          importState = it.importState.copy(transformedResult = transformed),
        )
      }
    }
  }

  private data class MappingBundle(
    val sourceFields: List<String>,
    val mapping: Map<ImportTargetField, FieldMappingEntry>,
    val transformed: TransformMappedRowsResult,
  )

  private fun buildMappingForRows(
    rows: List<JsonElement>,
    serverBaseUrl: String,
    explicitFields: List<String>? = null,
  ): MappingBundle {
    val fields = explicitFields ?: DynamicImporter.discoverSourceFields(rows)
    val mapping = DynamicImporter.autoDetectFieldMapping(fields)
    val transformed = DynamicImporter.transformMappedRows(rows, mapping, serverBaseUrl)
    return MappingBundle(fields, mapping, transformed)
  }

  fun startImport() {
    val imp = _uiState.value.importState
    if (imp.isImporting) return
    val activeTransform = imp.activeTransformResult ?: return
    if (activeTransform.validItems.isEmpty()) return

    executeImportItems(
      itemsToSubmit = activeTransform.validItems,
      clientErrors = activeTransform.clientErrors,
      clientFailedResults = activeTransform.clientFailedResults,
      totalRows = imp.totalSourceRowCount,
      priorResult = null,
    )
  }

  fun retryFailedImport() {
    val imp = _uiState.value.importState
    if (imp.isImporting || imp.remainingRetryItems.isEmpty()) return

    executeImportItems(
      itemsToSubmit = imp.remainingRetryItems,
      clientErrors = emptyList(),
      clientFailedResults = emptyList(),
      totalRows = imp.totalSourceRowCount,
      priorResult = imp.result,
    )
  }

  fun cancelImport() {
    activeImportJob?.cancel()
  }

  private fun executeImportItems(
    itemsToSubmit: List<ValidTransformedImportItem>,
    clientErrors: List<com.example.domain.model.ImportRowError>,
    clientFailedResults: List<com.example.domain.model.ImportRowResult>,
    totalRows: Int,
    priorResult: ImportLinksResponse?,
  ) {
    val conflictMode = _uiState.value.importState.conflictMode
    val links = itemsToSubmit.map { it.link }
    val validIndexMap = itemsToSubmit.map { it.originalIndex }

    activeImportJob?.cancel()
    activeImportJob = viewModelScope.launch {
      _uiState.update {
        it.copy(
          importState = it.importState.copy(
            isImporting = true,
            processedCount = 0,
            totalCount = links.size,
            importError = null,
            remainingRetryItems = emptyList(),
          ),
        )
      }
      try {
        val serverResult = repository.importLinksChunked(
          links = links,
          onConflict = conflictMode,
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
        val mergedCurrent = DynamicImporter.mergeClientAndServerImportReport(
          serverReport = serverResult,
          validIndexMap = validIndexMap,
          clientErrors = clientErrors,
          clientFailedResults = clientFailedResults,
          totalRows = if (priorResult == null) totalRows else serverResult.summary.total,
        )
        val finalReport = if (priorResult != null) {
          combineImportReports(priorResult, mergedCurrent, totalRows)
        } else {
          mergedCurrent
        }
        _uiState.update {
          it.copy(
            importState = it.importState.copy(
              isImporting = false,
              result = finalReport,
              importError = null,
              remainingRetryItems = emptyList(),
            ),
          )
        }
      } catch (e: ImportChunkException) {
        val processedValid = e.processedCount
        val remaining = itemsToSubmit.drop(processedValid)
        val partialReport = if (processedValid > 0 || clientErrors.isNotEmpty() || priorResult != null) {
          val lastOriginalIdx = if (processedValid > 0) {
            validIndexMap.getOrNull(processedValid - 1) ?: Int.MAX_VALUE
          } else {
            -1
          }
          val scopedErrors = clientErrors.filter { it.index <= lastOriginalIdx }
          val scopedFailed = clientFailedResults.filter { it.index <= lastOriginalIdx }
          val mergedPartial = DynamicImporter.mergeClientAndServerImportReport(
            serverReport = e.partialResponse,
            validIndexMap = validIndexMap,
            clientErrors = scopedErrors,
            clientFailedResults = scopedFailed,
            totalRows = processedValid + scopedErrors.size,
          )
          if (priorResult != null) {
            combineImportReports(
              a = priorResult,
              b = mergedPartial,
              totalRows = priorResult.summary.total + mergedPartial.summary.total,
            )
          } else {
            mergedPartial
          }
        } else {
          null
        }
        _uiState.update {
          it.copy(
            importState = it.importState.copy(
              isImporting = false,
              importError = e.message,
              result = partialReport,
              remainingRetryItems = remaining,
            ),
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            importState = it.importState.copy(
              isImporting = false,
              importError = e.message ?: "Import failed.",
              remainingRetryItems = itemsToSubmit,
            ),
          )
        }
      }
    }
  }

  private fun combineImportReports(
    a: ImportLinksResponse,
    b: ImportLinksResponse,
    totalRows: Int,
  ): ImportLinksResponse {
    return a.copy(
      summary = ImportSummary(
        total = totalRows,
        created = a.summary.created + b.summary.created,
        updated = a.summary.updated + b.summary.updated,
        skipped = a.summary.skipped + b.summary.skipped,
        failed = a.summary.failed + b.summary.failed,
      ),
      skippedSlugs = (a.skippedSlugs + b.skippedSlugs).distinct(),
      createdSlugs = (a.createdSlugs + b.createdSlugs).distinct(),
      updatedSlugs = (a.updatedSlugs + b.updatedSlugs).distinct(),
      errors = (a.errors + b.errors).sortedBy { it.index },
      results = (a.results + b.results).sortedBy { it.index },
    )
  }

  companion object {
    private const val LARGE_FILE_CHAR_THRESHOLD = 50_000
    private const val DEBOUNCE_ROW_THRESHOLD = 50
  }
}
