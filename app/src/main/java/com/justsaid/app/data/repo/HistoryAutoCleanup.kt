package com.justsaid.app.data.repo

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text Lifecycle T2: when the (OFF by default) auto-cleanup setting is on,
 * summaries older than 30 days are deleted on app start.
 */
@Singleton
class HistoryAutoCleanup @Inject constructor(
    private val settingsRepo: SettingsRepo,
    private val summaryRepo: RoomSummaryRepo,
) {

    suspend fun runIfEnabled() {
        if (settingsRepo.autoCleanupEnabled.first()) {
            summaryRepo.deleteOlderThan(System.currentTimeMillis() - RETENTION_MS)
        }
    }

    private companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
