package de.dgstudios.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        // Die Bedienungshilfe startet Android selbst neu; sonst den Overlay-Dienst.
        if (Prefs(c).autostart) OverlayService.startIfNeeded(c)
    }
}
