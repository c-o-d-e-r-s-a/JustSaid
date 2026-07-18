package com.justsaid.app

import android.app.Application
import com.justsaid.app.core.DbPassphraseProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. Hilt's object graph roots here.
 *
 * On first run we provision the SQLCipher DB passphrase (Phase 5 reads it) off the
 * main thread. This is idempotent, so running it every launch is harmless.
 */
@HiltAndroidApp
class JustSaidApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            DbPassphraseProvider(applicationContext).ensurePassphrase()
        }
    }
}
