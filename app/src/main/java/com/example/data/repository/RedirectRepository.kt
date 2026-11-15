package com.example.data.repository

import com.example.data.network.AccessSessionExpiredException
import com.example.data.network.RedirectApiClient
import com.example.data.network.RedirectApiException
import com.example.data.storage.SecurePreferences
import com.example.domain.model.CreateLinkRequest
import com.example.domain.model.CreateTokenRequest
import com.example.domain.model.CreateTokenResponse
import com.example.domain.model.DeleteResult
import com.example.domain.model.ExportedLink
import com.example.domain.model.ImportConflictMode
import com.example.domain.model.ImportLinkInput
import com.example.domain.model.ImportLinksResponse
import com.example.domain.model.LinkPatch
import com.example.domain.model.LinkRecord
import com.example.domain.model.LinkSortOption
import com.example.domain.model.LinkStatsResponse
import com.example.domain.model.PaginatedLinks
import com.example.domain.model.Principal
import com.example.domain.model.RevokeAllTokensResponse
import com.example.domain.model.RotateInitialTokenResponse
import com.example.domain.model.ServerConfig
import com.example.domain.model.StatusFilter
import com.example.domain.model.TokenInfo
import com.example.domain.util.ValidationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SessionState(
  val isBootstrapping: Boolean = true,
  val isConnecting: Boolean = false,
  val isAuthenticated: Boolean = false,
  val config: ServerConfig = ServerConfig(),
  val principal: Principal? = null,
  val accessExpired: Boolean = false,
  val kvPropagationWarning: String? = null,
  val authError: String? = null,
)

