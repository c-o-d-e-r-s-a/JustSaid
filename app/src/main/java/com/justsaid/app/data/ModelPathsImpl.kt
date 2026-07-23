package com.justsaid.app.data

import android.content.Context
import com.justsaid.app.core.DeviceRamInfo
import com.justsaid.app.core.ModelPaths
import com.justsaid.app.data.download.ModelCatalog
import com.justsaid.app.data.download.selectRamTier
import com.justsaid.app.data.repo.SettingsRepo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [ModelPaths] backed by app-private `filesDir/models/`.
 *
 * The STT model depends on the current language setting; the LLM depends on the
 * device's total RAM tier ([selectRamTier]). Language is read one-shot via
 * [runBlocking] — callers invoke these on a background dispatcher (e.g. the
 * onboarding gate), never the main thread.
 */
@Singleton
class ModelPathsImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepo: SettingsRepo,
    private val deviceRamInfo: DeviceRamInfo,
) : ModelPaths {

    private val modelsDir = File(context.filesDir, ModelCatalog.MODELS_DIR)

    override fun sttModelFile(): File =
        File(modelsDir, ModelCatalog.sttFor(currentLanguageLock()).fileName)

    override fun llmModelFile(): File =
        File(modelsDir, ModelCatalog.llmFor(currentRamTier()).fileName)

    override fun modelsReady(): Boolean =
        sttModelFile().exists() && llmModelFile().exists()

    private fun currentLanguageLock() =
        runBlocking { settingsRepo.sttLanguageLock.first() }

    private fun currentRamTier() = selectRamTier(deviceRamInfo.totalRamBytes())
}
