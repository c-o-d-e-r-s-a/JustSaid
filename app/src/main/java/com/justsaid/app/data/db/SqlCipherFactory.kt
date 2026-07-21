package com.justsaid.app.data.db

import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.justsaid.app.core.DbPassphraseProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vends the SQLCipher open-helper factory Room uses to open the encrypted DB.
 * The passphrase comes from Keystore-backed [DbPassphraseProvider] (Phase 1,
 * `Crypto.DB_KEY_ALIAS`) and is handed to SQLCipher as raw bytes — it is never
 * written anywhere else.
 */
@Singleton
class SqlCipherFactory @Inject constructor(
    private val passphraseProvider: DbPassphraseProvider,
) {
    fun create(): SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher")
        return SupportOpenHelperFactory(passphraseProvider.passphrase())
    }
}
