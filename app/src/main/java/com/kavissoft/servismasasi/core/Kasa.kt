package com.kavissoft.servismasasi.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Güvenli kasa: oturum anahtarları telefonun güvenli donanımındaki (Android Keystore) bir anahtarla
 * AES-256-GCM ile şifrelenerek saklanır. Şifre çözme anahtarı cihazdan dışarı çıkarılamaz.
 */
class Kasa(context: Context) {
    private val prefs = context.getSharedPreferences("sm_kasa", Context.MODE_PRIVATE)

    private fun anahtar(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(TAKMA_AD, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(TAKMA_AD, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    fun yaz(ad: String, deger: String?) {
        if (deger == null) {
            prefs.edit().remove(ad).apply()
            return
        }
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, anahtar())
            val sifreli = c.doFinal(deger.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(ad, Base64.encodeToString(c.iv + sifreli, Base64.NO_WRAP)).apply()
        } catch (e: Exception) {
            prefs.edit().remove(ad).apply()
        }
    }

    fun oku(ad: String): String? {
        val ham = prefs.getString(ad, null) ?: return null
        return try {
            val veri = Base64.decode(ham, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, anahtar(), GCMParameterSpec(128, veri, 0, 12))
            String(c.doFinal(veri, 12, veri.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            // Anahtar değişmiş ya da veri bozulmuş: değer güvenle okunamıyor, silinir
            prefs.edit().remove(ad).apply()
            null
        }
    }

    fun temizle() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAKMA_AD = "sm_kasa_anahtari"
    }
}
