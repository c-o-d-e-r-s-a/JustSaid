package com.justsaid.app.data.contacts

/**
 * Resolves a raw phone number to a saved contact's display name via the Contacts
 * ContentProvider. Returns null when there is no match or READ_CONTACTS is not granted
 * (the caller then renders the raw number). Interface so tests inject a fake with no device.
 */
interface ContactResolver {
    suspend fun resolve(phoneNumber: String): String?
}
