package de.dgstudios.island

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Die eigentliche Insel. Läuft in der Bedienungshilfe (TYPE_ACCESSIBILITY_OVERLAY)
 * oder im Vordergrund-Dienst (TYPE_APPLICATION_OVERLAY).
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class IslandOverlay(private val ctx: Context, private val windowType: Int) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = Prefs(ctx)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)
    private fun dpi(v: Int) = dp(v.toFloat()).toInt()

    private sealed class Show {
        object Idle : Show()
        data class Notif(val n: NotifItem) : Show()
        data class Media(val m: MediaItem) : Show()
        data class Charge(val level: Int) : Show()
    }

    private var show: Show = Show.Idle
    private var expanded = false
    private var attached = false

    private val bg = GradientDrawable().apply { setColor(Color.BLACK) }
    private val root = FrameLayout(ctx)
    private val pill = FrameLayout(ctx).apply { background = bg; clipToOutline = true }

    // eingeklappt
    private val smallRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val smallIcon = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
        outlineProvider = RoundOutline(dp(6f)) }
    private val smallSpacer = View(ctx)
    private val eq = EqualizerView(ctx)
    private val smallText = TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 12f; maxLines = 1 }
    private val smallChrono = Chronometer(ctx).apply { setTextColor(0xFFC15CFF.toInt()); textSize = 12f }

    // ausgeklappt
    private val bigBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; alpha = 0f }
    private val bigIcon = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
        outlineProvider = RoundOutline(dp(12f)) }
    private val bigTitle = TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 15f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private val bigText = TextView(ctx).apply { setTextColor(0xFFB8C4FF.toInt()); textSize = 13f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val bigChrono = Chronometer(ctx).apply { setTextColor(0xFFC15CFF.toInt()); textSize = 22f }
    private val controls = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }

    private val wHolder = FloatValueHolder(0f)
    private val hHolder = FloatValueHolder(0f)
    private val spring = SpringForce().setDampingRatio(0.72f).setStiffness(420f)
    private val wAnim = SpringAnimation(wHolder).setSpring(SpringForce().setDampingRatio(0.72f).setStiffness(420f))
    private val hAnim = SpringAnimation(hHolder).setSpring(spring)
    private var curW = 0f
    private var curH = 0f

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "DG Island"
    }

    private val autoCollapse = Runnable { if (show is Show.Notif && IslandBus.transient.value != null && !(show as Show.Notif).n.isCall) {
        IslandBus.transient.value = null } else setExpanded(false) }

    init {
        smallRow.addView(smallIcon, LinearLayout.LayoutParams(dpi(22), dpi(22)))
        smallRow.addView(smallSpacer, LinearLayout.LayoutParams(0, 1, 1f))
        smallRow.addView(smallText)
        smallRow.addView(smallChrono)
        smallRow.addView(eq, LinearLayout.LayoutParams(dpi(20), dpi(16)))
        smallRow.setPadding(dpi(8), 0, dpi(12), 0)
        pill.addView(smallRow, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(bigIcon, LinearLayout.LayoutParams(dpi(48), dpi(48)))
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(12), 0, 0, 0) }
        col.addView(bigTitle); col.addView(bigText)
        top.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(bigChrono)
        bigBox.addView(top, LinearLayout.LayoutParams(-1, -2))
        bigBox.addView(controls, LinearLayout.LayoutParams(-1, dpi(48)).apply { topMargin = dpi(8) })
        bigBox.setPadding(dpi(16), dpi(14), dpi(16), dpi(12))
        pill.addView(bigBox, FrameLayout.LayoutParams(-1, -2))

        root.addView(pill, FrameLayout.LayoutParams(0, 0))
        wAnim.addUpdateListener { _, v, _ -> curW = v; applySize() }
        hAnim.addUpdateListener { _, v, _ -> curH = v; applySize() }
        setupGestures()
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val fs = !insets.isVisible(WindowInsets.Type.statusBars())
                if (IslandBus.fullscreen.value != fs) IslandBus.fullscreen.value = fs
            }
            v.onApplyWindowInsets(insets)
        }
    }

    fun attach() {
        if (attached) return
        applyPrefs()
        try { wm.addView(root, lp); attached = true } catch (e: Exception) { return }
        scope.launch {
            combine(IslandBus.transient, IslandBus.ongoing, IslandBus.media, IslandBus.charging, IslandBus.settingsTick) { t, o, m, c, _ ->
                when {
                    !prefs.enabled -> null
                    t != null -> Show.Notif(t)
                    o != null && o.isCall -> Show.Notif(o)
                    c != null && prefs.showCharging -> Show.Charge(c)
                    o != null -> Show.Notif(o)
                    m != null && prefs.showMedia -> Show.Media(m)
                    else -> Show.Idle
                }
            }.collect { s -> render(s) }
        }
        scope.launch { IslandBus.fullscreen.collect { updateVisibility() } }
    }

    fun detach() {
        scope.cancel()
        main.removeCallbacksAndMessages(null)
        if (attached) try { wm.removeView(root) } catch (_: Exception) {}
        attached = false
    }

    fun onConfigChanged() { applyPrefs(); updateVisibility() }

    private fun applyPrefs() {
        lp.x = dpi(prefs.offsetX); lp.y = dpi(prefs.offsetY)
        if (attached) try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
    }

    private var hidden = false
    private fun updateVisibility() {
        val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val off = !prefs.enabled || (prefs.hideLandscape && land) || (prefs.hideFullscreen && IslandBus.fullscreen.value && !expanded) ||
            (show is Show.Idle && !prefs.showIdle)
        hidden = off
        pill.animate().alpha(if (off) 0f else 1f).setDuration(180).start()
        pill.isClickable = !off
    }

    private fun render(s: Show?) {
        applyPrefs()
        val prev = show
        show = s ?: Show.Idle
        if (s == null) { updateVisibility(); return }
        main.removeCallbacks(autoCollapse)
        smallChrono.stop(); bigChrono.stop()
        smallText.visibility = View.GONE; smallChrono.visibility = View.GONE; eq.visibility = View.GONE
        smallIcon.visibility = View.VISIBLE; bigChrono.visibility = View.GONE
        controls.removeAllViews()
        val accent: Int
        when (s) {
            is Show.Idle -> { smallIcon.visibility = View.GONE; accent = 0; expanded = false }
            is Show.Notif -> {
                val n = s.n
                smallIcon.setImageDrawable(n.largeIcon ?: n.appIcon)
                bigIcon.setImageDrawable(n.largeIcon ?: n.appIcon)
                bigTitle.text = n.title; bigText.text = n.text
                accent = if (n.isCall) 0xFF2F7BFF.toInt() else 0xFF7C4DFF.toInt()
                if (n.chronoBase != null) {
                    val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - n.chronoBase)
                    for (ch in listOf(smallChrono, bigChrono)) {
                        ch.base = base; if (Build.VERSION.SDK_INT >= 24) ch.isCountDown = n.chronoCountDown
                        ch.visibility = View.VISIBLE; ch.start()
                    }
                } else if (n.isCall) { smallText.text = "📞 Anruf"; smallText.visibility = View.VISIBLE }
                else { smallText.text = n.title.take(14); smallText.visibility = View.VISIBLE }
                n.actions.forEach { (label, pi) -> controls.addView(actionButton(label) { send(pi) }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dpi(4); marginEnd = dpi(4) }) }
                if (prev !is Show.Notif || prev.n.key != n.key || prev.n.title != n.title || prev.n.text != n.text) {
                    if (IslandBus.transient.value?.key == n.key) { expanded = true; main.postDelayed(autoCollapse, 5000) }
                    else if (n.isCall) expanded = true
                }
            }
            is Show.Media -> {
                val m = s.m
                val icon = m.art?.let { android.graphics.drawable.BitmapDrawable(ctx.resources, it) }
                    ?: try { ctx.packageManager.getApplicationIcon(m.pkg) } catch (_: Exception) { null }
                smallIcon.setImageDrawable(icon); bigIcon.setImageDrawable(icon)
                bigTitle.text = m.title; bigText.text = m.artist
                eq.visibility = View.VISIBLE; eq.playing = m.playing
                accent = 0xFFC15CFF.toInt()
                val tc = m.controller.transportControls
                controls.addView(mediaButton("⏮") { tc.skipToPrevious() }, LinearLayout.LayoutParams(0, -1, 1f))
                controls.addView(mediaButton(if (m.playing) "⏸" else "▶") { if (m.playing) tc.pause() else tc.play() }, LinearLayout.LayoutParams(0, -1, 1f))
                controls.addView(mediaButton("⏭") { tc.skipToNext() }, LinearLayout.LayoutParams(0, -1, 1f))
                if (prev !is Show.Media) expanded = false
            }
            is Show.Charge -> {
                smallIcon.setImageResource(android.R.drawable.ic_lock_idle_charging)
                smallIcon.setColorFilter(0xFF4FC3FF.toInt())
                smallText.text = "⚡ ${s.level} %"; smallText.visibility = View.VISIBLE
                bigIcon.setImageResource(android.R.drawable.ic_lock_idle_charging)
                bigTitle.text = "Lädt"; bigText.text = "Akku bei ${s.level} %"
                accent = 0xFF4FC3FF.toInt(); expanded = false
                animateChargeText(s.level)
            }
        }
        if (s !is Show.Charge) smallIcon.clearColorFilter()
        bg.setStroke(dpi(1), if (accent == 0) 0x00000000 else (accent and 0x00FFFFFF) or 0x88000000.toInt())
        controls.visibility = if (controls.childCount > 0) View.VISIBLE else View.GONE
        updateVisibility()
        animateTo()
    }

    /** Nur für Screenshots: Insel ohne Fenster und ohne Animation zeichnen. */
    internal fun previewView(n: NotifItem?, charge: Int?, expand: Boolean): View {
        if (n != null) IslandBus.transient.value = n
        render(when { n != null -> Show.Notif(n); charge != null -> Show.Charge(charge); else -> Show.Idle })
        main.removeCallbacksAndMessages(null)
        expanded = expand; curW = 0f; animateTo()
        smallRow.animate().cancel(); bigBox.animate().cancel(); pill.animate().cancel()
        smallRow.alpha = if (expand) 0f else 1f; bigBox.alpha = if (expand) 1f else 0f; pill.alpha = 1f
        return root
    }

    private fun animateChargeText(target: Int) {
        val a = android.animation.ValueAnimator.ofInt((target - 15).coerceAtLeast(0), target).setDuration(1200)
        a.addUpdateListener { if (show is Show.Charge) smallText.text = "⚡ ${it.animatedValue} %" }
        a.start()
    }

    private fun setExpanded(e: Boolean) {
        if (show is Show.Idle) return
        expanded = e
        main.removeCallbacks(autoCollapse)
        if (e) main.postDelayed(autoCollapse, 8000)
        updateVisibility()
        animateTo()
    }

    private fun animateTo() {
        val dm = ctx.resources.displayMetrics
        val baseW = dp(prefs.width.toFloat()); val baseH = dp(prefs.height.toFloat())
        val (tw, th) = when {
            show is Show.Idle -> baseW to baseH
            expanded -> {
                val w = (dm.widthPixels.coerceAtMost(dm.heightPixels) - dp(24f))
                w to (if (controls.visibility == View.VISIBLE) dp(140f) else dp(80f))
            }
            else -> (baseW + dp(90f)) to baseH
        }
        bg.cornerRadius = if (expanded) dp(36f) else dp(prefs.radius.toFloat())
        smallRow.animate().alpha(if (expanded) 0f else 1f).setDuration(150).start()
        bigBox.animate().alpha(if (expanded) 1f else 0f).setStartDelay(if (expanded) 90 else 0).setDuration(180).start()
        smallRow.visibility = View.VISIBLE
        if (curW == 0f) { curW = tw; curH = th; wHolder.value = tw; hHolder.value = th; applySize(); return }
        wAnim.animateToFinalPosition(tw); hAnim.animateToFinalPosition(th)
    }

    private fun applySize() {
        val p = pill.layoutParams as FrameLayout.LayoutParams
        p.width = curW.toInt().coerceAtLeast(1); p.height = curH.toInt().coerceAtLeast(1)
        pill.layoutParams = p
        bigBox.visibility = if (curH > dp(prefs.height.toFloat()) + dp(20f)) View.VISIBLE else View.INVISIBLE
    }

    private fun setupGestures() {
        val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                when {
                    show is Show.Idle -> {}
                    !expanded -> setExpanded(true)
                    else -> openCurrent()
                }
                return true
            }
            override fun onLongPress(e: MotionEvent) { if (expanded) setExpanded(false) else if (show !is Show.Idle) setExpanded(true) }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) > abs(vy) && abs(vx) > 600) { dismissCurrent(); return true }
                if (vy < -500) { setExpanded(false); return true }
                if (vy > 500 && !expanded) { setExpanded(true); return true }
                return false
            }
        })
        pill.setOnTouchListener { _, ev -> gd.onTouchEvent(ev) }
    }

    private fun dismissCurrent() {
        val s = show
        pill.animate().translationX(if (expanded) dp(60f) else dp(30f)).alpha(0f).setDuration(140).withEndAction {
            pill.translationX = 0f; pill.alpha = 1f
            when (s) {
                is Show.Notif -> if (IslandBus.transient.value?.key == s.n.key) IslandBus.transient.value = null
                    else if (IslandBus.ongoing.value?.key == s.n.key) IslandBus.ongoing.value = null
                is Show.Charge -> IslandBus.charging.value = null
                else -> setExpanded(false)
            }
            updateVisibility()
        }.start()
    }

    private fun openCurrent() {
        when (val s = show) {
            is Show.Notif -> { send(s.n.intent); if (IslandBus.transient.value?.key == s.n.key) IslandBus.transient.value = null }
            is Show.Media -> send(s.m.controller.sessionActivity) ?: run {
                ctx.packageManager.getLaunchIntentForPackage(s.m.pkg)?.let { it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(it) }
            }
            else -> {}
        }
        setExpanded(false)
    }

    private fun send(pi: PendingIntent?): Unit? {
        if (pi == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                val o = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(ctx, 0, null, null, null, null, o.toBundle())
            } else pi.send()
        } catch (_: Exception) { null }
    }

    private fun mediaButton(sym: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = sym; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setOnClickListener { onClick(); main.removeCallbacks(autoCollapse); main.postDelayed(autoCollapse, 8000) }
    }

    private fun actionButton(label: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 1
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF1E3A8A.toInt(), 0xFF5B2BD6.toInt())).apply { cornerRadius = dp(24f) }
        setOnClickListener { onClick(); IslandBus.transient.value = null; setExpanded(false) }
    }
}

class RoundOutline(private val r: Float) : android.view.ViewOutlineProvider() {
    override fun getOutline(view: View, outline: android.graphics.Outline) = outline.setRoundRect(0, 0, view.width, view.height, r)
}
