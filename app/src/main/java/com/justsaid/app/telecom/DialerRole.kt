package com.justsaid.app.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager

/**
 * Helpers for the `ROLE_DIALER` request flow. Being the default dialer is what lets the
 * telecom framework bind [JustSaidInCallService]; JustSaid never asks for more.
 */
object DialerRole {

    /** True if this app already holds the default-dialer role. */
    fun isDefault(context: Context): Boolean {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        return telecom.defaultDialerPackage == context.packageName
    }

    /**
     * Intent that prompts the user to make JustSaid the default phone app. Returns null when
     * the role is unavailable or already held, so callers can skip the prompt.
     */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (isDefault(context)) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
        if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    }
}
