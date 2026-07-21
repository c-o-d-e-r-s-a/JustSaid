package com.justsaid.app.di

import com.justsaid.app.llm.LlamaSummarizer
import com.justsaid.app.llm.LlmSummarizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 4 wiring: the real llama.cpp summarizer. The [com.justsaid.app.data.repo.SummaryRepo]
 * binding lives in [DbBindsModule] (Phase 5's Room/SQLCipher implementation).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLlmSummarizer(impl: LlamaSummarizer): LlmSummarizer
}
