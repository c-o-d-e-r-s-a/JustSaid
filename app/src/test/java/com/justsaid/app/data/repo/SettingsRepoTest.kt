package com.justsaid.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class SettingsRepoTest {

    private lateinit var tempDir: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepo

    @Before
    fun setUp() {
        tempDir = createTempDir(prefix = "justsaid_settings_test")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir, "settings.preferences_pb")
        }
        repo = SettingsRepo(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun `defaults are safe and private on a fresh install`() = runTest {
        assertThat(repo.legalAccepted.first()).isFalse()
        assertThat(repo.ttsNoticeEnabled.first()).isFalse()
        assertThat(repo.autoCleanupEnabled.first()).isFalse()
        assertThat(repo.alwaysListen.first()).isFalse()
        assertThat(repo.sttLanguageLock.first()).isEqualTo(SttLanguageLock.AUTO)
    }

    @Test
    fun `setters round-trip through the store`() = runTest {
        repo.setLegalAccepted(true)
        repo.setTtsNoticeEnabled(true)
        repo.setAutoCleanupEnabled(true)
        repo.setAlwaysListen(true)
        repo.setSttLanguageLock(SttLanguageLock.EN)

        assertThat(repo.legalAccepted.first()).isTrue()
        assertThat(repo.ttsNoticeEnabled.first()).isTrue()
        assertThat(repo.autoCleanupEnabled.first()).isTrue()
        assertThat(repo.alwaysListen.first()).isTrue()
        assertThat(repo.sttLanguageLock.first()).isEqualTo(SttLanguageLock.EN)
    }
}
