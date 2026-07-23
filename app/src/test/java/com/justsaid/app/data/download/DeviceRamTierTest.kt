package com.justsaid.app.data.download

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.data.repo.SttLanguageLock
import org.junit.Test

class DeviceRamTierTest {

    @Test
    fun `phones at or under 4 GiB get the light tier`() {
        assertThat(selectRamTier(3_688_944L * 1024)).isEqualTo(DeviceRamTier.LIGHT) // ~A14
        assertThat(selectRamTier(LIGHT_RAM_CEILING_BYTES)).isEqualTo(DeviceRamTier.LIGHT)
        assertThat(selectRamTier(0)).isEqualTo(DeviceRamTier.LIGHT)
    }

    @Test
    fun `phones above 4 GiB get the standard tier`() {
        assertThat(selectRamTier(LIGHT_RAM_CEILING_BYTES + 1)).isEqualTo(DeviceRamTier.STANDARD)
        assertThat(selectRamTier(8L * 1024 * 1024 * 1024)).isEqualTo(DeviceRamTier.STANDARD)
    }

    @Test
    fun `light tier requires the 1B summarizer`() {
        val specs = ModelCatalog.requiredFor(SttLanguageLock.AUTO, DeviceRamTier.LIGHT)
        assertThat(specs.map { it.fileName }).containsExactly(
            ModelCatalog.STT_BASE_MULTILINGUAL.fileName,
            ModelCatalog.LLM_1B.fileName,
        ).inOrder()
    }

    @Test
    fun `standard tier requires the 3B summarizer`() {
        val specs = ModelCatalog.requiredFor(SttLanguageLock.EN, DeviceRamTier.STANDARD)
        assertThat(specs.map { it.fileName }).containsExactly(
            ModelCatalog.STT_SMALL_EN.fileName,
            ModelCatalog.LLM_3B.fileName,
        ).inOrder()
    }

    @Test
    fun `llmFor maps tiers to distinct artifacts`() {
        assertThat(ModelCatalog.llmFor(DeviceRamTier.LIGHT)).isEqualTo(ModelCatalog.LLM_1B)
        assertThat(ModelCatalog.llmFor(DeviceRamTier.STANDARD)).isEqualTo(ModelCatalog.LLM_3B)
        assertThat(ModelCatalog.ALL_LLMS).containsExactly(ModelCatalog.LLM_1B, ModelCatalog.LLM_3B)
    }
}
