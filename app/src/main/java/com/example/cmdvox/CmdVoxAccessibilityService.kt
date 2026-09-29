package com.example.cmdvox

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Extension point for future semantic accessibility actions; no coordinate automation is used. */
class CmdVoxAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
