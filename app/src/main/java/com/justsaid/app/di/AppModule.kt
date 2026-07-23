package com.justsaid.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.core.DeviceRamInfo
import com.justsaid.app.core.IoDispatcher
import com.justsaid.app.core.ModelPaths
import com.justsaid.app.data.DeviceRamInfoImpl
import com.justsaid.app.data.ModelPathsImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Provides concrete singletons (dispatchers, OkHttp, DataStore). */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * OkHttp for the model downloader only. No disk cache (privacy) and no overall
     * call timeout, since model files are large and may take minutes on slow links.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("justsaid_settings") },
        )
}

/** Binds interfaces to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {

    @Binds
    @Singleton
    abstract fun bindModelPaths(impl: ModelPathsImpl): ModelPaths

    @Binds
    @Singleton
    abstract fun bindDeviceRamInfo(impl: DeviceRamInfoImpl): DeviceRamInfo
}
