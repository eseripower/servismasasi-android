package com.kavissoft.servismasasi

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kavissoft.servismasasi.core.Api
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kavissoft.servismasasi.core.Bildirim
import com.kavissoft.servismasasi.core.Biyometrik
import com.kavissoft.servismasasi.core.Guncelleyici
import com.kavissoft.servismasasi.core.Kasa
import com.kavissoft.servismasasi.core.Koruma
import com.kavissoft.servismasasi.core.Yanit
import com.kavissoft.servismasasi.core.adresUygun
import com.kavissoft.servismasasi.core.denemeAdresi
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class Ekran { YUKLENIYOR, ENGEL, GUNCELLE, FIRMA, GIRIS, KILIT, ANA, LISTE, FORM, DETAY, TOPLU, SOHBET }

/** JSON'da null olan alanlar "null" yazısı olarak değil, boş metin olarak okunur. */
fun JSONObject.metin(ad: String): String = if (isNull(ad)) "" else optString(ad, "")

class Durum(app: Application) : AndroidViewModel(app) {
    private val kasa = Kasa(app)
    private val api = Api(kasa)

    var ekran by mutableStateOf(Ekran.YUKLENIYOR)
        private set
    var mesaj by mutableStateOf("")
    var mesgul by mutableStateOf(false)
        private set
    var firmaAd by mutableStateOf(kasa.oku("firma_ad") ?: "")
        private set
    var kodGerekli by mutableStateOf(false)
        private set
    var kullanici by mutableStateOf<JSONObject?>(null)
        private set
    var guncelleAdres by mutableStateOf("")
        private set
    var yeniSurum by mutableStateOf("")
        private set
    var guncelleZorunlu by mutableStateOf(false)
        private set
    var guncelleMetin by mutableStateOf("")
        private set
    private var apkOzet = ""
    var biyometrikTeklif by mutableStateOf(false)
    var panel by mutableStateOf<JSONObject?>(null)
        private set
    var ozetMesaj by mutableStateOf("")
        private set
    var bilgi by mutableStateOf("")
    var modul by mutableStateOf<Modul?>(null)
        private set
    var listeBaslik by mutableStateOf("")
        private set
    var liste by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var listeAra by mutableStateOf("")
    var listeSayfa by mutableStateOf(1)
        private set
    var listeSayfaSayisi by mutableStateOf(1)
        private set
    var listeToplam by mutableStateOf(-1)
        private set
    var listeOzet by mutableStateOf("")
        private set
    private var listeEk: Map<String, String> = emptyMap()
    var secili by mutableStateOf<JSONObject?>(null)
    var form by mutableStateOf<FormTanim?>(null)
        private set
    val formDeger = mutableStateMapOf<String, String>()
    var sabitler by mutableStateOf<JSONObject?>(null)
        private set
    private var detayOnce = Ekran.ANA

    /** Giriş yapılan firmanın numarası (Firma No) */
    val firmaNo: String get() = kasa.oku("firma_no") ?: ""

    // ---- KavisCloud duyuruları ve canlı destek
    /** Gösterilmeyi bekleyen pop-up'lar (ilk sıradaki ekranda gösterilir) */
    var popuplar by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var kcDuyurular by mutableStateOf<List<String>>(emptyList())
        private set
    var destekVar by mutableStateOf(false)
        private set
    var destekDavet by mutableStateOf(false)
        private set

    // ---- Sohbet: ekip sohbeti ("ekip") ve canlı destek ("destek")
    var sohbetTur by mutableStateOf("ekip")
        private set
    var sohbetKanallar by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    /** Açık görüşme; ekip sohbetinde null ise kişi / kanal listesi gösterilir */
    var sohbetKanal by mutableStateOf<JSONObject?>(null)
        private set
    val sohbetMesajlar = mutableStateListOf<JSONObject>()
    var sohbetDurum by mutableStateOf("")
        private set
    var sohbetGonderiyor by mutableStateOf(false)
        private set
    private var sohbetYokluyor = false
    private val yigin = ArrayDeque<Triple<Modul, Map<String, String>, String>>()
    /** Servis ayrıntısının tamamı (parçalar, onaylar, tahsilatlar…) */
    var detayTam by mutableStateOf<JSONObject?>(null)
        private set
    /** Arama ile seçilen alanların görünen adı (ör. müşteri adı) */
    val formEtiket = mutableStateMapOf<String, String>()
    var araSonuc by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    private var formDonus = Ekran.LISTE
    /** Etiket okuma: marka → daha önce kayıtlı modeller (model adayları puanlanırken kullanılır) */
    private var bilinenMarka = "-"
    private var bilinenModeller: List<Pair<String, Boolean>> = emptyList()
    private var formSorgu: Map<String, String> = emptyMap()
    private var formOnYukleme: String = ""
    private var duzenleId: Int? = null
    val duzenleniyor: Boolean get() = duzenleId != null
    var belgeler by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var belgeResim by mutableStateOf<Bitmap?>(null)
    var detay by mutableStateOf<JSONObject?>(null)
        private set
    var gecmis by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    private var arkaPlanZamani = 0L
    val biyometrikAcik: Boolean get() = kasa.oku("biyometrik") == "1"
    val surum: String get() = BuildConfig.VERSION_NAME
    val sslsiz: Boolean get() = BuildConfig.SSLSIZ

    init {
        baslat()
    }

    fun baslat() = calis {
        mesaj = ""
        Koruma.denetle(getApplication())?.let {
            mesaj = it
            ekran = Ekran.ENGEL
            return@calis
        }
        val no = kasa.oku("firma_no")
        if (api.firmaAdres == null || no == null) {
            ekran = Ekran.FIRMA
            return@calis
        }
        // Her açılışta: firma hâlâ açık mı, uygulama sürümü yeterli mi?
        val y = api.firmaBul(no)
        if (y.ok) {
            if (!firmaUygula(y.veri) || !guncellemeTamam()) return@calis
        } else if (y.kod == 403) {
            mesaj = y.mesaj
            ekran = Ekran.ENGEL
            return@calis
        } else if (y.kod == 404) {
            firmayiUnut()
            mesaj = y.mesaj
            ekran = Ekran.FIRMA
            return@calis
        }
        oturumaGec()
    }

