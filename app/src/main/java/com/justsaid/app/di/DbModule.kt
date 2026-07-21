package com.justsaid.app.di

import android.content.Context
import androidx.room.Room
import com.justsaid.app.data.db.JustSaidDatabase
import com.justsaid.app.data.db.SqlCipherFactory
import com.justsaid.app.data.db.SummaryDao
import com.justsaid.app.data.repo.RoomSummaryRepo
import com.justsaid.app.data.repo.SummaryRepo
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 5 storage wiring: the SQLCipher-encrypted Room database and the real
 * [SummaryRepo] (replaces Phase 4's in-memory stand-in).
 */
@Module
@InstallIn(SingletonComponent::class)
object DbModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        sqlCipherFactory: SqlCipherFactory,
    ): JustSaidDatabase =
        Room.databaseBuilder(context, JustSaidDatabase::class.java, "justsaid.db")
            .openHelperFactory(sqlCipherFactory.create())
            .build()

    @Provides
    fun provideSummaryDao(db: JustSaidDatabase): SummaryDao = db.summaryDao()
}

/** Binds the encrypted-store repo as the app-wide [SummaryRepo]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DbBindsModule {

    @Binds
    @Singleton
    abstract fun bindSummaryRepo(impl: RoomSummaryRepo): SummaryRepo
}
