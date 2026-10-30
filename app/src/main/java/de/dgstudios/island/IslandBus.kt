package de.dgstudios.island

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow

data class NotifItem(
    val key: String,
    val pkg: String,
    val appIcon: Drawable?,
    val largeIcon: Drawable?,
    val title: String,
    val text: String,
    val intent: PendingIntent?,
    val isCall: Boolean,
    val actions: List<Pair<String, PendingIntent>>,
    val chronoBase: Long? = null,   // Wanduhr-Zeit (ms) für Timer/Stoppuhr
    val chronoCountDown: Boolean = false,
)

data class MediaItem(
    val pkg: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val controller: MediaController,
)

/** Zentraler Zustand, den Listener schreiben und die Insel liest. */
object IslandBus {
    val transient = MutableStateFlow<NotifItem?>(null)   // kurz eingeblendete Mitteilung
    val ongoing = MutableStateFlow<NotifItem?>(null)     // Anruf oder Timer
    val media = MutableStateFlow<MediaItem?>(null)
    val charging = MutableStateFlow<Int?>(null)          // Akku-% kurz nach Einstecken
    val settingsTick = MutableStateFlow(0)
    val fullscreen = MutableStateFlow(false)
    fun settingsChanged() { settingsTick.value++ }
}
