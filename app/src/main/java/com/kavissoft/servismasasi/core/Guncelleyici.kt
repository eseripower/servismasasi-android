package com.kavissoft.servismasasi.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Uygulamanın kendini güncellemesi. Yeni sürüm KavisCloud'dan indirilir, dosyanın SHA-256 özeti
 * KavisCloud'un bildirdiği özetle karşılaştırılır ve Android'in kendi kurucusuna verilir.
 * Android ayrıca yeni dosyanın aynı imza anahtarıyla imzalandığını denetler; başka imzalı dosya kurulamaz.
 */
object Guncelleyici {
    @Volatile
    private var bekleyen: CompletableDeferred<String>? = null

    fun sonuc(mesaj: String) {
        bekleyen?.complete(mesaj)
    }

    /** Dönen metin boşsa kurulum tamamlanmıştır (uygulama yeniden başlar); doluysa hata açıklamasıdır. */
    suspend fun guncelle(context: Context, api: Api, adres: String, beklenenOzet: String, ilerleme: (Int) -> Unit): String {
        val dosya = File(context.cacheDir, "guncelleme.apk")
        try {
            val ozet = api.indir(adres, dosya, ilerleme) ?: return "Yeni sürüm indirilemedi. İnternet bağlantınızı kontrol edin."
            if (!ozet.equals(beklenenOzet, ignoreCase = true)) return "İndirilen dosya doğrulanamadı; güvenlik nedeniyle kurulmadı."
            ilerleme(100)
            val bekle = CompletableDeferred<String>()
            bekleyen = bekle
            val hata = withContext(Dispatchers.IO) { kur(context, dosya) }
            if (hata != null) return hata
            return withTimeoutOrNull(300_000) { bekle.await() } ?: "Kurulum onaylanmadı."
        } catch (e: Exception) {
            return "Güncelleme kurulamadı."
        } finally {
            bekleyen = null
            runCatching { dosya.delete() }
        }
    }

    private fun kur(context: Context, dosya: File): String? {
        return try {
            val kurucu = context.packageManager.packageInstaller
            val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            p.setAppPackageName(context.packageName)
            p.setSize(dosya.length())
            // Android 12 ve üzeri: ilk izinden sonraki güncellemeler kullanıcıya sorulmadan kurulur
            if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val no = kurucu.createSession(p)
            val oturum = kurucu.openSession(no)
            try {
                val cikis = oturum.openWrite("servismasasi.apk", 0, dosya.length())
                try {
                    val giris = dosya.inputStream()
                    try {
                        giris.copyTo(cikis, 64 * 1024)
                    } finally {
                        giris.close()
                    }
                    oturum.fsync(cikis)
                } finally {
                    cikis.close()
                }
                val bayrak = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val niyet = Intent(context, KurulumAlici::class.java).setPackage(context.packageName)
                oturum.commit(PendingIntent.getBroadcast(context, no, niyet, bayrak).intentSender)
            } finally {
                oturum.close()
            }
            null
        } catch (e: Exception) {
            "Güncelleme kurulamadı."
        }
    }
}

/** Android kurucusunun bildirdiği sonuç: onay gerekiyorsa onay ekranı açılır. */
class KurulumAlici : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val onay = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (onay == null) Guncelleyici.sonuc("Kurulum onay ekranı açılamadı.")
                else runCatching { context.startActivity(onay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Guncelleyici.sonuc("Kurulum onay ekranı açılamadı.") }
            }
            PackageInstaller.STATUS_SUCCESS -> Guncelleyici.sonuc("")
            PackageInstaller.STATUS_FAILURE_ABORTED -> Guncelleyici.sonuc("Güncelleme iptal edildi.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> Guncelleyici.sonuc("Telefonda yeterli boş alan yok.")
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_INVALID -> {
                // Android'in ayrıntılı nedeni (ör. INSTALL_FAILED_UPDATE_INCOMPATIBLE, INSTALL_FAILED_VERSION_DOWNGRADE)
                val ayrinti = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Guncelleyici.sonuc(
                    when {
                        ayrinti.contains("DOWNGRADE", ignoreCase = true) ->
                            "Sunucudaki sürüm, telefondaki sürümden eski görünüyor (sürüm kodu düşük). Firmanızın yeni bir sürüm yayınlaması gerekiyor."
                        ayrinti.contains("UPDATE_INCOMPATIBLE", ignoreCase = true) || ayrinti.contains("signature", ignoreCase = true) ->
                            "Yeni sürüm farklı bir imza anahtarıyla imzalanmış. Uygulamayı bir kez kaldırıp yeniden kurun; sonraki güncellemeler kendiliğinden kurulur."
                        else -> "Yeni sürüm kurulamadı" + (if (ayrinti.isNotBlank()) " ($ayrinti)." else ".") + " Uygulamayı kaldırıp yeniden kurun."
                    }
                )
            }
            else -> Guncelleyici.sonuc("Güncelleme kurulamadı.")
        }
    }
}
