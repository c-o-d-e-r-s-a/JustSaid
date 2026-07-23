package com.justsaid.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Deliberately does nothing. Its only job is to EXIST as an *enabled* accessibility
 * service: since Android 10 the concurrent-capture policy feeds third-party microphone
 * clients pure silence while a cellular call is active (verified on Samsung One UI —
 * every capture tier recorded exact zeros), but apps with an enabled accessibility
 * service are exempt. This is the sanctioned mechanism on-device call recorders rely
 * on, and it keeps the app 100% offline.
 *
 * Privacy posture: the service config (`res/xml/capture_unlock_service.xml`) sets
 * `canRetrieveWindowContent="false"` and the narrowest event mask; this class ignores
 * every callback. It never reads the screen, input, or other apps.
 */
class CaptureUnlockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
