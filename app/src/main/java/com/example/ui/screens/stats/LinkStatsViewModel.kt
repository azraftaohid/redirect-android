package com.example.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.RedirectRepository
import com.example.domain.model.DailyClickPoint
import com.example.domain.model.LinkPatch
import com.example.domain.model.LinkRecord
import com.example.domain.model.LinkStatsResponse
import com.example.domain.util.StatsUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LinkStatsUiState(
  val selectedDays: Int = 30,
  val isLoading: Boolean = true,
  val error: String? = null,
  val stats: LinkStatsResponse? = null,
  val zeroFilledDaily: List<DailyClickPoint> = emptyList(),
  val periodClicks: Int = 0,
  val peakDay: DailyClickPoint? = null,
  val isEditOpen: Boolean = false,
  val isEditSubmitting: Boolean = false,
  val editError: String? = null,
  val showQrModal: Boolean = false,
)

class LinkStatsViewModel(
  private val linkId: String,
  private val repository: RedirectRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(LinkStatsUiState())
  val uiState: StateFlow<LinkStatsUiState> = _uiState.asStateFlow()

  init {
    loadStats(30)
  }

  fun selectPeriod(days: Int) {
    if (days == _uiState.value.selectedDays && _uiState.value.stats != null) return
    _uiState.update { it.copy(selectedDays = days) }
    loadStats(days)
  }

  fun refresh() {
    loadStats(_uiState.value.selectedDays)
  }

  fun openEdit() {
    _uiState.update { it.copy(isEditOpen = true, editError = null) }
  }

  fun closeEdit() {
    _uiState.update { it.copy(isEditOpen = false, editError = null, isEditSubmitting = false) }
  }

  fun openQr() {
    _uiState.update { it.copy(showQrModal = true) }
  }

  fun closeQr() {
    _uiState.update { it.copy(showQrModal = false) }
  }

  fun saveEdit(id: String, patch: LinkPatch) {
    viewModelScope.launch {
      _uiState.update { it.copy(isEditSubmitting = true, editError = null) }
      try {
        val updated: LinkRecord = repository.patchLink(id, patch)
        _uiState.update { state ->
          state.copy(
            isEditOpen = false,
            isEditSubmitting = false,
            stats = state.stats?.copy(link = updated),
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isEditSubmitting = false, editError = e.message ?: "Failed to update link.")
        }
      }
    }
  }

  private fun loadStats(days: Int) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      try {
        val res = repository.getLinkStats(linkId, days)
        val filled = StatsUtils.zeroFillDailySeries(res.range.from, res.range.to, res.daily)
        val periodTotal = filled.sumOf { it.clicks }
        val peak = filled.maxByOrNull { it.clicks }?.takeIf { it.clicks > 0 }
        _uiState.update {
          it.copy(
            isLoading = false,
            error = null,
            stats = res,
            zeroFilledDaily = filled,
            periodClicks = periodTotal,
            peakDay = peak,
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isLoading = false,
            error = e.message ?: "Failed to load analytics.",
          )
        }
      }
    }
  }
}
