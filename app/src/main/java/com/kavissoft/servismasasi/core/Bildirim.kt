package com.kavissoft.servismasasi.core

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kavissoft.servismasasi.MainActivity
import com.kavissoft.servismasasi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Hatırlatma bildirimleri (sesli). Telefon, firmanın Servis Masası'na "zamanı gelen var mı" diye sorar ve yeni olanları
 * web panelindeki zil sesiyle (yaklaşık 7 saniye) bildirir. Sunucu bir sonraki hatırlatmanın anını da bildirir; telefon o an
 * için bir alarm kurar, böylece uygulama kapalıyken de hatırlatma zamanında çalar. Dış bir bildirim servisi kullanılmaz;
 * bildirim içeriği telefonda saklanmaz.
 */
object Bildirim {
    private const val KANAL = "hatirlatma"
    private const val ESKI_KANAL = "vadeler"
    private const val IS_ADI = "vade_kontrol"
    private const val TEK_IS = "vade_simdi"
    private const val ALARM_KODU = 71

    fun izinVar(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun kanalKur(context: Context) {
        val yonetici = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { yonetici.deleteNotificationChannel(ESKI_KANAL) }
        val k = NotificationChannel(KANAL, "Hatırlatmalar (sesli)", NotificationManager.IMPORTANCE_HIGH)
        k.setSound(
            Uri.parse("android.resource://" + context.packageName + "/" + R.raw.hatirlatma),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        )
        k.enableVibration(true)
        yonetici.createNotificationChannel(k)
    }

    /** Zil sesini doğrudan çalar (bildirim izni verilmemişse ve uygulama açıksa). */
    private fun cal(context: Context) {
        runCatching {
            MediaPlayer.create(context.applicationContext, R.raw.hatirlatma)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }

    /**
     * Sunucudan gelen listeden daha önce gösterilmemiş olanları bildirir. Aynı anda birden fazla yeni kayıt varsa zil bir kez çalar.
     * [onPlanda]: uygulama açık; bildirim izni yoksa ses yine de çalınır.
     */
    fun goster(context: Context, kayitlar: List<JSONObject>, onPlanda: Boolean = false) {
        if (kayitlar.isEmpty()) return
        val izinli = izinVar(context)
        if (!izinli && !onPlanda) return
        val kayit = context.getSharedPreferences("sm_bildirim", Context.MODE_PRIVATE)
        val gorulen = (kayit.getStringSet("gorulen", emptySet()) ?: emptySet()).toMutableSet()
        val yeniler = kayitlar.filter { val a = it.optString("anahtar"); a.isNotEmpty() && a !in gorulen }.take(8)
        if (yeniler.isEmpty()) return
        if (izinli) {
            kanalKur(context)
            val ac = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            yeniler.forEachIndexed { i, k ->
                val b = NotificationCompat.Builder(context, KANAL)
                    .setSmallIcon(R.drawable.ic_bildirim)
                    .setContentTitle(k.optString("baslik"))
                    .setContentText(k.optString("metin"))
                    .setContentIntent(ac)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)   // kilit ekranında ayrıntı gösterilmez
                    .setSilent(i > 0)   // zil yalnızca ilk bildirimde çalar
                    .build()
                try {
                    NotificationManagerCompat.from(context).notify(k.optString("anahtar").hashCode(), b)
                } catch (e: SecurityException) {
                    return
                }
            }
        } else cal(context)
        yeniler.forEach { gorulen.add(it.optString("anahtar")) }
        // Liste sınırsız büyümesin
        val sakla = if (gorulen.size > 400) gorulen.toList().takeLast(300).toSet() else gorulen
        kayit.edit().putStringSet("gorulen", sakla).apply()
    }

    /** Sunucu yanıtını işler: yenileri bildirir ve bir sonraki hatırlatma anı için alarm kurar. */
    fun isle(context: Context, veri: JSONObject, onPlanda: Boolean = false) {
        val a = veri.optJSONArray("kayitlar")
        if (a != null) runCatching { goster(context, (0 until a.length()).mapNotNull { a.optJSONObject(it) }, onPlanda) }
        val sonraki = veri.optLong("sonraki_zaman", 0)
        val simdi = veri.optLong("simdi", 0)
        runCatching { if (sonraki > 0 && simdi > 0) alarmKur(context, maxOf(5L, sonraki - simdi) + 2) else alarmIptal(context) }
    }

    private fun alarmNiyeti(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, ALARM_KODU, Intent(context, AlarmAlici::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun alarmKur(context: Context, saniye: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val an = System.currentTimeMillis() + saniye * 1000
        val niyet = alarmNiyeti(context)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, an, niyet)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, an, niyet)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, an, niyet)
        }
    }

    private fun alarmIptal(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmNiyeti(context))
    }

    /** Sunucuya sorar ve sonucu işler. Oturum yoksa hiçbir şey yapmaz. false: sunucuya ulaşılamadı. */
    suspend fun kontrol(context: Context): Boolean {
        val api = Api(Kasa(context))
        if (api.firmaAdres == null || !api.yenilemeVar()) return true
        val y = api.istek("vadeler")
        if (y.ok) isle(context, y.veri)
        return y.ok
    }

    /** Bağlantı gelir gelmez bir kez kontrol et (alarm anında sunucuya ulaşılamadıysa) */
    fun hemenCalistir(context: Context) {
        val is_ = OneTimeWorkRequestBuilder<VadeIsci>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(TEK_IS, ExistingWorkPolicy.REPLACE, is_) }
    }

    fun zamanla(context: Context) {
        val is_ = PeriodicWorkRequestBuilder<VadeIsci>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(IS_ADI, ExistingPeriodicWorkPolicy.UPDATE, is_)
    }

    fun durdur(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(IS_ADI) }
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(TEK_IS) }
        runCatching { alarmIptal(context) }
        context.getSharedPreferences("sm_bildirim", Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/** Arka planda hatırlatma kontrolü (saatte bir ve alarm anında bağlantı yoksa bağlantı gelince). */
class VadeIsci(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            Bildirim.kontrol(applicationContext)
        } catch (e: Exception) {
        }
        return Result.success()
    }
}

/** Hatırlatma anı geldi: sunucuya sorulur ve bildirim zil sesiyle gösterilir (uygulama kapalı olsa da). */
class AlarmAlici : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val uygulama = context.applicationContext
        val bekleyen = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tamam = withTimeoutOrNull(8500) { Bildirim.kontrol(uygulama) }
                if (tamam != true) Bildirim.hemenCalistir(uygulama)
            } catch (e: Exception) {
                Bildirim.hemenCalistir(uygulama)
            } finally {
                runCatching { bekleyen.finish() }
            }
        }
    }
}