    /** KavisCloud yanıtını uygular: firma adresi ve sürüm bilgisi. Adres güvenli değilse false döner. */
    private fun firmaUygula(v: JSONObject): Boolean {
        val adres = denemeAdresi(v.metin("url"))
        if (!adresUygun(adres)) {
            mesaj = "Firma adresi güvenli değil; bağlantı kurulmadı."
            ekran = Ekran.FIRMA
            return false
        }
        api.firmaAdres = adres
        firmaAd = v.metin("name")
        kasa.yaz("firma_ad", firmaAd)
        guncelleAdres = denemeAdresi(v.metin("download_url")).let { if (adresUygun(it)) it else "" }
        apkOzet = v.metin("sha256")
        yeniSurum = if (surumKucuk(surum, v.metin("version"))) v.metin("version") else ""
        guncelleZorunlu = surumKucuk(surum, v.metin("min_version"))
        if (guncelleZorunlu && yeniSurum.isEmpty()) yeniSurum = v.metin("min_version")
        return true
    }

    /**
     * Yeni sürüm varsa kullanıcıya sorulur (güncelleme zorunluysa "Sonra" seçeneği olmaz).
     * Dönen: uygulama açılmaya devam edebilir mi (false: güncelleme ekranında bekleniyor).
     */
    private fun guncellemeTamam(): Boolean {
        if (yeniSurum.isEmpty()) return true
        if (guncelleErtelendi && !guncelleZorunlu) return true
        guncelleMetin = ""
        ekran = Ekran.GUNCELLE
        return false
    }

    private var guncelleErtelendi = false
    val guncelleKurulabilir: Boolean get() = guncelleAdres.isNotEmpty() && apkOzet.length == 64

    /** "Güncelle": yeni sürümü indirir, doğrular ve kurar (kurulunca uygulama yeniden başlar). */
    fun guncelleBaslat() = calis {
        mesaj = ""
        if (!guncelleKurulabilir) {
            mesaj = "Yeni sürüm dosyası henüz yayınlanmamış. Hizmet sağlayıcınıza bildirin."
            return@calis
        }
        guncelleMetin = "Yeni sürüm indiriliyor…"
        val hata = Guncelleyici.guncelle(getApplication(), api, guncelleAdres, apkOzet) {
            guncelleMetin = if (it >= 100) "Kuruluyor…" else "Yeni sürüm indiriliyor… %$it"
        }
        guncelleMetin = if (hata.isEmpty()) "Güncelleme kuruldu. Uygulama kapanırsa yeniden açın." else ""
        mesaj = hata
    }

    /** Ana sayfadaki "Güncelle" bandı: güncelleme ekranını yeniden açar. */
    fun guncelleGoster() {
        guncelleErtelendi = false
        mesaj = ""
        guncelleMetin = ""
        ekran = Ekran.GUNCELLE
    }

    /** "Sonra": güncelleme zorunlu değilse bu açılışta atlanır. */
    fun guncelleErtele() = calis {
        if (guncelleZorunlu) return@calis
        guncelleErtelendi = true
        mesaj = ""
        oturumaGec()
    }

    private suspend fun oturumaGec() {
        when {
            biyometrikAcik -> ekran = Ekran.KILIT
            api.yenilemeVar() && api.yenile() -> anaYukle()
            else -> ekran = Ekran.GIRIS
        }
    }

    fun firmaSec(no: String) = calis {
        mesaj = ""
        val temiz = no.trim().uppercase()
        if (temiz.length < 4) {
            mesaj = "Firma numaranızı yazın."
            return@calis
        }
        val y = api.firmaBul(temiz)
        if (!y.ok) {
            mesaj = y.mesaj
            return@calis
        }
        kasa.yaz("firma_no", temiz)
        if (firmaUygula(y.veri) && guncellemeTamam()) ekran = Ekran.GIRIS
    }

    fun firmaDegistir() = calis {
        if (api.oturumVar()) api.istek("cikis", JSONObject())
        firmayiUnut()
        mesaj = ""
        ekran = Ekran.FIRMA
    }

    private fun firmayiUnut() {
        runCatching { Bildirim.durdur(getApplication()) }
        val kimlik = api.cihazKimlik
        Biyometrik.sil()
        kasa.temizle()
        kasa.yaz("cihaz_kimlik", kimlik)
        api.oturumuSil()
        firmaAd = ""
        kullanici = null
        kodGerekli = false
    }

    fun giris(email: String, parola: String, kod: String) = calis {
        mesaj = ""
        if (email.isBlank() || parola.isEmpty()) {
            mesaj = "E-posta adresinizi ve parolanızı yazın."
            return@calis
        }
        val g = JSONObject()
            .put("firma_no", kasa.oku("firma_no") ?: "")
            .put("email", email.trim())
            .put("parola", parola)
            .put("cihaz_kimlik", api.cihazKimlik)
            .put("cihaz_adi", api.cihazAdi)
            .put("platform", "android")
            .put("cihaz_sirri", kasa.oku("cihaz_sirri") ?: "")
            .put("kod", kod.trim())
            .put("uygulama_surum", surum)
        val y = api.istek("giris", g, oturum = false)
        if (y.ok) {
            api.oturumuYaz(y.veri)
            kullanici = y.veri.optJSONObject("kullanici")
            kodGerekli = false
            // Telefon değişmiş ya da anahtar silinmişse biyometrik giriş yeniden önerilir
            biyometrikTeklif = !biyometrikAcik && Biyometrik.kullanilabilir(getApplication())
            anaYukle()
        } else {
            if (y.hataKodu == "tfa" || y.hataKodu == "tfa_hatali") kodGerekli = true
            mesaj = y.mesaj
        }
    }

