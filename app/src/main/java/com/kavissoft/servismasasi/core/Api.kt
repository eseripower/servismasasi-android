package com.kavissoft.servismasasi.core

import android.os.Build
import com.kavissoft.servismasasi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Adres kullanılabilir mi: olağan durumda yalnızca https. SSL'siz deneme sürümünde ayrıca
 * kavissoft.com ve alt alan adlarına http de kabul edilir.
 */
fun adresUygun(adres: String): Boolean {
    if (adres.startsWith("https://")) return true
    if (!BuildConfig.SSLSIZ || !adres.startsWith("http://")) return false
    val alan = adres.removePrefix("http://").substringBefore('/').substringBefore(':').lowercase()
    return alan == "kavissoft.com" || alan.endsWith(".kavissoft.com")
}

/** SSL'siz deneme sürümünde kavissoft.com adresleri https yerine http olarak kullanılır (sunucuda sertifika yok). */
fun denemeAdresi(adres: String): String {
    if (!BuildConfig.SSLSIZ || !adres.startsWith("https://")) return adres
    val duz = "http://" + adres.removePrefix("https://")
    return if (adresUygun(duz)) duz else adres
}

/** Sunucu yanıtı: [kod] HTTP durum kodu (0: bağlantı kurulamadı), [veri] JSON gövdesi. */
class Yanit(val kod: Int, val veri: JSONObject) {
    val ok: Boolean get() = veri.optBoolean("ok", false)
    val mesaj: String get() = veri.optString("msg", veri.optString("error", veri.optString("message", ""))).ifEmpty { if (kod == 0) "Sunucuya ulaşılamadı. İnternet bağlantınızı kontrol edin." else "İşlem tamamlanamadı (hata $kod)." }
    val hataKodu: String get() = veri.optString("kod", "")
}

/**
 * Sunucu bağlantısı. Uygulamanın içinde hiçbir parola ya da gizli anahtar bulunmaz:
 * firma adresi KavisCloud'dan Firma No ile sorulur, sonraki bütün istekler o firmanın kendi
 * Servis Masası sunucusuna, girişte verilen kısa ömürlü oturum anahtarıyla gider.
 */
class Api(private val kasa: Kasa) {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val yenilemeKilidi = Mutex()

