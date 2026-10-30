package de.dgstudios.island

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class IslandAccessibilityService : AccessibilityService() {
    private var host: IslandHost? = null
    override fun onServiceConnected() {
        active = true
        stopService(Intent(this, OverlayService::class.java)) // Bedienungshilfe hat Vorrang
        host = IslandHost(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY).also { it.start() }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); host?.overlay?.onConfigChanged() }
    override fun onUnbind(intent: Intent?): Boolean { active = false; host?.stop(); host = null; return super.onUnbind(intent) }
    override fun onDestroy() { active = false; host?.stop(); host = null; super.onDestroy() }
    companion object { @Volatile var active = false }
}
