package com.justsaid.app.core

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pref key under which the SQLCipher DB passphrase is stored inside
 * [EncryptedSharedPreferences]. Phase 5's SQLCipher factory reads this exact key.
 * Defined here (Phase 1) as the single source of truth; do not redefine elsewhere.
 */
const val DB_KEY_ALIAS: String = "justsaid_db_passphrase"

/** File name of the encrypted prefs; excluded from backups (see res/xml/backup_rules.xml). */
private const val SECURE_PREFS_FILE = "justsaid_secure_prefs"

private const val PASSPHRASE_BYTES = 32

/**
 * Provisions and vends the 32-byte database passphrase.
 *
 * The passphrase is generated once on first run with [SecureRandom] and stored in
 * Keystore-backed [EncryptedSharedPreferences]. It never touches plaintext prefs,
 * logs, or backups. Phase 5 consumes it via [passphrase]; Phase 1 only guarantees
 * it exists.
 */
@Singleton
class DbPassphraseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Creates the passphrase if absent. Idempotent. Safe to call on every launch. */
    fun ensurePassphrase() {
        if (prefs.contains(DB_KEY_ALIAS)) return
        val random = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.encodeToString(random, Base64.NO_WRAP)
        prefs.edit().putString(DB_KEY_ALIAS, encoded).apply()
    }

    /** Returns the raw 32-byte passphrase, provisioning it if needed. */
    fun passphrase(): ByteArray {
        ensurePassphrase()
        val encoded = prefs.getString(DB_KEY_ALIAS, null)
            ?: error("DB passphrase missing after provisioning")
        return Base64.decode(encoded, Base64.NO_WRAP)
    }
}