    private val istemci: OkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))   // yalnızca güncel TLS; şifresiz bağlantı yok
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    /**
     * YALNIZCA SSL'siz DENEME SÜRÜMÜ: kavissoft.com ve alt alan adlarına http ile ya da geçersiz
     * (kendinden imzalı) sertifikayla bağlanabilen istemci. Güvenli sürümde hiç oluşturulmaz.
     */
    private val denemeIstemci: OkHttpClient? = if (!BuildConfig.SSLSIZ) null else try {
        val guven = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val baglam = SSLContext.getInstance("TLS")
        baglam.init(null, arrayOf<TrustManager>(guven), SecureRandom())
        istemci.newBuilder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
            .sslSocketFactory(baglam.socketFactory, guven)
            .hostnameVerifier { alan, _ -> denemeAlani(alan) }
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    } catch (e: Exception) {
        null
    }

    private fun denemeAlani(alan: String): Boolean {
        val a = alan.lowercase()
        return a == "kavissoft.com" || a.endsWith(".kavissoft.com")
    }

    /** Adrese uygun istemci: deneme sürümünde yalnızca kavissoft.com adresleri gevşek istemciyle açılır. */
    private fun istemciSec(adres: String): OkHttpClient {
        val d = denemeIstemci ?: return istemci
        val alan = adres.substringAfter("://").substringBefore('/').substringBefore(':')
        return if (denemeAlani(alan)) d else istemci
    }

    val cihazKimlik: String
        get() = kasa.oku("cihaz_kimlik") ?: UUID.randomUUID().toString().also { kasa.yaz("cihaz_kimlik", it) }

    val cihazAdi: String
        get() = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).take(100)

    var firmaAdres: String?
        get() = kasa.oku("firma_adres")
        set(v) = kasa.yaz("firma_adres", v)

    private var erisim: String? = null

    fun oturumVar(): Boolean = erisim != null
    fun yenilemeVar(): Boolean = kasa.oku("yenileme") != null

    fun oturumuYaz(v: JSONObject) {
        erisim = v.optString("erisim").ifEmpty { null }
        v.optString("yenileme").ifEmpty { null }?.let { kasa.yaz("yenileme", it) }
        v.optString("cihaz_sirri").ifEmpty { null }?.let { kasa.yaz("cihaz_sirri", it) }
    }

    fun oturumuSil() {
        erisim = null
        kasa.yaz("yenileme", null)
    }

    /**
     * KavisCloud: Firma No ile firmanın adresini ve uygulama sürüm bilgisini sorar.
     * Panel adresi birden fazla aday içerebilir; yanıt veren adres hatırlanır ve sonraki açılışlarda önce o denenir.
     */
    suspend fun firmaBul(firmaNo: String): Yanit {
        val govde = JSONObject().put("business_no", firmaNo).put("package", BuildConfig.APPLICATION_ID).put("version", BuildConfig.VERSION_NAME)
        val guvenli = BuildConfig.PANEL.split(",").map { it.trim().trimEnd('/') }.filter { it.startsWith("https://") }
        val adaylar = if (BuildConfig.SSLSIZ) guvenli.map { "http://" + it.removePrefix("https://") } else guvenli
        val bilinen = kasa.oku("panel")
        val sira = if (bilinen != null && bilinen in adaylar) listOf(bilinen) + (adaylar - bilinen) else adaylar
        var son = Yanit(0, JSONObject())
        for (taban in sira) {
            val y = gonder("$taban/api/v1/mobile/lookup", govde, null)
            // Yanıt KavisCloud'un bu ucundan mı geldi? (ok ya da kendi hata alanı). Değilse sıradaki adres denenir.
            if (y.ok || y.veri.has("error")) {
                kasa.yaz("panel", taban)
                return y
            }
            if (y.kod == 429) return y
            if (y.kod != 0 || son.kod == 0) son = y
        }
        return if (son.kod == 0) son else Yanit(son.kod, JSONObject().put("msg", "Yönetim sunucusunda mobil uygulama desteği bulunamadı (hata ${son.kod}). Hizmet sağlayıcınıza bildirin." + (if (BuildConfig.SSLSIZ) " [" + son.veri.optString("message").take(120) + "]" else "")))
    }

    /** Firmanın Servis Masası sunucusuna istek. [oturum] true ise erişim anahtarı eklenir, süresi dolmuşsa bir kez yenilenir. */
    suspend fun istek(uc: String, govde: JSONObject? = null, sorgu: Map<String, String> = emptyMap(), oturum: Boolean = true, dosya: Pair<String, ByteArray>? = null, dosyaAlan: String = "dosya"): Yanit {
        val taban = firmaAdres ?: return Yanit(0, JSONObject().put("msg", "Firma bilgisi bulunamadı."))
        val adres = (taban.trimEnd('/') + "/api/v1/index.php").toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("r", uc)
            ?.apply { sorgu.forEach { (k, v) -> addQueryParameter(k, v) } }
            ?.build()?.toString() ?: return Yanit(0, JSONObject().put("msg", "Firma adresi geçersiz."))
        if (!oturum) return gonder(adres, govde, null, dosya, dosyaAlan)
        if (erisim == null && !yenile()) return Yanit(401, JSONObject().put("kod", "oturum").put("msg", "Oturumunuz sona erdi, yeniden giriş yapın."))
        var y = gonder(adres, govde, erisim, dosya, dosyaAlan)
        if (y.kod == 401 && y.hataKodu == "oturum" && yenile()) y = gonder(adres, govde, erisim, dosya, dosyaAlan)
        return y
    }

    /** Oturumla ham dosya indirir (belge / fotoğraf görüntüleme). En çok 12 MB. */
    suspend fun bayt(uc: String, sorgu: Map<String, String>): ByteArray? {
        val taban = firmaAdres ?: return null
        val adres = (taban.trimEnd('/') + "/api/v1/index.php").toHttpUrlOrNull()?.newBuilder()?.addQueryParameter("r", uc)
            ?.apply { sorgu.forEach { (k, v) -> addQueryParameter(k, v) } }?.build()?.toString() ?: return null
        if (erisim == null && !yenile()) return null
        var sonuc = baytIc(adres)
        if (sonuc.first == 401 && yenile()) sonuc = baytIc(adres)
        return sonuc.second
    }

    private suspend fun baytIc(adres: String): Pair<Int, ByteArray?> = withContext(Dispatchers.IO) {
        if (!adresUygun(adres)) return@withContext Pair(0, null)
        try {
            val anahtar = erisim ?: return@withContext Pair(401, null)
            val yanit = istemciSec(adres).newCall(Request.Builder().url(adres).header("Authorization", "Bearer $anahtar").header("X-Auth-Token", anahtar).build()).execute()
            try {
                val govde = yanit.body
                if (!yanit.isSuccessful || govde == null || govde.contentLength() > 12L * 1024 * 1024) Pair(yanit.code, null) else Pair(yanit.code, govde.bytes())
            } finally {
                yanit.close()
            }
        } catch (e: Exception) {
            Pair(0, null)
        }
    }

    /** Yenileme anahtarıyla yeni erişim anahtarı alır (anahtar her kullanımda değişir). */
    suspend fun yenile(): Boolean = yenilemeKilidi.withLock {
        val taban = firmaAdres ?: return@withLock false
        val anahtar = kasa.oku("yenileme") ?: return@withLock false
        val adres = taban.trimEnd('/') + "/api/v1/index.php?r=yenile"
        val y = gonder(adres, JSONObject().put("cihaz_kimlik", cihazKimlik).put("yenileme", anahtar), null)
        if (y.ok) {
            oturumuYaz(y.veri)
            true
        } else {
            if (y.kod == 401 || y.kod == 403) oturumuSil()   // ağ hatasında anahtar silinmez
            false
        }
    }

    /** Dosya indirir (yalnızca HTTPS, en çok 200 MB). Dönen: dosyanın SHA-256 özeti ya da null. */
    suspend fun indir(adres: String, hedef: File, ilerleme: (Int) -> Unit): String? = withContext(Dispatchers.IO) {
        if (!adresUygun(adres)) return@withContext null
        try {
            indirIc(adres, hedef, ilerleme)
        } catch (e: Exception) {
            null
        }
    }

    private fun indirIc(adres: String, hedef: File, ilerleme: (Int) -> Unit): String? {
        val sinir = 200L * 1024 * 1024
        val yanit = istemciSec(adres).newCall(Request.Builder().url(adres).header("User-Agent", "ServisMasasi-Android/" + BuildConfig.VERSION_NAME).build()).execute()
        try {
            val govde = yanit.body
            if (!yanit.isSuccessful || govde == null) return null
            val toplam = govde.contentLength()
            if (toplam > sinir) return null
            val ozet = MessageDigest.getInstance("SHA-256")
            val giris = govde.byteStream()
            val cikis = hedef.outputStream()
            try {
                val tampon = ByteArray(64 * 1024)
                var okunan = 0L
                var son = -1
                while (true) {
                    val n = giris.read(tampon)
                    if (n < 0) break
                    okunan += n
                    if (okunan > sinir) return null
                    ozet.update(tampon, 0, n)
                    cikis.write(tampon, 0, n)
                    if (toplam > 0) {
                        val yuzde = (okunan * 100 / toplam).toInt()
                        if (yuzde != son) {
                            son = yuzde
                            ilerleme(yuzde)
                        }
                    }
                }
            } finally {
                cikis.close()
                giris.close()
            }
            return ozet.digest().joinToString("") { "%02x".format(it) }
        } finally {
            yanit.close()
        }
    }

    /** Deneme sürümünde sorunun yerini bulmak için hata iletisine adres ve hata türü eklenir. */
    private fun iz(adres: String, e: Exception): String =
        if (BuildConfig.SSLSIZ) " [" + adres.substringBefore('?').take(90) + " · " + e.javaClass.simpleName + "]" else ""

    private suspend fun gonder(adres: String, govde: JSONObject?, anahtar: String?, dosya: Pair<String, ByteArray>? = null, dosyaAlan: String = "dosya"): Yanit = withContext(Dispatchers.IO) {
        if (!adresUygun(adres)) return@withContext Yanit(0, JSONObject().put("msg", "Güvenli olmayan adres reddedildi."))
        try {
            val b = Request.Builder().url(adres)
                .header("Accept", "application/json")
                .header("User-Agent", "ServisMasasi-Android/" + BuildConfig.VERSION_NAME)
            if (anahtar != null) {
                b.header("Authorization", "Bearer $anahtar")
                b.header("X-Auth-Token", anahtar)
            }
            if (dosya != null) {
                // Dosyalı gönderim: alanlar form alanı olarak, liste değerleri ad[] biçiminde eklenir
                val m = MultipartBody.Builder().setType(MultipartBody.FORM)
                if (govde != null) for (ad in govde.keys()) {
                    val v = govde.get(ad)
                    if (v is JSONArray) for (i in 0 until v.length()) m.addFormDataPart("$ad[]", v.optString(i)) else m.addFormDataPart(ad, v.toString())
                }
                m.addFormDataPart(dosyaAlan, dosya.first, dosya.second.toRequestBody((if (dosya.first.endsWith(".jpg")) "image/jpeg" else "application/octet-stream").toMediaType()))
                b.post(m.build())
            } else if (govde != null) b.post(govde.toString().toRequestBody(json)) else b.get()
            istemciSec(adres).newCall(b.build()).execute().use { r ->
                val metin = r.body?.string().orEmpty()
                val v = try {
                    JSONObject(metin)
                } catch (e: Exception) {
                    JSONObject().put("msg", if (r.code in 200..299) "Sunucudan beklenmeyen bir yanıt geldi." else "")
                }
                Yanit(r.code, v)
            }
        } catch (e: SSLException) {
            Yanit(0, JSONObject().put("msg", "Sunucunun güvenlik sertifikası doğrulanamadı. Bağlantı güvenli olmadığı için kesildi." + iz(adres, e)))
        } catch (e: Exception) {
            if (BuildConfig.SSLSIZ) Yanit(0, JSONObject().put("msg", "Sunucuya ulaşılamadı." + iz(adres, e))) else Yanit(0, JSONObject())
        }
    }
}
