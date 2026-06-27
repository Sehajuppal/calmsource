package com.example.calmsource.feature.iptv.xtream

import com.example.calmsource.core.model.XtreamSyncProgress
import com.example.calmsource.feature.iptv.XtreamApiClient
import com.example.calmsource.feature.iptv.XtreamRepository
import com.example.calmsource.feature.iptv.XtreamSyncService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Orchestrates full Xtream provider sync: authenticate → live → VOD → series.
 *
 * Delegates actual database writes to [XtreamRepository.syncProvider] which
 * handles batching and credential lookup. This class is the entry point
 * for UI / background-job callers.
 *
 * **Progress**: Exposed via [syncProgress] which delegates to the
 * single source-of-truth [XtreamRepository.syncProgress]. No duplicate
 * StateFlow is maintained here — the repository emits all stage
 * transitions (IDLE → VALIDATING → … → COMPLETE / FAILED) and this
 * service simply ensures the terminal state is correct on error.
 */
class XtreamSyncServiceImpl(
    private val apiClient: XtreamApiClient,
    private val repository: XtreamRepository
) : XtreamSyncService {

    /**
     * Sync progress, delegated to [XtreamRepository.syncProgress].
     *
     * Both the UI and callers should observe this single StateFlow
     * rather than maintaining a separate copy.
     */
    val syncProgress: StateFlow<XtreamSyncProgress?> get() = repository.syncProgress

    override suspend fun syncProvider(providerId: String) = withContext(Dispatchers.IO) {
        try {
            // Delegate the full sync to the repository which owns DB access,
            // credential lookup via SecureTokenStore, and progress emission.
            repository.syncProvider(providerId, apiClient)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The repository has already emitted a sanitized FAILED state.
            throw e
        }
    }
}