    fun biyometrikAc() = calis {
        biyometrikTeklif = false
        val acik = Biyometrik.anahtarUret()
        if (acik == null) {
            mesaj = "Bu telefonda parmak izi / yüz tanıma anahtarı oluşturulamadı."
            return@calis
        }
        val y = api.istek("biyometrik/kaydet", JSONObject().put("acik_anahtar", acik))
        if (y.ok) kasa.yaz("biyometrik", "1") else {
            Biyometrik.sil()
            mesaj = y.mesaj
        }
    }

    fun biyometrikKapat() = calis {
        api.istek("biyometrik/kapat", JSONObject())
        Biyometrik.sil()
        kasa.yaz("biyometrik", null)
    }

    /** Kilit ekranı: sunucudan tek kullanımlık metin alınır, parmak izi ile imzalanır, oturum açılır. */
    fun kilitAc(activity: FragmentActivity) = calis {
        mesaj = ""
        val m = api.istek("biyometrik/meydan", JSONObject().put("cihaz_kimlik", api.cihazKimlik), oturum = false)
        if (!m.ok) {
            if (m.kod == 0) mesaj = m.mesaj else parolayaDus(m.mesaj)
            return@calis
        }
        val s = Biyometrik.imzala(activity, m.veri.metin("meydan"), "Giriş için doğrulayın")
        if (s.anahtarGecersiz) {
            parolayaDus("Telefonun parmak izi / yüz kayıtları değişmiş. Güvenlik için parolanızla giriş yapın.")
            return@calis
        }
        if (s.imza == null) {
            mesaj = s.mesaj
            return@calis
        }
        val y = api.istek("biyometrik/giris", JSONObject().put("cihaz_kimlik", api.cihazKimlik).put("imza", s.imza).put("cihaz_adi", api.cihazAdi).put("uygulama_surum", surum), oturum = false)
        if (y.ok) {
            api.oturumuYaz(y.veri)
            kullanici = y.veri.optJSONObject("kullanici")
            anaYukle()
        } else if (y.kod == 0 || y.hataKodu == "meydan" || y.hataKodu == "eszamanli") {
            mesaj = y.mesaj
        } else {
            parolayaDus(y.mesaj)
        }
    }

    private fun parolayaDus(neden: String) {
        Biyometrik.sil()
        kasa.yaz("biyometrik", null)
        mesaj = neden
        ekran = Ekran.GIRIS
    }

    fun parolaIleGir() {
        mesaj = ""
        ekran = Ekran.GIRIS
    }

    fun yenile() = calis { anaYukle() }

    /** Ana sayfa: web panelindeki ana sayfanın karşılığı (durum kartları, duyurular, özet, son kayıtlar). */
    private suspend fun anaYukle() {
        ekran = Ekran.ANA
        val y = api.istek("panel")
        if (!oturumGecerli(y)) return
        if (y.ok) {
            panel = y.veri
            y.veri.optJSONObject("kullanici")?.let { kullanici = it }
            ozetMesaj = ""
            vadeKontrol()
            runCatching { duyuruKontrol() }
        } else {
            panel = null
            ozetMesaj = if (y.kod == 404) "Ana sayfa bilgileri alınamadı: firmanızın Servis Masası sürümü güncellenmeli." else y.mesaj
            if (kullanici == null) {
                val b = api.istek("ben")
                if (b.ok) kullanici = b.veri.optJSONObject("kullanici")
            }
        }
    }

    private fun dizi(a: JSONArray?): List<JSONObject> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    /** Bir bölümün listesini açar. [ek]: ek süzgeç (ör. durum), [baslik]: liste başlığı, [alt]: başka bir listenin içinden mi açıldı. */
    fun modulAc(m: Modul, ek: Map<String, String> = emptyMap(), baslik: String = m.baslik, alt: Boolean = false) = calis {
        val onceki = modul
        if (alt && onceki != null) yigin.addLast(Triple(onceki, listeEk, listeBaslik)) else if (!alt) yigin.clear()
        listeKur(m, ek, baslik)
    }

    private suspend fun listeKur(m: Modul, ek: Map<String, String>, baslik: String, ara: String = "") {
        modul = m
        listeEk = ek
        listeBaslik = baslik
        listeAra = ara
        liste = emptyList()
        listeToplam = -1
        listeOzet = ""
        secili = null
        mesaj = ""
        bilgi = ""
        ekran = Ekran.LISTE
        listeGetir(1, false)
    }

    /** Ana sayfadaki "Servis Kaydı Ara" kutusu */
    fun servisAra(q: String) = calis {
        yigin.clear()
        listeKur(Moduller.servis, emptyMap(), Moduller.servis.baslik, q.trim())
    }

    /** Menüden "… Ekle": bölümün listesi arkada açılır, önde yeni kayıt formu gelir. */
    fun yeniKayit(m: Modul) = calis {
        yigin.clear()
        listeKur(m, emptyMap(), m.baslik)
        formHazirla(null)
    }

    /** Menüden "Ana Sayfa" */
    fun anaSayfa() = calis {
        yigin.clear()
        modul = null
        liste = emptyList()
        secili = null
        form = null
        mesaj = ""
        bilgi = ""
        anaYukle()
    }

    fun listeYenile() = calis { listeGetir(1, false) }
    fun listeDevam() = calis { if (listeSayfa < listeSayfaSayisi) listeGetir(listeSayfa + 1, true) }

