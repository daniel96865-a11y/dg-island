package de.dgstudios.island

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

val Blue = Color(0xFF2F7BFF)
val Purple = Color(0xFF8B5CFF)
val Cyan = Color(0xFF4FC3FF)
val CardBg = Color(0xFF0B1433)
val TextDim = Color(0xFFB8C4FF)

const val VERSION_NOTES = """• Erste Version von DG Island 🎉
• Insel oben am Kamera-Loch mit weichen Feder-Animationen
• Mitteilungen mit App-Symbol, Tippen öffnet die App, Wischen blendet aus
• Musik mit Cover, Pause/Weiter/Zurück und Equalizer
• Laden-Anzeige mit Akku-Prozent, Anrufe und Timer live
• Position, Größe und Ecken frei einstellbar mit Live-Vorschau
• App-Filter, Querformat/Vollbild ausblenden, Autostart"""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val startTab = intent.getStringExtra("tab")
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Blue, secondary = Purple, background = Color.Black, surface = Color.Black)) { App(startTab) } }
    }
    override fun onResume() { super.onResume(); OverlayService.startIfNeeded(this) }
}

data class Perms(val notif: Boolean, val overlay: Boolean, val a11y: Boolean, val battery: Boolean)

fun readPerms(ctx: Context): Perms {
    val cn = ComponentName(ctx, IslandNotificationListener::class.java).flattenToString()
    val notif = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(cn) == true
    val a11yCn = ComponentName(ctx, IslandAccessibilityService::class.java).flattenToString()
    val a11y = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(a11yCn) == true
    val pm = ctx.getSystemService(PowerManager::class.java)
    return Perms(notif, Settings.canDrawOverlays(ctx), a11y, pm.isIgnoringBatteryOptimizations(ctx.packageName))
}

val LocalPermsOverride = staticCompositionLocalOf<Perms?> { null }

@Composable
fun rememberPerms(): Perms {
    LocalPermsOverride.current?.let { return it }
    val ctx = LocalContext.current
    var p by remember { mutableStateOf(readPerms(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) p = readPerms(ctx) }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return p
}

@Composable
fun App(startTab: String?) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var onboarded by remember { mutableStateOf(prefs.onboarded) }
    var tab by remember { mutableIntStateOf(when (startTab) { "look" -> 1; "apps" -> 2; "settings" -> 3; else -> 0 }) }
    var showNews by remember { mutableStateOf(prefs.onboarded && prefs.lastSeenVersion != BuildConfigVersion) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071233), Color(0xFF030716), Color.Black))).systemBarsPadding()) {
        if (!onboarded && startTab == null) {
            Onboarding { prefs.onboarded = true; prefs.lastSeenVersion = BuildConfigVersion; onboarded = true }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) { 0 -> HomeTab(); 1 -> LookTab(); 2 -> AppsTab(); else -> SettingsTab() }
                }
                BottomNav(tab) { tab = it }
            }
        }
        if (showNews) NewsDialog { prefs.lastSeenVersion = BuildConfigVersion; showNews = false }
    }
}

const val BuildConfigVersion = "1.0.0"

@Composable
fun NewsDialog(onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, containerColor = CardBg,
        title = { Text("Neu in Version $BuildConfigVersion ✨", color = Color.White, fontWeight = FontWeight.Bold) },
        text = { Text(VERSION_NOTES.trimIndent(), color = TextDim) },
        confirmButton = { TextButton(onClick = onClose) { Text("Cool!", color = Cyan) } })
}

