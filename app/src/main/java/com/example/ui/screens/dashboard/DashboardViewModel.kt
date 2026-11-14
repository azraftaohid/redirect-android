package com.example.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.RedirectRepository
import com.example.domain.model.CreateLinkRequest
import com.example.domain.model.LinkPatch
import com.example.domain.model.LinkRecord
import com.example.domain.model.LinkSortOption
import com.example.domain.model.StatusFilter
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

data class DashboardUiState(
  val links: List<LinkRecord> = emptyList(),
  val total: Int = 0,
  val offset: Int = 0,
  val hasMore: Boolean = false,
  val isLoading: Boolean = true,
  val isRefreshing: Boolean = false,
  val error: String? = null,
  val quickUrl: String = "",
  val isQuickShortening: Boolean = false,
  val quickError: String? = null,
  val searchQuery: String = "",
  val statusFilter: StatusFilter = StatusFilter.ALL,
  val sortOption: LinkSortOption = LinkSortOption.NEWEST,
  val activeTag: String? = null,
  val togglingIds: Set<String> = emptySet(),
  val isFormOpen: Boolean = false,
  val formTargetLink: LinkRecord? = null,
  val formInitialUrl: String = "",
  val isFormSubmitting: Boolean = false,
  val formError: String? = null,
  val qrTargetLink: LinkRecord? = null,
  val deleteTargetLink: LinkRecord? = null,
  val isDeleting: Boolean = false,
)

sealed interface DashboardEvent {
  data class LinkShortenedAndCopied(val link: LinkRecord, val shortUrl: String) : DashboardEvent
  data class ShowMessage(val text: String) : DashboardEvent
}

