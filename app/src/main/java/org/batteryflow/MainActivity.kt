package org.batteryflow

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.content.Intent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class VM(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val rangeH = MutableStateFlow(24)
    val latest = dao.latest().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val history = rangeH.flatMapLatest { h -> dao.since(System.currentTimeMillis() - h * 3_600_000L) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ContextCompat.startForegroundService(this, Intent(this, BatteryService::class.java))
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = if (Build.VERSION.SDK_INT >= 31)
                (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx))
            else if (dark) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) { App() }
        }
    }
}

fun healthStr(h: Int) = when (h) {
    2 -> "Good"; 3 -> "Overheat"; 4 -> "Dead"; 5 -> "Over voltage"; 6 -> "Failure"; 7 -> "Cold"; else -> "Unknown"
}
fun statusStr(s: Int) = when (s) {
    2 -> "Charging"; 3 -> "Discharging"; 4 -> "Not charging"; 5 -> "Full"; else -> "Unknown"
}

/** Average drain in %/h while discharging, split by screen state. */
fun drain(list: List<Sample>, screenOn: Boolean): Float? {
    var pct = 0f; var hrs = 0f
    for (k in 0 until list.size - 1) {
        val a = list[k]; val c = list[k + 1]
        val dt = (c.ts - a.ts) / 3_600_000f
        if (dt > 0.1f || a.currentMa >= 0 || c.currentMa >= 0 || a.screenOn != screenOn) continue
        pct += (a.level - c.level).coerceAtLeast(0); hrs += dt
    }
    return if (hrs > 0.05f) pct / hrs else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VM = androidx.lifecycle.viewmodel.compose.viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    if (Build.VERSION.SDK_INT >= 33) {
        val l = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { l.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("BatteryFlow") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.BatteryChargingFull, null) }, label = { Text("Now") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.ShowChart, null) }, label = { Text("History") })
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tab == 0) Now(vm) else History(vm)
        }
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) = ElevatedCard(modifier) {
    Column(Modifier.padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun Now(vm: VM) {
    val s by vm.latest.collectAsStateWithLifecycle()
    val h by vm.history.collectAsStateWithLifecycle()
    val x = s ?: return Text("Waiting for first sample…")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text("${x.level}%", fontSize = 56.sp, style = MaterialTheme.typography.displayMedium)
            Text(statusStr(x.status))
            LinearProgressIndicator(progress = { x.level / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
    val w = x.currentMa * x.voltageMv / 1_000_000f
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("Current", "${x.currentMa} mA", Modifier.weight(1f))
        Stat("Power", "%.2f W".format(w), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("Temperature", "%.1f °C".format(x.tempC), Modifier.weight(1f))
        Stat("Voltage", "%.2f V".format(x.voltageMv / 1000f), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("Health", healthStr(x.health), Modifier.weight(1f))
        Stat("Est. capacity", if (x.level > 5) "${x.chargeMah * 100 / x.level} mAh" else "—", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("Drain, screen on", drain(h, true)?.let { "%.1f %%/h".format(it) } ?: "—", Modifier.weight(1f))
        Stat("Drain, screen off", drain(h, false)?.let { "%.1f %%/h".format(it) } ?: "—", Modifier.weight(1f))
    }
}

@Composable
fun History(vm: VM) {
    val h by vm.history.collectAsStateWithLifecycle()
    val range by vm.rangeH.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(6 to "6 h", 24 to "24 h", 168 to "7 d", 720 to "30 d").forEach { (v, l) ->
            FilterChip(range == v, { vm.rangeH.value = v }, { Text(l) })
        }
    }
    Text("${h.size} samples", style = MaterialTheme.typography.labelMedium)
    Chart("Level (%)", h.map { it.level.toFloat() }, MaterialTheme.colorScheme.primary)
    Chart("Temperature (°C)", h.map { it.tempC }, MaterialTheme.colorScheme.tertiary)
    Chart("Current (mA)", h.map { it.currentMa.toFloat() }, MaterialTheme.colorScheme.secondary)
}

@Composable
fun Chart(title: String, raw: List<Float>, color: Color) = ElevatedCard(Modifier.fillMaxWidth()) {
    val v = if (raw.size > 300) raw.filterIndexed { i, _ -> i % (raw.size / 300 + 1) == 0 } else raw
    Column(Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (v.size > 1) Text("min %.1f  max %.1f".format(v.min(), v.max()), style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp)) {
            if (v.size < 2) return@Canvas
            val mn = v.min(); val r = (v.max() - mn).coerceAtLeast(1f)
            val p = Path()
            v.forEachIndexed { i, y ->
                val px = size.width * i / (v.size - 1); val py = size.height * (1 - (y - mn) / r)
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            drawPath(p, color, style = Stroke(4f))
        }
    }
}