    private suspend fun listeGetir(sayfa: Int, ekle: Boolean) {
        val m = modul ?: return
        val sorgu = m.sorgu().toMutableMap()
        sorgu.putAll(listeEk)
        if (listeAra.isNotBlank()) sorgu["q"] = listeAra.trim()
        sorgu["sayfa"] = sayfa.toString()
        val y = api.istek(m.uc, sorgu = sorgu)
        if (!oturumGecerli(y)) return
        if (!y.ok) {
            mesaj = if (y.kod == 404) "Bu bölüm için firmanızın Servis Masası sürümü güncellenmeli." else y.mesaj
            return
        }
        mesaj = ""
        val yeni = dizi(y.veri.optJSONArray(m.anahtar))
        liste = if (ekle) liste + yeni else yeni
        listeSayfa = y.veri.optInt("sayfa", 1)
        listeSayfaSayisi = y.veri.optInt("sayfa_sayisi", 1)
        listeToplam = y.veri.optInt("toplam", liste.size)
        listeOzet = y.veri.metin("ozet_yazi")
    }

    fun listeKapat() = calis {
        val onceki = yigin.removeLastOrNull()
        if (onceki != null) {
            listeKur(onceki.first, onceki.second, onceki.third)
            return@calis
        }
        modul = null
        liste = emptyList()
        secili = null
        mesaj = ""
        bilgi = ""
        anaYukle()
    }

    /** Listeden bir kayıt seçildi: ayrıntı penceresi açılır, belge eklenebilen bölümlerde ekli belgeler getirilir. */
    fun kayitSec(k: JSONObject) {
        secili = k
        belgeler = emptyList()
        val m = modul ?: return
        if (m.belge) calis { belgeleriGetir(m, k) }
    }

    private suspend fun belgeleriGetir(m: Modul, k: JSONObject) {
        val y = api.istek("belgeler", sorgu = mapOf("tur" to m.kayitTur, "id" to k.optInt("id").toString()))
        if (y.ok && secili === k) belgeler = dizi(y.veri.optJSONArray("kayitlar"))
    }

    /** Seçili kayda fotoğraf / belge ekler (fotoğraf yüklenmeden önce küçültülmüş JPEG'tir). */
    fun belgeYukle(veri: ByteArray) = calis {
        val m = modul ?: return@calis
        val k = secili ?: return@calis
        mesaj = ""
        val y = api.istek("belge_ekle", JSONObject(), sorgu = mapOf("tur" to m.kayitTur, "id" to k.optInt("id").toString()), dosya = Pair("foto.jpg", veri))
        if (!oturumGecerli(y)) return@calis
        if (y.ok) {
            bilgi = y.mesaj
            belgeleriGetir(m, k)
        } else {
            secili = null
            mesaj = if (y.kod == 404) "Belge eklemek için firmanızın Servis Masası sürümü güncellenmeli." else y.mesaj
        }
    }

    fun belgeAc(kimlik: String) = calis {
        val b = api.bayt("belge", mapOf("kimlik" to kimlik))
        val resim = if (b == null) null else runCatching { BitmapFactory.decodeByteArray(b, 0, b.size) }.getOrNull()
        if (resim == null) {
            secili = null
            mesaj = "Belge açılamadı (PDF belgeler web panelinden görüntülenir)."
        } else belgeResim = resim
    }

    /** Kaydı siler (yalnızca yöneticiler; kural sunucuda denetlenir). */
    fun kayitSil(k: JSONObject) = calis {
        val m = modul ?: return@calis
        secili = null
        mesaj = ""
        bilgi = ""
        val y = api.istek("sayfa", JSONObject().put("islem", "sil").put("id", k.optInt("id").toString()), sorgu = mapOf("s" to m.silSayfa))
        if (!oturumGecerli(y)) return@calis
        if (!y.ok) {
            mesaj = if (y.kod == 404) "Silme için firmanızın Servis Masası sürümü güncellenmeli." else y.mesaj
            return@calis
        }
        listeGetir(1, false)
        bilgi = y.veri.metin("msg").ifEmpty { "Kayıt silindi." }
    }

    private suspend fun sabitleriGetir(f: FormTanim): Boolean {
        if (f.alanlar.none { it.kaynak.isNotEmpty() && it.tur != Tur.ARA }) return true   // seçenek listeleri her açılışta yenilenir (yeni eklenen marka, teknisyen…)
        val y = api.istek("sabitler")
        if (!oturumGecerli(y)) return false
        if (y.ok) sabitler = y.veri
        return true
    }

    /** Ekleme formunu ([kayit] verilirse düzenleme formunu) açar. Düzenlemede mevcut değerler sunucudan alınır. */
    fun formAc(kayit: JSONObject? = null) = calis { formHazirla(kayit) }

    private fun varsayilanCoz(v: String): String = when (v) {
        "bugun" -> LocalDate.now().toString()
        "ben_id" -> sabitler?.optJSONObject("ben")?.metin("id") ?: ""
        "ben_ad" -> sabitler?.optJSONObject("ben")?.metin("adsoyad") ?: ""
        "inceleme" -> (sabitler?.metin("inceleme_ucreti") ?: "").let { if (it == "0") "" else it }
        else -> v
    }

