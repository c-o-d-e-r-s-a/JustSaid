package com.justsaid.app.telecom

import android.content.Context
import android.content.Intent

/**
 * Inert placeholder kept only so existing onboarding UI compiles until the companion
 * capture screens replace dialer setup (migration task 5). No telecom APIs.
 */
object DialerRole {
    fun isDefault(context: Context): Boolean = false

    fun requestIntent(context: Context): Intent? = null
}
