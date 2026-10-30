package de.dgstudios.island

import android.graphics.Color as AColor
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class Screenshots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.NoActionBar")

    private fun shot(perms: Perms, content: @Composable () -> Unit) = paparazzi.snapshot {
        CompositionLocalProvider(LocalPermsOverride provides perms) {
            MaterialTheme(colorScheme = darkColorScheme(primary = Blue, secondary = Purple, background = Color.Black, surface = Color.Black)) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071233), Color(0xFF030716), Color.Black))).padding(top = 24.dp)) { content() }
            }
        }
    }
    private val dp = androidx.compose.ui.unit.Dp(1f)
    private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())

    @Test fun onboarding() = shot(Perms(true, false, false, false)) { Onboarding {} }
    @Test fun home() = shot(Perms(true, false, true, true)) { Column { Box(Modifier.weight(1f)) { HomeTab() }; BottomNav(0) {} } }
    @Test fun aussehen() = shot(Perms(true, false, true, true)) { Column { Box(Modifier.weight(1f)) { LookTab() }; BottomNav(1) {} } }
    @Test fun optionen() = shot(Perms(true, false, true, true)) { Column { Box(Modifier.weight(1f)) { SettingsTab() }; BottomNav(3) {} } }

    private fun island(n: NotifItem?, charge: Int?, expand: Boolean) {
        val ctx = paparazzi.context
        val ov = IslandOverlay(ctx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        val v = ov.previewView(n, charge, expand)
        val frame = FrameLayout(ctx).apply {
            setBackgroundColor(AColor.parseColor("#0B1A4A"))
            addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL).apply { topMargin = 30 })
        }
        IslandBus.transient.value = null
        paparazzi.snapshot(frame)
    }

    private fun demo(call: Boolean = false) = NotifItem("k", "com.whatsapp", android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF2F7BFF.toInt(), 0xFFB04DFF.toInt())), null,
        if (call) "Mama" else "Lisa", if (call) "Eingehender Anruf" else "Bist du heute Abend dabei? 🍕", null, call,
        emptyList())
    private fun fakePi(): android.app.PendingIntent? = null

    @Test fun insel_idle() = island(null, null, false)
    @Test fun insel_mitteilung() = island(demo(), null, true)
    @Test fun insel_laden() = island(null, 87, false)
}