@Composable
fun BottomNav(sel: Int, onSel: (Int) -> Unit) {
    val items = listOf("🏝️" to "Start", "🎨" to "Aussehen", "📱" to "Apps", "⚙️" to "Optionen")
    Row(Modifier.padding(16.dp, 8.dp, 16.dp, 12.dp).fillMaxWidth().shadow(18.dp, RoundedCornerShape(32.dp), ambientColor = Blue, spotColor = Purple)
        .clip(RoundedCornerShape(32.dp)).background(Color(0xFF0A1230)).border(1.dp, Brush.horizontalGradient(listOf(Blue.copy(.6f), Purple.copy(.6f))), RoundedCornerShape(32.dp))
        .padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEachIndexed { i, (e, l) ->
            val on = i == sel
            Column(Modifier.weight(1f).clip(RoundedCornerShape(26.dp))
                .background(if (on) Brush.horizontalGradient(listOf(Blue, Purple)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                .clickable { onSel(i) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(e, fontSize = 18.sp); Text(l, color = Color.White, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun GlowCard(modifier: Modifier = Modifier, glow: Color = Blue, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(24.dp), ambientColor = glow, spotColor = glow)
        .clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF0E1C4D), Color(0xFF090F2B))))
        .border(1.dp, Brush.linearGradient(listOf(glow.copy(.7f), Purple.copy(.25f))), RoundedCornerShape(24.dp)).padding(18.dp), content = content)
}

@Composable
fun Header(title: String, sub: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(sub, color = TextDim, fontSize = 14.sp)
    }
}

// ---------------- Onboarding ----------------

data class PermStep(val emoji: String, val title: String, val text: String, val ok: Boolean, val optional: Boolean, val open: (Context) -> Unit)

fun permSteps(p: Perms) = listOf(
    PermStep("🔔", "Benachrichtigungszugriff", "Damit DG Island Mitteilungen, Anrufe und Musik in der Insel zeigen kann. Nichts verlässt dein Handy.", p.notif, false) {
        it.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
    PermStep("♿", "Bedienungshilfe", "Empfohlen: So liegt die Insel zuverlässig über der Statusleiste am Kamera-Loch. Es werden keine Inhalte gelesen. Unter „Installierte Apps“ → DG Island einschalten.", p.a11y, true) {
        it.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
    PermStep("🪟", "Über anderen Apps anzeigen", "Ersatzweg, falls du die Bedienungshilfe nicht nutzen willst. Eins von beiden reicht.", p.overlay, true) {
        it.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${it.packageName}"))) },
    PermStep("🔋", "Akku-Optimierung aus", "Damit Android die Insel nicht im Hintergrund beendet.", p.battery, true) {
        @SuppressLint("BatteryLife")
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${it.packageName}"))
        try { it.startActivity(i) } catch (_: Exception) { it.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } },
)

@Composable
fun Onboarding(onDone: () -> Unit) {
    val p = rememberPerms()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PillPreview(widthDp = 150, heightDp = 40, radius = 20, active = true)
                Spacer(Modifier.height(20.dp))
                Text("Willkommen bei DG Island", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Deine Dynamic Island für Android 🏝️", color = TextDim)
                Text("Nur noch kurz ein paar Freigaben:", color = TextDim, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            }
        }
        items(permSteps(p)) { s -> PermCard(s) }
        item {
            val ready = p.notif && (p.a11y || p.overlay)
            Button(onClick = onDone, Modifier.padding(20.dp).fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), contentPadding = PaddingValues()) {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Blue, Purple))), contentAlignment = Alignment.Center) {
                    Text(if (ready) "Los geht's 🚀" else "Später einrichten", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun PermCard(s: PermStep) {
    val ctx = LocalContext.current
    GlowCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), glow = if (s.ok) Cyan else Purple) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.emoji, fontSize = 26.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(s.title + if (s.optional) "" else " *", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(s.text, color = TextDim, fontSize = 13.sp)
            }
            if (s.ok) Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Cyan, Blue))), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
            else Button(onClick = { s.open(ctx) }, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White)) { Text("Öffnen") }
        }
    }
}

// ---------------- Start ----------------

@Composable
fun HomeTab() {
    val ctx = LocalContext.current
    val p = rememberPerms()
    val prefs = remember { Prefs(ctx) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    val media by IslandBus.media.collectAsState()
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Header("DG Island", "Deine Insel von DG Studios") }
        item {
            GlowCard(Modifier.padding(16.dp, 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (enabled) "Insel ist an ✨" else "Insel ist aus", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        val mode = when { p.a11y -> "über Bedienungshilfe"; p.overlay -> "über Overlay"; else -> "Freigabe fehlt" }
                        Text("Modus: $mode", color = TextDim, fontSize = 13.sp)
                    }
                    Switch(enabled, { enabled = it; prefs.enabled = it; IslandBus.settingsChanged(); if (it) OverlayService.startIfNeeded(ctx) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Blue, checkedThumbColor = Color.White))
                }
            }
        }
        item {
            GlowCard(Modifier.padding(16.dp, 8.dp), glow = Purple) {
                Text("Ausprobieren", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Zeigt eine Beispiel-Mitteilung in der Insel.", color = TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                GradientButton("Test-Mitteilung 🔔") {
                    IslandBus.transient.value = NotifItem("test-${System.currentTimeMillis()}", ctx.packageName,
                        ctx.packageManager.getApplicationIcon(ctx.packageName), null, "DG Island", "So sieht eine Mitteilung in deiner Insel aus 👋",
                        null, false, emptyList())
                }
                Spacer(Modifier.height(8.dp))
                GradientButton("Laden simulieren ⚡") {
                    val bm = ctx.getSystemService(android.os.BatteryManager::class.java)
                    IslandBus.charging.value = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ IslandBus.charging.value = null }, 4000)
                }
            }
        }
        item {
            GlowCard(Modifier.padding(16.dp, 8.dp), glow = Cyan) {
                Text("Gerade läuft 🎵", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(media?.let { "${it.title} – ${it.artist}" } ?: "Keine Musik aktiv", color = TextDim)
            }
        }
        if (!(p.notif && (p.a11y || p.overlay))) item { Text("Freigaben fehlen:", color = Color.White, modifier = Modifier.padding(20.dp, 8.dp)) }
        items(permSteps(p).filter { !it.ok }) { PermCard(it) }
    }
}

