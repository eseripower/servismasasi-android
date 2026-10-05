package com.kavissoft.servismasasi.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.resume

/**
 * Parmak izi / yüz tanıma ile giriş. Telefonun güvenli donanımında, yalnızca biyometrik doğrulamayla
 * kullanılabilen bir imza anahtarı üretilir. Sunucu her girişte tek kullanımlık bir metin gönderir,
 * telefon bunu imzalar. Özel anahtar ve parmak izi verisi telefondan hiç çıkmaz.
 */
object Biyometrik {
    private const val TAKMA_AD = "sm_biyometrik_anahtari"

    fun kullanilabilir(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Yeni anahtar çifti üretir; sunucuya kaydedilecek açık anahtarı (base64) döndürür. */
    fun anahtarUret(): String? = try {
        sil()
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        kpg.initialize(
            KeyGenParameterSpec.Builder(TAKMA_AD, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        Base64.encodeToString(kpg.generateKeyPair().public.encoded, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    fun sil() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(TAKMA_AD) }
    }

    /**
     * Metni biyometrik doğrulamayla imzalar. Dönen: imza (base64) ya da null.
     * [anahtarGecersiz] true dönerse telefona yeni parmak izi eklenmiştir; parola ile giriş gerekir.
     */
    suspend fun imzala(activity: FragmentActivity, metin: String, baslik: String): Sonuc {
        val imza: Signature = try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val ozel = ks.getKey(TAKMA_AD, null) as? PrivateKey ?: return Sonuc(null, true, "")
            Signature.getInstance("SHA256withECDSA").apply { initSign(ozel) }
        } catch (e: Exception) {
            return Sonuc(null, true, "")
        }
        return suspendCancellableCoroutine { devam ->
            val istem = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val s = try {
                        val nesne = result.cryptoObject?.signature
                        if (nesne == null) null else {
                            nesne.update(metin.toByteArray(Charsets.UTF_8))
                            Base64.encodeToString(nesne.sign(), Base64.NO_WRAP)
                        }
                    } catch (e: Exception) {
                        null
                    }
                    if (devam.isActive) devam.resume(Sonuc(s, false, if (s == null) "İmzalama başarısız oldu." else ""))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (devam.isActive) devam.resume(Sonuc(null, false, errString.toString()))
                }
            })
            val bilgi = BiometricPrompt.PromptInfo.Builder()
                .setTitle(baslik)
                .setSubtitle("Servis Masası")
                .setNegativeButtonText("Parola ile gir")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setConfirmationRequired(false)
                .build()
            try {
                istem.authenticate(bilgi, BiometricPrompt.CryptoObject(imza))
            } catch (e: Exception) {
                if (devam.isActive) devam.resume(Sonuc(null, false, "Biyometrik doğrulama açılamadı."))
            }
            devam.invokeOnCancellation { runCatching { istem.cancelAuthentication() } }
        }
    }

    data class Sonuc(val imza: String?, val anahtarGecersiz: Boolean, val mesaj: String)
}
