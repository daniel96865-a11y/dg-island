package de.dgstudios.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.view.WindowManager

/** Ersatzweg ohne Bedienungshilfe: "Über anderen Apps anzeigen". */
class OverlayService : Service() {
    private var host: IslandHost? = null
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("island", "Insel aktiv", NotificationManager.IMPORTANCE_MIN))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "island").setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("DG Island läuft").setContentText("Tippen für Einstellungen").setContentIntent(pi).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        if (Settings.canDrawOverlays(this) && !IslandAccessibilityService.active)
            host = IslandHost(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY).also { it.start() }
        else stopSelf()
    }

    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); host?.overlay?.onConfigChanged() }
    override fun onDestroy() { host?.stop(); host = null; super.onDestroy() }

    companion object {
        fun startIfNeeded(ctx: Context) {
            val p = Prefs(ctx)
            if (!p.enabled || IslandAccessibilityService.active || IslandHost.running || !Settings.canDrawOverlays(ctx)) return
            try { ctx.startForegroundService(Intent(ctx, OverlayService::class.java)) } catch (_: Exception) {}
        }
    }
}