@Composable
fun GradientButton(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(listOf(Blue, Purple))).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontWeight = FontWeight.Bold) }
}

// ---------------- Aussehen ----------------

@Composable
fun PillPreview(widthDp: Int, heightDp: Int, radius: Int, active: Boolean) {
    val t = rememberInfiniteTransition(label = "eq")
    val ph by t.animateFloat(0f, 6.28f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "ph")
    val w by animateDpAsState((widthDp + if (active) 90 else 0).dp, spring(0.7f, 400f), label = "w")
    Box(Modifier.width(w).height(heightDp.dp).shadow(16.dp, RoundedCornerShape(radius.dp), ambientColor = Purple, spotColor = Blue)
        .clip(RoundedCornerShape(radius.dp)).background(Color.Black)
        .border(1.dp, Brush.horizontalGradient(listOf(Blue.copy(.7f), Purple.copy(.7f))), RoundedCornerShape(radius.dp))) {
        if (active) Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size((heightDp * .62f).dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Blue, Purple))))
            Spacer(Modifier.weight(1f))
            Row(Modifier.height((heightDp * .5f).dp).drawBehind {
                for (i in 0 until 4) {
                    val f = .25f + .75f * kotlin.math.abs(kotlin.math.sin(ph + i * 1.3f))
                    val bw = size.width / 8; val bh = size.height * f; val x = i * bw * 2 + bw / 2
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFC15CFF), Blue)), androidx.compose.ui.geometry.Offset(x, (size.height - bh) / 2),
                        androidx.compose.ui.geometry.Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                }
            }.width(20.dp)) {}
        }
    }
}

@Composable
fun LookTab() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var x by remember { mutableFloatStateOf(prefs.offsetX.toFloat()) }
    var y by remember { mutableFloatStateOf(prefs.offsetY.toFloat()) }
    var w by remember { mutableFloatStateOf(prefs.width.toFloat()) }
    var h by remember { mutableFloatStateOf(prefs.height.toFloat()) }
    var r by remember { mutableFloatStateOf(prefs.radius.toFloat()) }
    var active by remember { mutableStateOf(true) }
    fun save() { prefs.offsetX = x.toInt(); prefs.offsetY = y.toInt(); prefs.width = w.toInt(); prefs.height = h.toInt(); prefs.radius = r.toInt(); IslandBus.settingsChanged() }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Header("Aussehen", "Position und Form – die echte Insel ändert sich live mit") }
        item {
            GlowCard(Modifier.padding(16.dp, 8.dp), glow = Purple) {
                Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Color(0xFF13245E), Color(0xFF060B22)))),
                    contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.offset(x.dp, (y + 6).dp)) { PillPreview(w.toInt(), h.toInt(), r.toInt(), active) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vorschau mit Musik", color = TextDim, modifier = Modifier.weight(1f))
                    Switch(active, { active = it }, colors = SwitchDefaults.colors(checkedTrackColor = Purple))
                }
            }
        }
        item {
            GlowCard(Modifier.padding(16.dp, 8.dp)) {
                SliderRow("↔️ Seitlich verschieben", x, -150f..150f, "${x.toInt()} dp") { x = it; save() }
                SliderRow("↕️ Abstand von oben", y, 0f..80f, "${y.toInt()} dp") { y = it; save() }
                SliderRow("📏 Breite", w, 60f..240f, "${w.toInt()} dp") { w = it; save() }
                SliderRow("📐 Höhe", h, 20f..60f, "${h.toInt()} dp") { h = it; save() }
                SliderRow("⭕ Ecken-Rundung", r, 0f..30f, "${r.toInt()} dp") { r = it; save() }
                TextButton(onClick = { x = 0f; y = 8f; w = 120f; h = 34f; r = 17f; save() }) { Text("Zurücksetzen", color = Cyan) }
            }
        }
    }
}

