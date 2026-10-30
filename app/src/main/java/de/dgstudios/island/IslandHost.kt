package de.dgstudios.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper

/** Hält Insel + Lade-Erkennung für einen Dienst (Bedienungshilfe oder Vordergrund-Dienst). */
class IslandHost(private val ctx: Context, type: Int) {
    val overlay = IslandOverlay(ctx, type)
    private val main = Handler(Looper.getMainLooper())
    private val clearCharge = Runnable { IslandBus.charging.value = null }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_POWER_CONNECTED) {
                main.postDelayed({
                    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    IslandBus.charging.value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    main.removeCallbacks(clearCharge); main.postDelayed(clearCharge, 4000)
                }, 300)
            } else if (i.action == Intent.ACTION_POWER_DISCONNECTED) IslandBus.charging.value = null
        }
    }

    fun start() {
        overlay.attach()
        val f = IntentFilter().apply { addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED) }
        ctx.registerReceiver(receiver, f)
        running = true
    }

    fun stop() {
        try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
        main.removeCallbacksAndMessages(null)
        overlay.detach()
        running = false
    }

    companion object { @Volatile var running = false }
}