class DashboardViewModel(
  private val repository: RedirectRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(DashboardUiState())
  val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

  private val _events = MutableSharedFlow<DashboardEvent>()
  val events: SharedFlow<DashboardEvent> = _events.asSharedFlow()

  private var searchDebounceJob: Job? = null

  init {
    viewModelScope.launch {
      var lastSeenVersion = repository.linksVersion.value
      repository.linksVersion.collect { version ->
        if (version != lastSeenVersion) {
          lastSeenVersion = version
          fetchLinks(isRefresh = false)
        }
      }
    }
  }

  fun loadInitialIfNeeded() {
    if (_uiState.value.links.isEmpty() && _uiState.value.isLoading) {
      fetchLinks(isRefresh = false)
    }
  }

  fun refresh() {
    fetchLinks(isRefresh = true)
  }

  fun onQuickUrlChange(value: String) {
    _uiState.update { it.copy(quickUrl = value, quickError = null) }
  }

  fun quickShorten() {
    val raw = _uiState.value.quickUrl
    val normalized = ValidationUtils.normalizeTargetUrlInput(raw)
    val serverBase = repository.session.value.config.baseUrl
    val err = ValidationUtils.validateTargetUrl(normalized, serverBase)
    if (err != null) {
      _uiState.update { it.copy(quickError = err) }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isQuickShortening = true, quickError = null) }
      try {
        val created = repository.createLink(CreateLinkRequest(targetUrl = normalized))
        val shortUrl = ValidationUtils.buildShortUrl(serverBase, created.slug)
        _uiState.update { it.copy(quickUrl = "", isQuickShortening = false, offset = 0) }
        _events.emit(DashboardEvent.LinkShortenedAndCopied(created, shortUrl))
        fetchLinks(isRefresh = false)
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isQuickShortening = false,
            quickError = e.message ?: "Failed to shorten link.",
          )
        }
      }
    }
  }

  fun onSearchQueryChange(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
    searchDebounceJob?.cancel()
    searchDebounceJob = viewModelScope.launch {
      delay(400L)
      _uiState.update { it.copy(offset = 0) }
      fetchLinks(isRefresh = false, searchOverride = query)
    }
  }

  fun submitSearchNow() {
    searchDebounceJob?.cancel()
    _uiState.update { it.copy(offset = 0) }
    fetchLinks(isRefresh = false, searchOverride = _uiState.value.searchQuery)
  }

  fun clearSearch() {
    searchDebounceJob?.cancel()
    _uiState.update { it.copy(searchQuery = "", offset = 0) }
    fetchLinks(isRefresh = false, searchOverride = "")
  }

  fun onStatusFilterChange(filter: StatusFilter) {
    _uiState.update { it.copy(statusFilter = filter, offset = 0) }
    fetchLinks(isRefresh = false)
  }

  fun onSortOptionChange(option: LinkSortOption) {
    _uiState.update { it.copy(sortOption = option, offset = 0) }
    fetchLinks(isRefresh = false)
  }

  fun onTagFilterSelected(tag: String?) {
    val next = if (_uiState.value.activeTag == tag) null else tag
    _uiState.update { it.copy(activeTag = next, offset = 0) }
    fetchLinks(isRefresh = false)
  }

  fun resetAllFilters() {
    searchDebounceJob?.cancel()
    _uiState.update {
      it.copy(
        searchQuery = "",
        statusFilter = StatusFilter.ALL,
        sortOption = LinkSortOption.NEWEST,
        activeTag = null,
        offset = 0,
      )
    }
    fetchLinks(isRefresh = false, searchOverride = "")
  }

  fun nextPage() {
    val state = _uiState.value
    if (!state.hasMore) return
    _uiState.update { it.copy(offset = state.offset + ValidationUtils.PAGE_SIZE) }
    fetchLinks(isRefresh = false)
  }

  fun previousPage() {
    val state = _uiState.value
    if (state.offset <= 0) return
    _uiState.update { it.copy(offset = maxOf(0, state.offset - ValidationUtils.PAGE_SIZE)) }
    fetchLinks(isRefresh = false)
  }

  fun toggleLinkActive(link: LinkRecord, nextActive: Boolean) {
    viewModelScope.launch {
      _uiState.update { it.copy(togglingIds = it.togglingIds + link.id) }
      try {
        val updated = repository.patchLink(
          id = link.id,
          patch = LinkPatch(isActive = nextActive, hasIsActive = true),
        )
        _uiState.update { state ->
          state.copy(
            togglingIds = state.togglingIds - link.id,
            links = state.links.map { if (it.id == updated.id) updated else it },
          )
        }
      } catch (e: Exception) {
        _uiState.update { it.copy(togglingIds = it.togglingIds - link.id) }
        _events.emit(DashboardEvent.ShowMessage(e.message ?: "Failed to update link status."))
      }
    }
  }

  fun openCreateForm() {
    val draftUrl = _uiState.value.quickUrl.trim()
    val prefill = if (draftUrl.isNotEmpty()) ValidationUtils.normalizeTargetUrlInput(draftUrl) else ""
    _uiState.update {
      it.copy(
        isFormOpen = true,
        formTargetLink = null,
        formInitialUrl = prefill,
        formError = null,
      )
    }
  }

  fun openEditForm(link: LinkRecord) {
    _uiState.update {
      it.copy(
        isFormOpen = true,
        formTargetLink = link,
        formInitialUrl = "",
        formError = null,
      )
    }
  }

  fun closeForm() {
    _uiState.update {
      it.copy(isFormOpen = false, formTargetLink = null, formError = null, isFormSubmitting = false)
    }
  }

  fun createFromForm(request: CreateLinkRequest) {
    viewModelScope.launch {
      _uiState.update { it.copy(isFormSubmitting = true, formError = null) }
      try {
        val created = repository.createLink(request)
        val shortUrl = ValidationUtils.buildShortUrl(repository.session.value.config.baseUrl, created.slug)
        _uiState.update {
          it.copy(
            isFormOpen = false,
            isFormSubmitting = false,
            quickUrl = "",
            offset = 0,
          )
        }
        _events.emit(DashboardEvent.LinkShortenedAndCopied(created, shortUrl))
        fetchLinks(isRefresh = false)
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isFormSubmitting = false, formError = e.message ?: "Failed to create link.")
        }
      }
    }
  }

  fun patchFromForm(id: String, patch: LinkPatch) {
    viewModelScope.launch {
      _uiState.update { it.copy(isFormSubmitting = true, formError = null) }
      try {
        val updated = repository.patchLink(id, patch)
        _uiState.update { state ->
          state.copy(
            isFormOpen = false,
            isFormSubmitting = false,
            formTargetLink = null,
            links = state.links.map { if (it.id == updated.id) updated else it },
          )
        }
        _events.emit(DashboardEvent.ShowMessage("Updated /${updated.slug}"))
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isFormSubmitting = false, formError = e.message ?: "Failed to update link.")
        }
      }
    }
  }

  fun openQrModal(link: LinkRecord) {
    _uiState.update { it.copy(qrTargetLink = link) }
  }

  fun closeQrModal() {
    _uiState.update { it.copy(qrTargetLink = null) }
  }

  fun requestDelete(link: LinkRecord) {
    _uiState.update { it.copy(deleteTargetLink = link) }
  }

  fun cancelDelete() {
    _uiState.update { it.copy(deleteTargetLink = null, isDeleting = false) }
  }

  fun confirmDelete() {
    val target = _uiState.value.deleteTargetLink ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isDeleting = true) }
      try {
        repository.deleteLink(target.id)
        _uiState.update { it.copy(deleteTargetLink = null, isDeleting = false) }
        _events.emit(DashboardEvent.ShowMessage("Deleted /${target.slug}"))
        fetchLinks(isRefresh = false)
      } catch (e: Exception) {
        _uiState.update { it.copy(deleteTargetLink = null, isDeleting = false) }
        _events.emit(DashboardEvent.ShowMessage(e.message ?: "Failed to delete link."))
      }
    }
  }

  private fun fetchLinks(isRefresh: Boolean, searchOverride: String? = null) {
    val state = _uiState.value
    val effectiveSearch = searchOverride ?: state.searchQuery
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isLoading = !isRefresh && it.links.isEmpty(),
          isRefreshing = isRefresh,
          error = null,
        )
      }
      try {
        val res = repository.listLinks(
          search = effectiveSearch,
          tag = state.activeTag,
          statusFilter = state.statusFilter,
          sortOption = state.sortOption,
          limit = ValidationUtils.PAGE_SIZE,
          offset = state.offset,
        )
        _uiState.update {
          it.copy(
            links = res.data,
            total = res.pagination.total,
            offset = res.pagination.offset,
            hasMore = res.pagination.hasMore,
            isLoading = false,
            isRefreshing = false,
            error = null,
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isLoading = false,
            isRefreshing = false,
            error = e.message ?: "Failed to load links.",
          )
        }
      }
    }
  }
}