    private suspend fun formHazirla(kayit: JSONObject?) {
        val m = modul ?: return
        val f = m.form ?: return
        formDonus = if (ekran == Ekran.DETAY) Ekran.DETAY else Ekran.LISTE
        formSorgu = emptyMap()
        formOnYukleme = ""
        formEtiket.clear()
        secili = null
        mesaj = ""
        if (!sabitleriGetir(f)) return
        formDeger.clear()
        if (kayit == null) {
            duzenleId = null
            f.alanlar.forEach { a -> formDeger[a.ad] = varsayilanCoz(a.varsayilan) }
        } else {
            val y = api.istek("kayit", sorgu = mapOf("tur" to m.kayitTur, "id" to kayit.optInt("id").toString()))
            if (!oturumGecerli(y)) return
            val v = y.veri.optJSONObject("veri")
            if (!y.ok || v == null) {
                mesaj = if (y.kod == 404) "Düzenleme için firmanızın Servis Masası sürümü güncellenmeli." else y.mesaj
                return
            }
            duzenleId = kayit.optInt("id")
            if (kayit.metin("musteri_adi").isNotEmpty()) formEtiket["musteri_id"] = kayit.metin("musteri_adi")
            f.alanlar.forEach { a ->
                formDeger[a.ad] = when (a.tur) {
                    Tur.COKLU -> v.optJSONArray(a.ad).let { d -> if (d == null) "" else (0 until d.length()).joinToString(",") { d.optString(it) } }
                    Tur.ONAY -> if (v.metin(a.ad) == "1") "1" else ""
                    Tur.PAROLA -> ""
                    Tur.SAAT -> v.metin(a.ad).take(5)
                    Tur.SAYI -> v.metin(a.ad).let { if (it.endsWith(".00")) it.dropLast(3) else it }
                    else -> v.metin(a.ad)
                }
            }
        }
        form = f
        ekran = Ekran.FORM
    }

    /** Bir işlemin küçük formunu açar (ör. durum değiştir, parça ekle, tahsilat). */
    private fun eylemFormu(e: Eylem, kayit: JSONObject) = calis {
        val f = FormTanim(e.etiket, e.sayfa, e.govde(kayit), e.alanlar)
        if (!sabitleriGetir(f)) return@calis
        formDonus = if (ekran == Ekran.DETAY) Ekran.DETAY else Ekran.LISTE
        formSorgu = e.sorgu(kayit)
        formOnYukleme = ""
        duzenleId = null
        formDeger.clear()
        formEtiket.clear()
        val on = e.varsayilan(kayit)
        e.alanlar.forEach { a -> formDeger[a.ad] = on[a.ad] ?: varsayilanCoz(a.varsayilan) }
        form = f
        mesaj = ""
        ekran = Ekran.FORM
    }

    /** Ayar sayfası: değerler sunucudan alınır, kaydedilince ana sayfaya dönülür. */
    fun ayarAc(f: FormTanim, uc: String) = calis {
        val y = api.istek(uc)
        if (!oturumGecerli(y)) return@calis
        val v = y.veri.optJSONObject("veri")
        if (!y.ok || v == null) {
            mesaj = if (y.kod == 404) "Bu bölüm için firmanızın Servis Masası sürümü güncellenmeli." else y.mesaj
            return@calis
        }
        yigin.clear()
        modul = null
        duzenleId = null
        formDonus = Ekran.ANA
        formSorgu = emptyMap()
        formOnYukleme = uc
        formDeger.clear()
        formEtiket.clear()
        f.alanlar.forEach { a -> formDeger[a.ad] = v.metin(a.ad) }
        form = f
        mesaj = ""
        bilgi = ""
        ekran = Ekran.FORM
    }

    /** Etiket okuyucunun bulduğu değerleri (tür → değer) formdaki ilgili alanlara yazar */
    fun taraYaz(yazilan: List<Pair<String, String>>) {
        val f = form ?: return
        val ozet = mutableListOf<String>()
        yazilan.forEach { (tur, v) ->
            if (v.isBlank()) return@forEach
            f.alanlar.firstOrNull { it.tara == tur }?.let { a ->
                formDeger[a.ad] = v.trim().take(190)
                ozet.add(a.etiket + ": " + v.trim())
            }
        }
        mesaj = ""
        if (ozet.isNotEmpty()) bilgi = "Okundu — " + ozet.joinToString(" · ")
    }

    /** Formda seçili markanın adı (küçük harf); marka desenleriyle eşleştirme için */
    fun taraMarka(): String {
        val id = formDeger["marka_id"].orEmpty()
        if (id.isEmpty()) return ""
        return dizi(sabitler?.optJSONArray("markalar")).firstOrNull { it.metin("kod") == id }?.metin("ad").orEmpty().lowercase()
    }

