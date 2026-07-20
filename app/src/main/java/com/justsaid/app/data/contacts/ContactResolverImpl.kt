package com.justsaid.app.data.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import com.justsaid.app.core.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContactsContract-backed resolver. Uses `PhoneLookup` (indexed, number-normalized) so it
 * matches regardless of formatting. Read-only; never writes contacts (Permissions Policy).
 */
@Singleton
class ContactResolverImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContactResolver {

    override suspend fun resolve(phoneNumber: String): String? = withContext(ioDispatcher) {
        if (phoneNumber.isBlank()) return@withContext null
        val uri: Uri = Uri.withAppendedPath(
            PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )
        try {
            context.contentResolver.query(
                uri,
                arrayOf(Phone.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(Phone.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
        } catch (e: SecurityException) {
            // READ_CONTACTS not granted — degrade gracefully to the raw number.
            null
        }
    }
}
