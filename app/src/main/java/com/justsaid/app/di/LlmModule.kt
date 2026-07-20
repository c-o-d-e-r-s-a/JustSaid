package com.justsaid.app.di

import com.justsaid.app.data.repo.InMemorySummaryRepo
import com.justsaid.app.data.repo.SummaryRepo
import com.justsaid.app.llm.LlamaSummarizer
import com.justsaid.app.llm.LlmSummarizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 4 wiring: the real llama.cpp summarizer, and the in-memory stand-in for
 * Phase 5's encrypted store so the full pipeline runs standalone. Phase 5
 * replaces the [SummaryRepo] binding with the Room/SQLCipher implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLlmSummarizer(impl: LlamaSummarizer): LlmSummarizer

    @Binds
    @Singleton
    abstract fun bindSummaryRepo(impl: InMemorySummaryRepo): SummaryRepo
}