    /** Daha önce kayıtlı modeller: (model, seçili markaya ait mi). Alınamazsa boş liste; okuma yine çalışır. */
    suspend fun modelOneri(): List<Pair<String, Boolean>> {
        val id = formDeger["marka_id"].orEmpty()
        if (id == bilinenMarka) return bilinenModeller
        return try {
            val y = api.istek("model_oneri", sorgu = mapOf("marka_id" to id))
            if (!y.ok) emptyList() else {
                val a = y.veri.optJSONArray("modeller")
                val l = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.let { it.optString(0, "") to (it.optInt(1, 0) == 1) } }.filter { it.first.isNotEmpty() }
                bilinenMarka = id
                bilinenModeller = l
                l
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Arama ile seçim (müşteri, stok parçası) */
    fun araYap(uc: String, q: String) = calis {
        val y = api.istek(uc, sorgu = mapOf("q" to q.trim()))
        if (!oturumGecerli(y)) return@calis
        araSonuc = if (y.ok) dizi(y.veri.optJSONArray("kayitlar")) else emptyList()
    }

    fun araTemizle() {
        araSonuc = emptyList()
    }

    /** Servis formundan çıkmadan hızlı müşteri ekleme (web panelindeki "hızlı ekle") */
    fun hizliMusteri(alan: String, ad: String, tel: String, bitti: () -> Unit) = calis {
        mesaj = ""
        val y = api.istek("sayfa", JSONObject().put("adsoyad", ad.trim()).put("tel", tel.trim()), sorgu = mapOf("s" to "musteri-ekle", "hizli" to "1"))
        if (!oturumGecerli(y)) return@calis
        val secim = y.veri.optJSONObject("pick")
        if (!y.ok || secim == null) {
            mesaj = y.mesaj
            return@calis
        }
        formDeger[alan] = secim.optInt("id").toString()
        formEtiket[alan] = secim.metin("label")
        bitti()
    }

    fun formKapat() {
        form = null
        duzenleId = null
        mesaj = ""
        if (formDonus == Ekran.ANA) anaSayfa() else ekran = formDonus
    }

    /** Formu gönderir. Kayıt, web panelindeki aynı sayfanın kurallarıyla sunucuda yapılır. */
    fun formGonder() = calis {
        val f = form ?: return@calis
        mesaj = ""
        f.alanlar.firstOrNull { it.zorunlu && formDeger[it.ad].isNullOrBlank() }?.let {
            mesaj = "${it.etiket} zorunludur."
            return@calis
        }
        val alanlar = JSONObject()
        f.alanlar.forEach { a ->
            val v = (formDeger[a.ad] ?: "").trim()
            if (a.tur == Tur.COKLU) alanlar.put(a.ad, JSONArray(v.split(",").filter { it.isNotBlank() })) else alanlar.put(a.ad, v)
        }
        val g = if (f.sarmala.isNotEmpty()) JSONObject().put(f.sarmala, alanlar) else alanlar
        val id = duzenleId
        // Düzenlemede sabit değerler gönderilmez: kaydın kendi değerleri sunucuda korunur
        if (id == null) f.sabit.forEach { (k, v) -> if (v is List<*>) g.put(k, JSONArray(v)) else g.put(k, v.toString()) }
        val sorgu = (f.sorgu + formSorgu).toMutableMap()
        if (id == null) sorgu["s"] = f.sayfa else {
            sorgu["s"] = f.duzenle
            sorgu["duzenle"] = modul?.kayitTur ?: ""
            sorgu["id"] = id.toString()
        }
        val y = api.istek("sayfa", g, sorgu = sorgu)
        if (!oturumGecerli(y)) return@calis
        if (!y.ok) {
            mesaj = if (y.kod == 404) "Bu işlem için firmanızın Servis Masası sürümü güncellenmeli." else y.mesaj
            return@calis
        }
        val sonuc = y.veri.metin("msg").ifEmpty { "Kaydedildi." }
        val donus = formDonus
        form = null
        duzenleId = null
        // Yeni servis kaydı: doğrudan kaydın ayrıntısı açılır
        val yeniServis = if (id == null && f.sayfa == "servis-ekle") Regex("[?&]id=(\\d+)").find(y.veri.metin("redirect"))?.groupValues?.get(1)?.toIntOrNull() else null
        when {
            yeniServis != null -> {
                ekran = Ekran.LISTE
                servisGetir(yeniServis)
            }
            donus == Ekran.DETAY -> detay?.optInt("id")?.let { servisGetir(it) }
            donus == Ekran.ANA -> anaYukle()
            else -> {
                ekran = Ekran.LISTE
                listeGetir(1, false)
            }
        }
        // yeni / değişen hatırlatıcı ve randevular için telefonun alarmı güncellenir
        if (f.sayfa.startsWith("hatirlatici") || f.sayfa.startsWith("randevu") || f.sayfa.startsWith("takvim") || donus != Ekran.ANA) runCatching { vadeKontrol() }
        bilgi = sonuc
    }

    /** Kayıt üzerindeki tek dokunuşluk işlem (ödendi, tahsil edildi…). */
    fun eylemYap(e: Eylem, kayit: JSONObject) {
        secili = null
        val git = e.git
        if (git != null) {
            val (m, ek, baslik) = git(kayit)
            modulAc(m, ek, baslik, alt = true)
        } else if (e.alanlar.isNotEmpty()) eylemFormu(e, kayit) else eylemCalistir(e, kayit)
    }

    private fun eylemCalistir(e: Eylem, kayit: JSONObject) = calis {
        mesaj = ""
        bilgi = ""
        val g = JSONObject()
        e.govde(kayit).forEach { (k, v) -> g.put(k, v) }
        val y = api.istek("sayfa", g, sorgu = e.sorgu(kayit) + mapOf("s" to e.sayfa))
        if (!oturumGecerli(y)) return@calis
        if (!y.ok) {
            mesaj = if (y.kod == 404) "Bu işlem için firmanızın Servis Masası sürümü güncellenmeli." else y.mesaj
            return@calis
        }
        if (ekran == Ekran.DETAY) detay?.optInt("id")?.let { servisGetir(it) } else listeGetir(1, false)
        bilgi = y.veri.metin("msg").ifEmpty { "İşlem tamamlandı." }
    }

    /** Servis ayrıntısındaki bir kalemle ilgili işlem (parça kaldır, onay sil, kaydı sil…) */
    fun detayIslem(govde: Map<String, String>, kapat: Boolean = false) = calis {
        val id = detay?.optInt("id") ?: return@calis
        mesaj = ""
        bilgi = ""
        val g = JSONObject()
        govde.forEach { (k, v) -> g.put(k, v) }
        val y = api.istek("sayfa", g, sorgu = mapOf("s" to "servis-detay", "id" to id.toString()))
        if (!oturumGecerli(y)) return@calis
        if (!y.ok) {
            mesaj = y.mesaj
            return@calis
        }
        val sonuc = y.veri.metin("msg").ifEmpty { "İşlem tamamlandı." }
        if (kapat) {
            detay = null
            detayTam = null
            ekran = detayOnce
            if (detayOnce == Ekran.LISTE) listeGetir(1, false) else anaYukle()
        } else servisGetir(id)
        bilgi = sonuc
    }

    /** Servis kaydının fotoğrafını değiştirir / ekler. */
    fun servisFoto(veri: ByteArray) = calis {
        val id = detay?.optInt("id") ?: return@calis
        mesaj = ""
        val y = api.istek("sayfa", JSONObject(), sorgu = mapOf("s" to "servis-duzenle", "duzenle" to "servis", "id" to id.toString()), dosya = Pair("foto.jpg", veri), dosyaAlan = "resim")
        if (!oturumGecerli(y)) return@calis
        if (y.ok) {
            servisGetir(id)
            bilgi = "Fotoğraf kaydedildi."
        } else mesaj = y.mesaj
    }

    fun servisFotoAc() = calis {
        val id = detay?.optInt("id") ?: return@calis
        val b = api.bayt("servis_resim", mapOf("id" to id.toString()))
        val resim = if (b == null) null else runCatching { BitmapFactory.decodeByteArray(b, 0, b.size) }.getOrNull()
        if (resim == null) mesaj = "Fotoğraf açılamadı." else belgeResim = resim
    }

    /** Servis kaydını düzenleme formu (ayrıntı ekranından) */
    fun servisDuzenle() = calis {
        val s = detay ?: return@calis
        val hedef = if (s.optBoolean("dis")) Moduller.disServis else Moduller.servis
        if (modul?.form !== hedef.form) {
            modul = hedef
            listeEk = emptyMap()
            listeBaslik = hedef.baslik
        }
        formHazirla(s)
    }

    /** Toplu kayıt: CSV / Excel dosyası yükler. */
    fun topluAc() {
        yigin.clear()
        modul = null
        mesaj = ""
        bilgi = ""
        ekran = Ekran.TOPLU
    }

    fun topluYukle(ad: String, veri: ByteArray) = calis {
        mesaj = ""
        bilgi = ""
        val y = api.istek("sayfa", JSONObject(), sorgu = mapOf("s" to "servis-toplu"), dosya = Pair(ad, veri))
        if (!oturumGecerli(y)) return@calis
        if (y.ok) bilgi = y.mesaj else mesaj = if (y.kod == 404) "Toplu kayıt için firmanızın Servis Masası sürümü güncellenmeli." else y.mesaj
    }

    private suspend fun servisGetir(id: Int) {
        val y = api.istek("servis", sorgu = mapOf("id" to id.toString()))
        if (!oturumGecerli(y)) return
        if (y.ok) {
            detay = y.veri.optJSONObject("servis")
            detayTam = y.veri
            gecmis = dizi(y.veri.optJSONArray("gecmis"))
            if (ekran != Ekran.DETAY) detayOnce = if (ekran == Ekran.LISTE || ekran == Ekran.FORM) Ekran.LISTE else Ekran.ANA
            ekran = Ekran.DETAY
        } else mesaj = y.mesaj
    }

    fun servisAc(id: Int) = calis {
        bilgi = ""
        servisGetir(id)
    }

    fun detayKapat() {
        detay = null
        detayTam = null
        mesaj = ""
        if (detayOnce == Ekran.LISTE && modul != null) {
            ekran = Ekran.LISTE
            listeYenile()
        } else {
            ekran = Ekran.ANA
            anaSayfa()
        }
    }

    fun cikis() = calis {
        runCatching { Bildirim.durdur(getApplication()) }
        api.istek("cikis", JSONObject())
        api.oturumuSil()
        kullanici = null
        panel = null
        liste = emptyList()
        mesaj = ""
        ekran = if (biyometrikAcik) Ekran.KILIT else Ekran.GIRIS
    }

    /** Oturum sunucuda kapatılmışsa (süre doldu, yönetici çıkardı, cihaz engellendi) giriş ekranına döner. */
    private fun oturumGecerli(y: Yanit): Boolean {
        if (y.kod != 401 && !(y.kod == 403 && (y.hataKodu == "kapali" || y.hataKodu == "cihaz_engel"))) return true
        api.oturumuSil()
        kullanici = null
        panel = null
        liste = emptyList()
        form = null
        secili = null
        detay = null
        mesaj = y.mesaj
        ekran = if (y.kod == 401 && biyometrikAcik) Ekran.KILIT else Ekran.GIRIS
        return false
    }

    /** Vadesi gelenleri sorar, yenilerini telefon bildirimi olarak gösterir ve arka plan kontrolünü kurar. */
    private suspend fun vadeKontrol() {
        val app: Application = getApplication()
        runCatching { Bildirim.zamanla(app) }
        val y = api.istek("vadeler")
        if (y.ok) runCatching { Bildirim.isle(app, y.veri, true) }
    }

    // ------------------------------------------------------------ KavisCloud duyuruları
    private fun gorulenPopuplar(): Set<String> = getApplication<Application>().getSharedPreferences("sm_duyuru", 0).getStringSet("gorulen", emptySet()) ?: emptySet()

    /** Yönetim panelinden gönderilen pop-up ve duyuruları alır; daha önce kapatılan pop-up yeniden gösterilmez. */
    private suspend fun duyuruKontrol() {
        val gorulen = gorulenPopuplar()
        val y = api.istek("duyurular", JSONObject().put("seen", JSONArray(gorulen.mapNotNull { it.toIntOrNull() })))
        if (!y.ok) return
        destekVar = y.veri.optBoolean("destek", false)
        destekDavet = y.veri.optBoolean("davet", false)
        kcDuyurular = dizi(y.veri.optJSONArray("duyurular")).map { it.metin("metin") }.filter { it.isNotEmpty() }
        popuplar = dizi(y.veri.optJSONArray("popups")).filter { it.optInt("id") > 0 && it.optInt("id").toString() !in gorulen }
    }

    fun popupKapat() {
        val p = popuplar.firstOrNull() ?: return
        val kayit = getApplication<Application>().getSharedPreferences("sm_duyuru", 0)
        val yeni = (gorulenPopuplar() + p.optInt("id").toString()).toList().takeLast(300).toSet()
        kayit.edit().putStringSet("gorulen", yeni).apply()
        popuplar = popuplar.drop(1)
    }

    // ------------------------------------------------------------ Sohbet
    /** Ekip sohbeti: kişi / kanal listesi */
    fun sohbetAc() = calis {
        val y = api.istek("sohbet_kanallar")
        if (!oturumGecerli(y)) return@calis
        if (!y.ok) {
            mesaj = if (y.kod == 404) "Ekip sohbeti için firmanızın Servis Masası sürümü güncellenmeli." else y.mesaj
            return@calis
        }
        sohbetTur = "ekip"
        sohbetKanal = null
        sohbetMesajlar.clear()
        sohbetKanallar = dizi(y.veri.optJSONArray("kanallar"))
        mesaj = ""
        bilgi = ""
        ekran = Ekran.SOHBET
    }

    fun sohbetKanalAc(k: JSONObject) {
        sohbetMesajlar.clear()
        sohbetDurum = ""
        mesaj = ""
        sohbetKanal = k
    }

    /** Canlı destek: destek ekibiyle yazışma */
    fun destekAc() {
        sohbetTur = "destek"
        sohbetMesajlar.clear()
        sohbetDurum = ""
        sohbetKanal = JSONObject().put("kanal", "destek").put("ad", "Canlı Destek")
        destekDavet = false
        mesaj = ""
        bilgi = ""
        ekran = Ekran.SOHBET
    }

    /** Açık görüşmedeki yeni mesajları alır (ekran açıkken birkaç saniyede bir çağrılır). */
    suspend fun sohbetYokla() {
        val k = sohbetKanal ?: return
        if (sohbetYokluyor) return
        sohbetYokluyor = true
        try {
            val kanal = k.metin("kanal")
            val son = sohbetMesajlar.maxOfOrNull { it.optInt("id") } ?: 0
            val y = if (sohbetTur == "destek") api.istek("destek", sorgu = mapOf("son" to son.toString()))
            else api.istek("sohbet", sorgu = mapOf("kanal" to kanal, "son" to son.toString()))
            if (sohbetKanal?.metin("kanal") != kanal) return   // bu arada başka görüşmeye geçildi
            if (!y.ok) {
                if (y.kod == 404) mesaj = "Bu özellik için firmanızın Servis Masası sürümü güncellenmeli."
                return
            }
            val yeni = dizi(y.veri.optJSONArray("mesajlar")).filter { it.optInt("id") > son }
            if (yeni.isNotEmpty()) sohbetMesajlar.addAll(yeni)
            if (sohbetTur == "destek") sohbetDurum = when {
                !y.veri.optBoolean("ulasildi", true) -> "Destek sunucusuna ulaşılamıyor"
                y.veri.optBoolean("yaziyor") -> "Destek yazıyor…"
                y.veri.metin("durum") == "waiting" -> "Sırada bekliyorsunuz" + (y.veri.optInt("sira").let { if (it > 0) " ($it. sıra)" else "" })
                y.veri.metin("durum") == "closed" -> "Görüşme kapatıldı; yeni mesaj yazarak yeniden başlatabilirsiniz"
                y.veri.metin("durum") == "none" -> if (y.veri.isNull("cevrimici") || y.veri.optBoolean("cevrimici")) "Mesajınızı yazın, destek ekibi yanıtlayacak" else "Destek ekibi şu an çevrimdışı; mesajınız iletilecek"
                else -> "Destek ekibine bağlısınız"
            }
        } catch (e: Exception) {
        } finally {
            sohbetYokluyor = false
        }
    }

    fun sohbetGonder(metin: String, bitti: () -> Unit) {
        val k = sohbetKanal ?: return
        val m = metin.trim()
        if (m.isEmpty() || sohbetGonderiyor) return
        sohbetGonderiyor = true
        viewModelScope.launch {
            try {
                val g = JSONObject().put("mesaj", m.take(2000))
                val y = if (sohbetTur == "destek") api.istek("destek", g) else api.istek("sohbet", g.put("kanal", k.metin("kanal")))
                if (y.ok) {
                    mesaj = ""
                    bitti()
                    sohbetYokla()
                } else mesaj = y.mesaj.ifEmpty { "Mesaj gönderilemedi." }
            } catch (e: Exception) {
                mesaj = "Mesaj gönderilemedi."
            } finally {
                sohbetGonderiyor = false
            }
        }
    }

    /** Geri: ekip sohbetinde görüşmeden listeye, listeden ana sayfaya */
    fun sohbetGeri() {
        if (sohbetTur == "ekip" && sohbetKanal != null) {
            sohbetKanal = null
            sohbetMesajlar.clear()
            mesaj = ""
            viewModelScope.launch { runCatching { val y = api.istek("sohbet_kanallar"); if (y.ok) sohbetKanallar = dizi(y.veri.optJSONArray("kanallar")) } }
        } else {
            sohbetKanal = null
            sohbetMesajlar.clear()
            anaSayfa()
        }
    }

    /** Uygulama arka plana alındı. */
    fun arkaPlan() {
        arkaPlanZamani = System.currentTimeMillis()
    }

    /** Uygulama yeniden öne geldi: 5 dakikadan uzun süre arka planda kaldıysa kilitlenir. */
    fun onPlan() {
        val sure = System.currentTimeMillis() - arkaPlanZamani
        if (arkaPlanZamani > 0 && sure > 5 * 60_000 && biyometrikAcik && (ekran == Ekran.ANA || ekran == Ekran.DETAY || ekran == Ekran.LISTE || ekran == Ekran.FORM || ekran == Ekran.TOPLU || ekran == Ekran.SOHBET)) {
            detay = null
            form = null
            secili = null
            mesaj = ""
            ekran = Ekran.KILIT
        }
        arkaPlanZamani = 0
    }

    private fun calis(is_: suspend () -> Unit) {
        if (mesgul) return
        mesgul = true
        viewModelScope.launch {
            try {
                is_()
            } catch (e: Exception) {
                mesaj = "Beklenmeyen bir hata oluştu. Tekrar deneyin."
            } finally {
                mesgul = false
            }
        }
    }

    private fun surumKucuk(a: String, b: String): Boolean {
        if (b.isBlank()) return false
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p < q
        }
        return false
    }
}