@Composable
fun SliderRow(label: String, v: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Row { Text(label, color = Color.White, modifier = Modifier.weight(1f)); Text(valueText, color = Cyan) }
    Slider(v, onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Blue, inactiveTrackColor = Color(0xFF1C2A5C)))
}

// ---------------- Apps ----------------

data class AppRow(val pkg: String, val label: String, val icon: Drawable?)

@Composable
fun AppsTab() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var blocked by remember { mutableStateOf(prefs.blocked) }
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppRow>>(emptyList()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = ctx.packageManager
            val launcher = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }
            val seen = prefs.sp.getStringSet("seen", emptySet())!!
            (launcher + seen).toSet().filter { it != ctx.packageName }.mapNotNull { pkg ->
                try { val ai = pm.getApplicationInfo(pkg, 0); AppRow(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai)) }
                catch (_: PackageManager.NameNotFoundException) { null }
            }.sortedBy { it.label.lowercase() }
        }
    }
    Column {
        Header("Apps", "Welche Apps dürfen in die Insel?")
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp, 4.dp), placeholder = { Text("App suchen …") },
            shape = RoundedCornerShape(24.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Blue, unfocusedBorderColor = Purple.copy(.5f), focusedTextColor = Color.White, unfocusedTextColor = Color.White))
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp)) {
            items(apps.filter { query.isBlank() || it.label.contains(query, true) }, key = { it.pkg }) { a ->
                val on = a.pkg !in blocked
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(CardBg).padding(12.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    a.icon?.let { Image(it.toBitmap(96, 96).asImageBitmap(), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))) }
                    Text(a.label, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Switch(on, { v -> blocked = if (v) blocked - a.pkg else blocked + a.pkg; prefs.blocked = blocked },
                        colors = SwitchDefaults.colors(checkedTrackColor = Blue))
                }
            }
        }
    }
}

// ---------------- Optionen ----------------

@Composable
fun SettingsTab() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var showNews by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Header("Optionen", "Verhalten der Insel") }
        item {
            GlowCard(Modifier.padding(16.dp, 8.dp)) {
                ToggleRow("💤 Leere Insel immer zeigen", "Kleine schwarze Pille, auch wenn nichts los ist", prefs.showIdle) { prefs.showIdle = it }
                ToggleRow("🎵 Musik anzeigen", "Cover, Steuerung und Equalizer", prefs.showMedia) { prefs.showMedia = it }
                ToggleRow("⚡ Laden anzeigen", "Akku-Prozent beim Einstecken", prefs.showCharging) { prefs.showCharging = it }
                ToggleRow("🔄 Im Querformat ausblenden", "Zum Beispiel bei Videos und Spielen", prefs.hideLandscape) { prefs.hideLandscape = it }
                ToggleRow("🎬 Im Vollbild ausblenden", "Wenn die Statusleiste versteckt ist (ab Android 11)", prefs.hideFullscreen) { prefs.hideFullscreen = it }
                ToggleRow("🚀 Autostart", "Nach dem Neustart automatisch starten", prefs.autostart) { prefs.autostart = it }
            }
        }
        item {
            GlowCard(Modifier.padding(16.dp, 8.dp), glow = Purple) {
                Text("Neu in dieser Version ✨", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(VERSION_NOTES.trimIndent(), color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Text("DG Island $BuildConfigVersion · DG Studios", color = TextDim.copy(.6f), fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    if (showNews) NewsDialog { showNews = false }
}

@Composable
fun ToggleRow(title: String, sub: String, initial: Boolean, onChange: (Boolean) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(sub, color = TextDim, fontSize = 12.sp) }
        Switch(v, { v = it; onChange(it); IslandBus.settingsChanged() }, colors = SwitchDefaults.colors(checkedTrackColor = Blue, checkedThumbColor = Color.White))
    }
}
