package com.kavissoft.servismasasi.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.kavissoft.servismasasi.BuildConfig
import java.io.File
import java.security.MessageDigest

/** Cihaz ve uygulama bütünlüğü denetimleri. Dönen metin boş değilse uygulama açılmaz. */
object Koruma {

    fun denetle(context: Context): String? {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return "Hata ayıklayıcı bağlıyken uygulama çalışmaz."
        if (!imzaDogru(context)) return "Bu uygulama özgün değil (imzası değiştirilmiş). Uygulamayı yalnızca firmanızın verdiği adresten kurun."
        if (emulatorMu()) return "Uygulama sanal cihazlarda (emülatör) çalışmaz."
        if (rootluMu()) return "Bu telefonda yönetici (root) erişimi açık görünüyor. Verilerinizin güvenliği için uygulama bu cihazda çalışmaz."
        return null
    }

    /** Uygulamanın imza sertifikası, derleme sırasında gömülen özetle aynı mı? (Yeniden paketlenmiş sahte APK'yı yakalar) */
    private fun imzaDogru(context: Context): Boolean {
        val beklenen = BuildConfig.IMZA_OZET.replace(":", "").lowercase()
        if (beklenen.isEmpty()) return true
        return try {
            val pm = context.packageManager
            val imzalar = if (Build.VERSION.SDK_INT >= 28) {
                val bilgi = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                when {
                    bilgi == null -> emptyArray<android.content.pm.Signature>()
                    bilgi.hasMultipleSigners() -> bilgi.apkContentsSigners
                    else -> bilgi.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures ?: emptyArray<android.content.pm.Signature>()
            }
            imzalar.any { s ->
                val ozet = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
                ozet.joinToString("") { "%02x".format(it) } == beklenen
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun emulatorMu(): Boolean {
        val parmak = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val donanim = Build.HARDWARE.lowercase()
        val urun = Build.PRODUCT.lowercase()
        return parmak.startsWith("generic") || parmak.contains("vbox") || parmak.contains("test-keys") && parmak.contains("sdk") ||
            model.contains("emulator") || model.contains("android sdk built for") ||
            donanim.contains("goldfish") || donanim.contains("ranchu") || donanim.contains("vbox") ||
            urun.contains("sdk_gphone") || urun.contains("emulator") || urun.contains("simulator") ||
            Build.MANUFACTURER.lowercase().contains("genymotion")
    }

    private fun rootluMu(): Boolean {
        if ((Build.TAGS ?: "").contains("test-keys")) return true
        val yollar = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su",
            "/system/xbin/daemonsu", "/sbin/.magisk", "/data/adb/magisk", "/data/adb/ksu", "/system/bin/.ext/.su"
        )
        return yollar.any { runCatching { File(it).exists() }.getOrDefault(false) }
    }
}
