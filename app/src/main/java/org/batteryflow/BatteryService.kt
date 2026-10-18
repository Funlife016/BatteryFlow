package org.batteryflow

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.abs

class BatteryService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bf", "Battery monitor", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, notif("Monitoring battery…"))
        if (job == null) job = scope.launch {
            val dao = AppDb.get(this@BatteryService).dao()
            var n = 0
            while (isActive) {
                val s = read()
                dao.insert(s)
                if (n++ % 200 == 0) dao.prune(System.currentTimeMillis() - 90L * 86_400_000)
                nm.notify(1, notif("${s.level}%  ${s.currentMa} mA  ${"%.1f".format(s.tempC)}°C"))
                delay(30_000)
            }
        }
        return START_STICKY
    }

    private fun read(): Sample {
        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))!!
        val bm = getSystemService(BatteryManager::class.java)
        val status = b.getIntExtra(BatteryManager.EXTRA_STATUS, 1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        var raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toLong()
        if (abs(raw) > 20_000) raw /= 1000                    // µA -> mA (heuristic)
        val ma = if (charging) abs(raw).toInt() else -abs(raw).toInt() // normalize vendor sign quirks
        val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        return Sample(
            ts = System.currentTimeMillis(),
            level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale,
            tempC = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            voltageMv = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            currentMa = ma,
            status = status,
            health = b.getIntExtra(BatteryManager.EXTRA_HEALTH, 1),
            plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            screenOn = getSystemService(PowerManager::class.java).isInteractive,
            chargeMah = (bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) / 1000)
        )
    }

    private fun notif(text: String) = NotificationCompat.Builder(this, "bf")
        .setSmallIcon(R.drawable.ic_battery)
        .setContentTitle("BatteryFlow").setContentText(text)
        .setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED)
            c.startForegroundService(Intent(c, BatteryService::class.java))
    }
}