class RedirectRepository(
  private val prefs: SecurePreferences,
  private val api: RedirectApiClient,
) {
  private val _session = MutableStateFlow(SessionState(config = prefs.loadConfig()))
  val session: StateFlow<SessionState> = _session.asStateFlow()

  suspend fun bootstrapVerify() {
    val saved = prefs.loadConfig()
    if (saved.baseUrl.isBlank() || saved.apiToken.isBlank()) {
      _session.update {
        it.copy(
          isBootstrapping = false,
          isAuthenticated = false,
          config = saved,
          principal = null,
        )
      }
      return
    }

    _session.update { it.copy(isBootstrapping = true, config = saved, authError = null) }
    try {
      val principal = api.verifyAuth(saved)
      _session.update {
        it.copy(
          isBootstrapping = false,
          isAuthenticated = true,
          principal = principal,
          accessExpired = false,
          kvPropagationWarning = null,
          authError = null,
        )
      }
    } catch (e: AccessSessionExpiredException) {
      _session.update {
        it.copy(
          isBootstrapping = false,
          isAuthenticated = true, // Keep user inside so banner guides fixing Zero Trust
          accessExpired = true,
        )
      }
    } catch (e: RedirectApiException) {
      if (e.status == 401 && isWithinKvGraceWindow(saved.tokenSetAtMillis)) {
        _session.update {
          it.copy(
            isBootstrapping = false,
            isAuthenticated = true,
            kvPropagationWarning =
              "Token was saved recently and may still be propagating across Cloudflare KV edge nodes (up to 60s).",
          )
        }
      } else {
        prefs.clearTokenKeepUrl()
        _session.update {
          it.copy(
            isBootstrapping = false,
            isAuthenticated = false,
            config = prefs.loadConfig(),
            principal = null,
            authError = e.message,
          )
        }
      }
    } catch (e: Exception) {
      // Network offline or DNS issue at launch — keep config and let user retry
      _session.update {
        it.copy(
          isBootstrapping = false,
          isAuthenticated = true,
          authError = null,
        )
      }
    }
  }

  suspend fun connect(
    rawBaseUrl: String,
    rawToken: String,
    cfClientId: String,
    cfClientSecret: String,
  ): Result<Principal> {
    val normalizedUrl = ValidationUtils.normalizeServerBaseUrl(rawBaseUrl)
    val token = rawToken.trim()
    val urlErr = ValidationUtils.validateServerBaseUrl(normalizedUrl)
    if (urlErr != null) {
      _session.update { it.copy(authError = urlErr) }
      return Result.failure(IllegalArgumentException(urlErr))
    }
    if (token.isEmpty()) {
      val msg = "API Token is required."
      _session.update { it.copy(authError = msg) }
      return Result.failure(IllegalArgumentException(msg))
    }

    val candidate = ServerConfig(
      baseUrl = normalizedUrl,
      apiToken = token,
      tokenSetAtMillis = System.currentTimeMillis(),
      cfAccessClientId = cfClientId.trim(),
      cfAccessClientSecret = cfClientSecret.trim(),
    )

    _session.update { it.copy(isConnecting = true, authError = null) }
    return try {
      val principal = api.verifyAuth(candidate)
      prefs.saveConfig(candidate)
      _session.update {
        it.copy(
          isConnecting = false,
          isAuthenticated = true,
          config = candidate,
          principal = principal,
          accessExpired = false,
          kvPropagationWarning = null,
          authError = null,
        )
      }
      Result.success(principal)
    } catch (e: AccessSessionExpiredException) {
      _session.update {
        it.copy(
          isConnecting = false,
          accessExpired = true,
          authError = e.message,
        )
      }
      Result.failure(e)
    } catch (e: Exception) {
      _session.update {
        it.copy(
          isConnecting = false,
          authError = e.message ?: "Failed to connect to server.",
        )
      }
      Result.failure(e)
    }
  }

  suspend fun pingForInitialToken(
    rawBaseUrl: String,
    cfClientId: String,
    cfClientSecret: String,
  ): Result<Unit> {
    val normalizedUrl = ValidationUtils.normalizeServerBaseUrl(rawBaseUrl)
    val urlErr = ValidationUtils.validateServerBaseUrl(normalizedUrl)
    if (urlErr != null) return Result.failure(IllegalArgumentException(urlErr))
    return try {
      api.bootstrapInitialTokenPing(normalizedUrl, cfClientId, cfClientSecret)
      _session.update { it.copy(accessExpired = false) }
      Result.success(Unit)
    } catch (e: AccessSessionExpiredException) {
      _session.update { it.copy(accessExpired = true) }
      Result.failure(e)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun updateActiveToken(newToken: String) {
    val now = System.currentTimeMillis()
    prefs.updateToken(newToken.trim(), now)
    val updated = prefs.loadConfig()
    _session.update {
      it.copy(
        config = updated,
        kvPropagationWarning = null,
      )
    }
    try {
      val principal = api.verifyAuth(updated)
      _session.update { it.copy(principal = principal, accessExpired = false) }
    } catch (_: Exception) {
      // Keep updated token even if edge KV is still propagating
    }
  }

  fun updateZeroTrustHeaders(clientId: String, clientSecret: String) {
    val current = prefs.loadConfig()
    val updated = current.copy(
      cfAccessClientId = clientId.trim(),
      cfAccessClientSecret = clientSecret.trim(),
    )
    prefs.saveConfig(updated)
    _session.update { it.copy(config = updated, accessExpired = false) }
  }

  fun logout() {
    prefs.clearTokenKeepUrl()
    val updated = prefs.loadConfig()
    _session.update {
      SessionState(
        isBootstrapping = false,
        isAuthenticated = false,
        config = updated,
      )
    }
  }

  fun dismissAccessBanner() {
    _session.update { it.copy(accessExpired = false) }
  }

  fun dismissKvWarning() {
    _session.update { it.copy(kvPropagationWarning = null) }
  }

  suspend fun <T> runAuthenticated(block: suspend (ServerConfig) -> T): T {
    val config = _session.value.config
    return try {
      val res = block(config)
      if (_session.value.accessExpired || _session.value.kvPropagationWarning != null) {
        _session.update { it.copy(accessExpired = false, kvPropagationWarning = null) }
      }
      res
    } catch (e: AccessSessionExpiredException) {
      _session.update { it.copy(accessExpired = true) }
      throw e
    } catch (e: RedirectApiException) {
      if (e.status == 401) {
        if (isWithinKvGraceWindow(config.tokenSetAtMillis)) {
          _session.update {
            it.copy(
              kvPropagationWarning =
                "Token was saved recently and may still be propagating across Cloudflare KV edge nodes (up to 60s).",
            )
          }
        } else {
          prefs.clearTokenKeepUrl()
          _session.update {
            SessionState(
              isBootstrapping = false,
              isAuthenticated = false,
              config = prefs.loadConfig(),
              authError = "Session expired or token revoked (401 Unauthorized).",
            )
          }
        }
      }
      throw e
    }
  }

  suspend fun listLinks(
    search: String = "",
    tag: String? = null,
    slug: String? = null,
    targetUrl: String? = null,
    statusFilter: StatusFilter = StatusFilter.ALL,
    sortOption: LinkSortOption = LinkSortOption.NEWEST,
    limit: Int = ValidationUtils.PAGE_SIZE,
    offset: Int = 0,
  ): PaginatedLinks = runAuthenticated { cfg ->
    api.listLinks(cfg, search, tag, slug, targetUrl, statusFilter, sortOption, limit, offset)
  }

  private val _linksVersion = MutableStateFlow(0)
  val linksVersion: StateFlow<Int> = _linksVersion.asStateFlow()

  suspend fun createLink(request: CreateLinkRequest): LinkRecord = runAuthenticated { cfg ->
    api.createLink(cfg, request).also { _linksVersion.update { v -> v + 1 } }
  }

  suspend fun patchLink(id: String, patch: LinkPatch): LinkRecord = runAuthenticated { cfg ->
    api.patchLink(cfg, id, patch).also { _linksVersion.update { v -> v + 1 } }
  }

  suspend fun deleteLink(id: String): DeleteResult = runAuthenticated { cfg ->
    api.deleteLink(cfg, id).also { _linksVersion.update { v -> v + 1 } }
  }

  suspend fun getLinkStats(id: String, days: Int): LinkStatsResponse = runAuthenticated { cfg ->
    api.getLinkStats(cfg, id, days)
  }

  suspend fun listTokens(): List<TokenInfo> = runAuthenticated { cfg ->
    api.listTokens(cfg)
  }

  suspend fun createToken(request: CreateTokenRequest): CreateTokenResponse = runAuthenticated { cfg ->
    api.createToken(cfg, request)
  }

  suspend fun revokeToken(id: String): DeleteResult = runAuthenticated { cfg ->
    api.revokeToken(cfg, id)
  }

  suspend fun revokeAllTokens(): RevokeAllTokensResponse = runAuthenticated { cfg ->
    api.revokeAllTokens(cfg)
  }

  suspend fun rotateInitialToken(): RotateInitialTokenResponse = runAuthenticated { cfg ->
    val rotated = api.rotateInitialToken(cfg)
    updateActiveToken(rotated.token)
    rotated
  }

  suspend fun exportAllLinks(onPageFetched: (Int) -> Unit = {}): Pair<List<ExportedLink>, String> =
    runAuthenticated { cfg ->
      api.exportAllLinks(cfg, onPageFetched)
    }

  suspend fun importLinksChunked(
    links: List<ImportLinkInput>,
    onConflict: ImportConflictMode,
    onProgress: (Int, Int) -> Unit,
  ): ImportLinksResponse = runAuthenticated { cfg ->
    api.importLinksChunked(cfg, links, onConflict, onProgress).also {
      _linksVersion.update { v -> v + 1 }
    }
  }

  private fun isWithinKvGraceWindow(tokenSetAtMillis: Long): Boolean {
    if (tokenSetAtMillis <= 0L) return false
    return (System.currentTimeMillis() - tokenSetAtMillis) < ValidationUtils.KV_PROPAGATION_GRACE_MS
  }
}
