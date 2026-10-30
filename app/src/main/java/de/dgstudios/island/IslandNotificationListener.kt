package de.dgstudios.island

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class IslandNotificationListener : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = pushMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = pushMedia()
        override fun onSessionDestroyed() { pickController() }
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pickController(it) }

    override fun onListenerConnected() {
        connected = true
        msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val cn = ComponentName(this, IslandNotificationListener::class.java)
        try {
            msm?.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
            pickController(msm?.getActiveSessions(cn))
        } catch (_: SecurityException) {}
        try { activeNotifications?.forEach { handle(it, initial = true) } } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        connected = false
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        controller?.unregisterCallback(cb)
    }

    private fun pickController(list: List<MediaController>? = null) {
        val all = list ?: try { msm?.getActiveSessions(ComponentName(this, IslandNotificationListener::class.java)) } catch (_: SecurityException) { null }
        val best = all?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: all?.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(cb)
            controller = best
            best?.registerCallback(cb, main)
        }
        pushMedia()
    }

    private fun pushMedia() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null || Prefs(this).blocked.contains(c.packageName)) { IslandBus.media.value = null; return }
        val state = c.playbackState?.state
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        if (title.isBlank() || state == PlaybackState.STATE_STOPPED || state == PlaybackState.STATE_NONE) { IslandBus.media.value = null; return }
        IslandBus.media.value = MediaItem(
            pkg = c.packageName,
            title = title,
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING,
            controller = c,
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, initial = false)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (IslandBus.ongoing.value?.key == sbn.key) IslandBus.ongoing.value = null
        if (IslandBus.transient.value?.key == sbn.key) IslandBus.transient.value = null
    }

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        val prefs = Prefs(this)
        if (!prefs.enabled || sbn.packageName == packageName) return
        if (prefs.blocked.contains(sbn.packageName)) return
        val n = sbn.notification
        val ex = n.extras
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return // Musik läuft über MediaSession
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val isCall = n.category == Notification.CATEGORY_CALL
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val isTimer = chrono || n.category == Notification.CATEGORY_STOPWATCH || n.category == "alarm" && sbn.isOngoing
        val ongoing = sbn.isOngoing
        if (ongoing && !isCall && !isTimer) return
        if (initial && !ongoing) return

        val title = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        val appIcon = try { packageManager.getApplicationIcon(sbn.packageName) } catch (_: Exception) { null }
        val large = try { n.getLargeIcon()?.loadDrawable(this) } catch (_: Exception) { null }
        val actions = n.actions?.filter { it.actionIntent != null && it.title != null }
            ?.take(2)?.map { it.title.toString() to it.actionIntent } ?: emptyList()
        val item = NotifItem(
            key = sbn.key, pkg = sbn.packageName, appIcon = appIcon, largeIcon = large,
            title = title.ifBlank { appLabel(sbn.packageName) }, text = text,
            intent = n.contentIntent, isCall = isCall, actions = actions,
            chronoBase = if (chrono && n.`when` > 0) n.`when` else null,
            chronoCountDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),
        )
        seenApps(sbn.packageName)
        if (isCall || isTimer) IslandBus.ongoing.value = item else IslandBus.transient.value = item
    }

    private fun appLabel(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    private fun seenApps(pkg: String) {
        val sp = Prefs(this).sp
        val s = sp.getStringSet("seen", emptySet())!!.toMutableSet()
        if (s.add(pkg)) sp.edit().putStringSet("seen", s).apply()
    }

    companion object { @Volatile var connected = false }
}
