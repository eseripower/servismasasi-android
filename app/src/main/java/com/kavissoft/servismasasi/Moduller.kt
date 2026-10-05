package com.kavissoft.servismasasi

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Form alanı türleri */
enum class Tur { METIN, COKSATIR, SAYI, TELEFON, TARIH, SAAT, SECIM, COKLU, ONAY, PAROLA, ARA, DESEN }

/** Bir form alanı. [kaynak] doluysa seçenekler sunucudaki "sabitler" listesinden alınır. */
class Alan(
    val ad: String, val etiket: String, val tur: Tur = Tur.METIN, val zorunlu: Boolean = false, val varsayilan: String = "",
    val secenek: List<Pair<String, String>> = emptyList(), val kaynak: String = "",
    /** Doluysa alanın yanında kamera düğmesi çıkar ve etiket fotoğrafından okunur: "model", "seri" ya da "lisans" */
    val tara: String = ""
)

/**
 * Kayıt formu. [sayfa]: yeni kayıt için web panelindeki sayfa; [duzenle]: düzenleme sayfası.
 * Kayıt, o sayfanın kurallarıyla sunucuda yapılır. [sabit] yalnızca yeni kayıtta gönderilir.
 */
class FormTanim(
    val baslik: String, val sayfa: String, val sabit: Map<String, Any>, val alanlar: List<Alan>, val duzenle: String = "",
    /** Sayfaya adres çubuğunda gönderilen ek değerler (ör. dış servis için dis=1) */
    val sorgu: Map<String, String> = emptyMap(),
    /** Doluysa alanlar bu adın altında gönderilir (ayar sayfalarındaki a[anahtar] biçimi) */
    val sarmala: String = ""
)

/** Listede bir satırın görünümü */
class Satir(val baslik: String, val alt: String = "", val alt2: String = "", val sag: String = "", val uyari: Boolean = false)

/**
 * Kayıt üzerinde tek dokunuşla yapılan işlem (ödendi, tahsil edildi…); web panelindeki aynı işlemi çalıştırır.
 * [alanlar] doluysa önce küçük bir form açılır (ör. ödeme tutarı). [git] doluysa işlem yerine alt liste açılır.
 */
class Eylem(
    val etiket: String, val sayfa: String, val kosul: (JSONObject) -> Boolean = { true }, val alanlar: List<Alan> = emptyList(),
    val git: ((JSONObject) -> Triple<Modul, Map<String, String>, String>)? = null,
    /** Sayfaya adres çubuğunda gönderilen değerler (ör. kaydın kimliği) */
    val sorgu: (JSONObject) -> Map<String, String> = { emptyMap() },
    /** Küçük formdaki alanların başlangıç değerleri */
    val varsayilan: (JSONObject) -> Map<String, String> = { emptyMap() },
    val ikon: String = "", val tehlike: Boolean = false,
    val govde: (JSONObject) -> Map<String, String> = { emptyMap() }
)

/**
 * Uygulamadaki bir bölüm: listesi, satır görünümü, ayrıntı alanları, ekleme / düzenleme formu ve işlemleri.
 * [kayitTur]: sunucudaki kayıt türü (düzenleme ve belge için), [silSayfa]: silme işleminin yapıldığı panel sayfası,
 * [belge]: kayda fotoğraf / belge eklenebilir mi.
 */
class Modul(
    val kod: String, val baslik: String, val uc: String, val anahtar: String = "kayitlar",
    val sorgu: () -> Map<String, String> = { emptyMap() }, val aramali: Boolean = true, val servisMi: Boolean = false,
    val kayitTur: String = "", val silSayfa: String = "", val belge: Boolean = false,
    val satir: (JSONObject) -> Satir, val detay: (JSONObject) -> List<Pair<String, String>> = { emptyList() }, val form: FormTanim? = null,
    val eylemler: List<Eylem> = emptyList()
)

private fun bugun(): String = LocalDate.now().toString()

private fun trTarih(t: String): String = try {
    LocalDate.parse(t.take(10)).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
} catch (e: Exception) {
    t
}

private fun birles(vararg p: String): String = p.filter { it.isNotBlank() }.joinToString(" · ")
private fun kimlik(k: JSONObject): String = k.optInt("id").toString()
private val aktifPasif = listOf("1" to "Aktif", "0" to "Pasif")

object Moduller {
    private val servisAlanlari = listOf(
        Alan("cihaz_id", "Cihaz Türü", Tur.SECIM, kaynak = "cihaz_turleri"), Alan("marka_id", "Marka", Tur.SECIM, kaynak = "markalar"),
        Alan("model", "Model", tara = "model"), Alan("seri_no", "Seri Numarası", tara = "seri"), Alan("ariza", "Arıza / Şikâyet", Tur.COKSATIR, zorunlu = true),
        Alan("aksesuar_sec", "Aksesuarlar", Tur.COKLU, kaynak = "aksesuarlar"), Alan("aksesuar", "Diğer Aksesuar"),
        Alan("dis_gorunum", "Dış Görünüm / Fiziksel Durum", Tur.COKSATIR),
        Alan("teknisyen_id", "Teknisyen", Tur.SECIM, varsayilan = "ben_id", kaynak = "teknisyenler"), Alan("kabul_eden", "Kabul Eden", varsayilan = "ben_ad"),
        Alan("servis_tarihi", "Servis Tarihi", Tur.TARIH, varsayilan = "bugun"),
        Alan("oncelik", "Öncelik", Tur.SECIM, varsayilan = "1", kaynak = "oncelik"),
        Alan("garanti", "Garanti", Tur.SECIM, varsayilan = "0", secenek = listOf("0" to "Garanti Yok", "1" to "Garantili")),
        Alan("kullanim_durumu", "Kullanım Durumu", Tur.SECIM, varsayilan = "2", secenek = listOf("1" to "Yeni", "2" to "Eski / Kullanılmış Cihaz")),
        Alan("mudahale_gormus", "Cihaz daha önce müdahale görmüş", Tur.ONAY), Alan("veri_yedegi", "Veri yedeği alınsın", Tur.ONAY),
        Alan("isletim_lisansi", "İşletim Sistemi Lisans Bilgileri", tara = "lisans"), Alan("kart_kodu", "Kart / Donanım Kodu"),
        Alan("kilit_sifresi", "Ekran Kilidi Şifresi / PIN"), Alan("kilit_deseni", "Ekran Kilidi Deseni", Tur.DESEN)
    )
    private val servisSatir: (JSONObject) -> Satir = { s -> Satir(s.metin("musteri").ifEmpty { "Müşteri yok" }, birles(s.metin("marka"), s.metin("model"), s.metin("cihaz")), birles(s.metin("takip_kodu"), s.metin("firma")), s.metin("durum_ad"), s.optBoolean("gecikmis")) }

    val servis = Modul(
        kod = "servis", baslik = "Servis Kayıtları", uc = "servisler", servisMi = true, kayitTur = "servis", satir = servisSatir,
        form = FormTanim(
            "Servis Kaydı", "servis-ekle", emptyMap(),
            listOf(Alan("musteri_id", "Müşteri", Tur.ARA, zorunlu = true, kaynak = "musteriler")) + servisAlanlari +
                listOf(Alan("inceleme_ucreti", "İnceleme Ücreti (TL)", Tur.SAYI, varsayilan = "inceleme"), Alan("bildir", "Müşteriye SMS / e-posta gönder", Tur.ONAY, varsayilan = "1")),
            duzenle = "servis-duzenle"
        )
    )

    /** Dış servis: başka bir firmanın (iş ortağının) gönderdiği cihazlar */
    val disServis = Modul(
        kod = "dis", baslik = "Dış Servis Kayıtları", uc = "servisler", sorgu = { mapOf("dis" to "1") }, servisMi = true, kayitTur = "servis", satir = servisSatir,
        form = FormTanim(
            "Dış Servis Kaydı", "servis-ekle", emptyMap(),
            listOf(
                Alan("is_ortagi_id", "Gönderen Firma (İş Ortağı)", Tur.SECIM, zorunlu = true, kaynak = "is_ortaklari"), Alan("son_kullanici", "Son Kullanıcı (cihazın sahibi)"),
                Alan("firma_tespit", "Firmanın Tespiti / Notu", Tur.COKSATIR), Alan("gelis_sekli", "Geliş Şekli", Tur.SECIM, varsayilan = "elden", kaynak = "ds_gelis"),
                Alan("gelis_kargo", "Geliş Kargo Firması / Takip No"), Alan("hedef_tarih", "Hedef Teslim Tarihi", Tur.TARIH)
            ) + servisAlanlari + listOf(
                Alan("garanti_gun", "Onarım Garantisi (gün, boşsa firma anlaşması)", Tur.SAYI),
                Alan("inceleme_ucreti", "İnceleme Ücreti (TL)", Tur.SAYI, varsayilan = "inceleme"), Alan("bildir", "Firmaya SMS / e-posta gönder", Tur.ONAY, varsayilan = "1")
            ),
            duzenle = "servis-duzenle", sorgu = mapOf("dis" to "1")
        )
    )

    private val sid: (JSONObject) -> Map<String, String> = { mapOf("id" to kimlik(it)) }

    /** Servis kaydı ayrıntısındaki işlemler (web panelindeki servis detay sayfasının işlemleri) */
    val servisEylemleri = listOf(
        Eylem("Durum Değiştir", "servis-detay", ikon = "rotate", sorgu = sid, varsayilan = { mapOf("durum" to it.optInt("durum").toString(), "onarim_sonuc_id" to it.metin("onarim_sonuc_id")) },
            alanlar = listOf(Alan("durum", "Yeni Durum", Tur.SECIM, zorunlu = true, kaynak = "durumlar"), Alan("onarim_sonuc_id", "İşlem Sonucu", Tur.SECIM, kaynak = "onarim_sonuclari"),
                Alan("aciklama", "Açıklama", Tur.COKSATIR), Alan("bildir", "Müşteriye bildirim gönder", Tur.ONAY))) { mapOf("islem" to "durum") },
        Eylem("Yapılan İşlemler", "servis-detay", ikon = "pen-to-square", sorgu = sid, varsayilan = { mapOf("yapilan_islem" to it.metin("yapilan_islem"), "teknisyen_notu" to it.metin("teknisyen_notu"), "teknisyen_id" to it.metin("teknisyen_id")) },
            alanlar = listOf(Alan("yapilan_islem", "Yapılan İşlemler", Tur.COKSATIR), Alan("teknisyen_notu", "Teknisyen Notu (müşteri görmez)", Tur.COKSATIR), Alan("teknisyen_id", "Teknisyen", Tur.SECIM, kaynak = "teknisyenler"))) { mapOf("islem" to "islem") },
        Eylem("Parça Ekle", "servis-detay", ikon = "cube", sorgu = sid, varsayilan = { mapOf("adet" to "1") },
            alanlar = listOf(Alan("parca_id", "Stoktan Parça (stokta yoksa boş bırakın)", Tur.ARA, kaynak = "parcalar"), Alan("parca_adi", "Parça Adı (stok dışı parça için)"),
                Alan("adet", "Adet", Tur.SAYI, zorunlu = true), Alan("birim_fiyat", "Birim Fiyat (boşsa stok satış fiyatı)", Tur.SAYI), Alan("seri_no", "Parça Seri No"),
                Alan("garanti_ay", "Parça Garantisi (ay)", Tur.SAYI), Alan("stoksuz", "Stok yetersiz olsa da ekle", Tur.ONAY))) { mapOf("islem" to "parca_ekle") },
        Eylem("Dışarıdan Parça Ekle", "servis-detay", { it.optBoolean("dis") }, ikon = "cube", sorgu = sid, varsayilan = { mapOf("adet" to "1", "kaynak" to "disari") },
            alanlar = listOf(Alan("parca_adi", "Parça Adı", zorunlu = true), Alan("kaynak", "Parça Kaynağı", Tur.SECIM, kaynak = "ds_kaynak"), Alan("adet", "Adet", Tur.SAYI, zorunlu = true),
                Alan("alis_tutari", "Alış Maliyeti (TL)", Tur.SAYI), Alan("birim_fiyat", "Firmaya Yansıyan Fiyat (boşsa anlaşmaya göre)", Tur.SAYI), Alan("tedarikci", "Tedarikçi"), Alan("fatura_no", "Fatura No"))) { mapOf("islem" to "parca_ekle") },
        Eylem("Ücretler", "servis-detay", { !it.optBoolean("dis") }, ikon = "circle-dollar-to-slot", sorgu = sid,
            varsayilan = { mapOf("iscilik" to it.metin("iscilik_ham"), "inceleme_ucreti" to it.metin("inceleme_ucreti_ham"), "tasima_ucreti" to it.metin("tasima_ucreti_ham"), "indirim" to it.metin("indirim_ham")) },
            alanlar = listOf(Alan("iscilik", "İşçilik (TL)", Tur.SAYI), Alan("inceleme_ucreti", "İnceleme Ücreti (TL)", Tur.SAYI), Alan("tasima_ucreti", "Taşıma Ücreti (TL)", Tur.SAYI), Alan("indirim", "İndirim (TL)", Tur.SAYI))) { mapOf("islem" to "ucret") },
        Eylem("Ücretler", "servis-detay", { it.optBoolean("dis") }, ikon = "circle-dollar-to-slot", sorgu = sid,
            varsayilan = { mapOf("inceleme_ucreti" to it.metin("inceleme_ucreti_ham"), "tasima_ucreti" to it.metin("tasima_ucreti_ham"), "indirim" to it.metin("indirim_ham")) },
            alanlar = listOf(Alan("inceleme_ucreti", "İnceleme Ücreti (TL)", Tur.SAYI), Alan("tasima_ucreti", "Taşıma Ücreti (TL)", Tur.SAYI), Alan("indirim", "İndirim (TL)", Tur.SAYI))) { mapOf("islem" to "ucret") },
        Eylem("İşçilik Kalemi Ekle", "servis-detay", { it.optBoolean("dis") }, ikon = "screwdriver-wrench", sorgu = sid,
            alanlar = listOf(Alan("aciklama", "Yapılan İş", zorunlu = true), Alan("sure_dk", "Süre (dakika)", Tur.SAYI), Alan("ucret", "Ücret (TL)", Tur.SAYI), Alan("ucretsiz", "Ücretsiz (garanti kapsamında)", Tur.ONAY))) { mapOf("islem" to "iscilik_ekle") },
        Eylem("Ek Masraf Ekle", "servis-detay", { it.optBoolean("dis") }, ikon = "file-invoice-dollar", sorgu = sid, varsayilan = { mapOf("tur" to "kargo") },
            alanlar = listOf(Alan("tur", "Masraf Türü", Tur.SECIM, kaynak = "ds_masraf"), Alan("aciklama", "Açıklama"), Alan("tedarikci", "Tedarikçi / Firma"), Alan("maliyet", "Maliyet (TL)", Tur.SAYI),
                Alan("yansit", "Firmaya Yansıyan Tutar (TL)", Tur.SAYI), Alan("yansit_ayni", "Maliyetin aynısını yansıt", Tur.ONAY))) { mapOf("islem" to "masraf_ekle") },
        Eylem("Dönüş Kargo Bilgisi", "servis-detay", { it.optBoolean("dis") }, ikon = "box", sorgu = sid, varsayilan = { mapOf("donus_kargo" to it.metin("donus_kargo")) },
            alanlar = listOf(Alan("donus_kargo", "Dönüş Kargo Firması / Takip No"))) { mapOf("islem" to "dis_kargo") },
        Eylem("Tahsilat Al", "servis-detay", ikon = "money-bill-transfer", sorgu = sid, varsayilan = { mapOf("tutar" to it.metin("kalan_ham").let { k -> if (k == "0.00") "" else k.removeSuffix(".00") }) },
            alanlar = listOf(Alan("tutar", "Tutar (TL)", Tur.SAYI, zorunlu = true), Alan("aciklama", "Açıklama"))) { mapOf("islem" to "tahsilat") },
        Eylem("Müşteri Onayı İste", "servis-detay", ikon = "circle-check", sorgu = sid, varsayilan = { mapOf("tur" to "fiyat", "kanal" to "sms", "durum_guncelle" to "1") },
            alanlar = listOf(Alan("tur", "Onay Türü", Tur.SECIM, secenek = listOf("fiyat" to "Ücret onayı", "teknik" to "İşlem onayı")), Alan("aciklama", "Açıklama", Tur.COKSATIR, zorunlu = true), Alan("fiyat", "Fiyat (TL)", Tur.SAYI),
                Alan("kanal", "Gönderim", Tur.SECIM, secenek = listOf("sms" to "SMS", "whatsapp" to "WhatsApp", "mail" to "E-posta", "ikisi" to "SMS ve e-posta")),
                Alan("durum_guncelle", "Durumu \"Müşteri Onayında\" yap", Tur.ONAY))) { mapOf("islem" to "onay") },
        Eylem("Bakım Hatırlatıcısı Ekle", "servis-detay", ikon = "bell", sorgu = sid,
            alanlar = listOf(Alan("bakim_tarihi", "Bakım Tarihi", Tur.TARIH, zorunlu = true), Alan("aciklama", "Açıklama"))) { mapOf("islem" to "hatirlatici") },
        Eylem("Müşteriye Mesaj Gönder", "servis-detay", ikon = "envelope", sorgu = sid, varsayilan = { mapOf("kanal" to "sms") },
            alanlar = listOf(Alan("mesaj", "Mesaj", Tur.COKSATIR, zorunlu = true), Alan("kanal", "Gönderim", Tur.SECIM, secenek = listOf("sms" to "SMS", "mail" to "E-posta", "ikisi" to "SMS ve e-posta")))) { mapOf("islem" to "bildirim") }
    )

    val onay = Modul(
        kod = "onay", baslik = "Onay Talepleri", uc = "onaylar", silSayfa = "",
        satir = { o -> Satir(o.metin("musteri").ifEmpty { "#" + o.metin("kayit_no") }, birles(o.metin("tur_ad"), o.metin("fiyat_yazi"), "#" + o.metin("kayit_no")), birles(o.metin("marka"), o.metin("model"), o.metin("tarih_yazi")), o.metin("durum_ad"), o.optInt("durum") == 2) },
        detay = { o -> listOf("Müşteri" to o.metin("musteri"), "Servis no" to o.metin("kayit_no"), "Cihaz" to birles(o.metin("marka"), o.metin("model"), o.metin("cihaz")), "Tür" to o.metin("tur_ad"), "Fiyat" to o.metin("fiyat_yazi"), "Açıklama" to o.metin("aciklama"), "Durum" to o.metin("durum_ad"), "İstek tarihi" to o.metin("tarih_yazi"), "Yanıt tarihi" to o.metin("onay_yazi"), "Müşteri notu" to o.metin("musteri_notu")) },
        eylemler = listOf(
            Eylem("Onaylandı olarak işaretle", "servis-onaylar", { it.optInt("durum") == 0 }) { mapOf("islem" to "manuel", "id" to kimlik(it), "durum" to "1") },
            Eylem("Reddedildi olarak işaretle", "servis-onaylar", { it.optInt("durum") == 0 }) { mapOf("islem" to "manuel", "id" to kimlik(it), "durum" to "2") },
            Eylem("Servis kaydını aç", "servis-onaylar", git = { o -> Triple(servis, mapOf("q" to o.metin("kayit_no")), "Servis #" + o.metin("kayit_no")) })
        )
    )

    val bakim = Modul(
        kod = "bakim", baslik = "Bakım Hatırlatmaları", uc = "bakimlar", sorgu = { mapOf("f" to "tumu") }, silSayfa = "servis-hatirlatici",
        satir = { h -> Satir(h.metin("musteri").ifEmpty { "#" + h.metin("kayit_no") }, birles(h.metin("tarih_yazi"), h.metin("aciklama")), birles(h.metin("marka"), h.metin("model"), "#" + h.metin("kayit_no")), h.metin("durum_ad"), h.optBoolean("gecikmis")) },
        detay = { h -> listOf("Müşteri" to h.metin("musteri"), "Telefon" to h.metin("tel"), "Servis no" to h.metin("kayit_no"), "Cihaz" to birles(h.metin("marka"), h.metin("model"), h.metin("cihaz")), "Bakım tarihi" to h.metin("tarih_yazi"), "Açıklama" to h.metin("aciklama"), "Durum" to h.metin("durum_ad")) },
        form = FormTanim("Bakım Hatırlatıcısı", "servis-hatirlatici", mapOf("islem" to "ekle"), listOf(Alan("servis", "Servis Kayıt No", Tur.SAYI, zorunlu = true), Alan("bakim_tarihi", "Bakım Tarihi", Tur.TARIH, zorunlu = true), Alan("aciklama", "Açıklama"))),
        eylemler = listOf(
            Eylem("Tamamlandı", "servis-hatirlatici", { it.optInt("durum") == 0 }) { mapOf("islem" to "tamam", "id" to kimlik(it), "durum" to "1") },
            Eylem("Tamamlanmadı olarak geri al", "servis-hatirlatici", { it.optInt("durum") == 1 }) { mapOf("islem" to "tamam", "id" to kimlik(it), "durum" to "") },
            Eylem("Müşteriye SMS gönder", "servis-hatirlatici", { it.metin("tel").isNotEmpty() }) { mapOf("islem" to "sms", "id" to kimlik(it)) }
        )
    )

    private fun basit(kod: String, baslik: String, tekil: String, sayfa: String) = Modul(
        kod = kod, baslik = baslik, uc = "basit", sorgu = { mapOf("t" to sayfa) }, silSayfa = sayfa,
        satir = { r -> Satir(r.metin("ad")) }, detay = { r -> listOf(tekil to r.metin("ad")) },
        form = FormTanim(tekil, sayfa, mapOf("islem" to "kaydet"), listOf(Alan("ad", tekil + " Adı", zorunlu = true))),
        eylemler = listOf(Eylem("Adını değiştir", sayfa, varsayilan = { mapOf("ad" to it.metin("ad")) }, alanlar = listOf(Alan("ad", tekil + " Adı", zorunlu = true))) { mapOf("islem" to "kaydet", "id" to kimlik(it)) })
    )

    val aksesuar = basit("aksesuar", "Aksesuarlar", "Aksesuar", "aksesuarlar")
    val islemSonucu = basit("sonuc", "İşlem Sonuçları", "İşlem Sonucu", "onarim-sonuclari")
    val cihazTuru = basit("cihaz", "Cihaz Türleri", "Cihaz Türü", "cihaz-turleri")
    val marka = basit("marka", "Markalar", "Marka", "markalar")

    val isOrtagi = Modul(
        kod = "io", baslik = "İş Ortakları", uc = "is_ortaklari", kayitTur = "is_ortagi", silSayfa = "is-ortaklari",
        satir = { f -> Satir(f.metin("unvan"), birles(f.metin("yetkili"), f.metin("tel")), "${f.optInt("acik")} açık · ${f.optInt("kayit")} toplam kayıt", f.metin("bakiye_yazi").let { b -> if (f.optBoolean("borclu")) b else if (f.optInt("durum", 1) == 0) "Pasif" else "" }, f.optBoolean("borclu") || f.optInt("durum", 1) == 0) },
        detay = { f -> listOf("Firma" to f.metin("unvan"), "Yetkili" to f.metin("yetkili"), "Telefon" to f.metin("tel"), "E-posta" to f.metin("email"), "Açık kayıt" to f.optInt("acik").toString(), "Toplam kayıt" to f.optInt("kayit").toString(), "Cari bakiye" to f.metin("bakiye_yazi"), "Vade (gün)" to f.metin("vade_gun"), "Onarım garantisi (gün)" to f.metin("garanti_gun"), "Notlar" to f.metin("notlar")) },
        form = FormTanim(
            "İş Ortağı", "is-ortagi-ekle", emptyMap(),
            listOf(
                Alan("unvan", "Firma Unvanı", zorunlu = true), Alan("yetkili", "Yetkili Kişi", zorunlu = true), Alan("tel", "Cep Telefonu (05xx xxx xx xx)", Tur.TELEFON, zorunlu = true),
                Alan("tel2", "Sabit Telefon", Tur.TELEFON), Alan("email", "E-posta"), Alan("vergi_dairesi", "Vergi Dairesi"), Alan("vergi_no", "Vergi / TC No", Tur.SAYI), Alan("adres", "Adres", Tur.COKSATIR),
                Alan("iscilik_iskonto", "İşçilik İskontosu (%)", Tur.SAYI, varsayilan = "0"), Alan("parca_fiyat", "Parça Fiyatı", Tur.SECIM, varsayilan = "bayi", kaynak = "io_parca_fiyat"),
                Alan("parca_kar", "Parça Kâr Oranı (%, alış + kâr seçiliyse)", Tur.SAYI, varsayilan = "0"), Alan("vade_gun", "Ödeme Vadesi (gün)", Tur.SAYI, varsayilan = "30"),
                Alan("garanti_gun", "Onarım Garantisi (gün)", Tur.SAYI, varsayilan = "90"), Alan("durum", "Durum", Tur.SECIM, varsayilan = "1", secenek = aktifPasif), Alan("notlar", "Notlar", Tur.COKSATIR)
            ),
            duzenle = "is-ortagi-duzenle"
        ),
        eylemler = listOf(
            Eylem("Servis kayıtlarını göster", "is-ortaklari", git = { f -> Triple(disServis, mapOf("io" to kimlik(f)), f.metin("unvan")) }),
            Eylem("Tahsilat al", "is-ortagi-detay", { it.optBoolean("borclu") }, sorgu = sid, alanlar = listOf(Alan("tutar", "Tutar (TL)", Tur.SAYI, zorunlu = true), Alan("aciklama", "Açıklama"), Alan("tarih", "Tarih", Tur.TARIH, varsayilan = "bugun"))) { mapOf("islem" to "tahsilat") },
            Eylem("Pasif yap", "is-ortaklari", { it.optInt("durum", 1) == 1 }) { mapOf("islem" to "durum", "id" to kimlik(it), "durum" to "") },
            Eylem("Aktif yap", "is-ortaklari", { it.optInt("durum", 1) == 0 }) { mapOf("islem" to "durum", "id" to kimlik(it), "durum" to "1") }
        )
    )

    private val yazdir = listOf("1" to "Yazdırılsın", "0" to "Yazdırılmasın")
    private val evetHayir = listOf("1" to "Evet", "0" to "Hayır")

    /** Servis Ayarları (web panelindeki aynı ayarlar) */
    val servisAyarlari = FormTanim(
        "Servis Ayarları", "servis-ayarlar", emptyMap(),
        listOf(
            Alan("servis_kosullari", "Teknik Servis Şartları (servis formunda yazdırılır)", Tur.COKSATIR), Alan("seri_no_buyuk_harf", "Barkod Ön Eki (ör. SP)"),
            Alan("inceleme_ucreti_varsayilan", "Varsayılan İnceleme Ücreti (TL)", Tur.SAYI), Alan("gecikme_gun", "Gecikme Süresi (gün)", Tur.SAYI),
            Alan("servis_liste_limit", "Liste Sayfa Başına Kayıt", Tur.SECIM, secenek = listOf(10, 15, 20, 25, 30, 40, 50, 75, 100).map { it.toString() to "$it adet göster" }),
            Alan("aksesuar_gosterim", "Aksesuar Girişi", Tur.SECIM, secenek = listOf("secmeli" to "Seçmeli (liste)", "yazmali" to "Yazmalı (serbest metin)")),
            Alan("fiyat_bilgi_goster", "Fiyat Bilgi Alanı", Tur.SECIM, secenek = listOf("1" to "Evet, göster", "0" to "Hayır, gizle")), Alan("teslim_fisi_notu", "Teslim Fişi Notu", Tur.COKSATIR),
            Alan("uyari_aktif", "Ekran Uyarıları", Tur.SECIM, secenek = listOf("1" to "Açık", "0" to "Kapalı")), Alan("uyari_hazir_gun", "Teslim Alınmayan Cihaz Uyarısı (gün)", Tur.SAYI),
            Alan("uyari_bekleme_gun", "Bekleme Süresi Dolan Cihaz Uyarısı (gün)", Tur.SAYI), Alan("stok_kritik", "Kritik Stok Seviyesi (adet)", Tur.SAYI),
            Alan("etiket_firma_bilgisi", "Etiket: Firma Bilgisi"), Alan("etiket_firma_tel", "Etiket: Firma Telefonu"), Alan("etiket_yazi_boyutu", "Etiket: Yazı Boyutu (px)", Tur.SAYI),
            Alan("etiket_kalin_yazi", "Etiket: Yazı Kalın", Tur.SECIM, secenek = evetHayir), Alan("etiket_ust_bosluk", "Etiket: Üst Kenar Boşluğu (px)", Tur.SAYI),
            Alan("yazdir_barkod", "Yazdır: Barkod", Tur.SECIM, secenek = yazdir), Alan("yazdir_musteri", "Yazdır: Müşteri Adı", Tur.SECIM, secenek = yazdir), Alan("yazdir_tel", "Yazdır: Müşteri Telefonu", Tur.SECIM, secenek = yazdir),
            Alan("fis_bayi_goster", "Yazdır: Bayi / Firma Adı", Tur.SECIM, secenek = yazdir), Alan("yazdir_kayit_tarihi", "Yazdır: Kabul Tarihi", Tur.SECIM, secenek = yazdir),
            Alan("yazdir_markamodel", "Yazdır: Marka / Model", Tur.SECIM, secenek = yazdir), Alan("yazdir_serino", "Yazdır: Cihaz Seri No", Tur.SECIM, secenek = yazdir), Alan("yazdir_ariza", "Yazdır: Arıza", Tur.SECIM, secenek = yazdir),
            Alan("yazdir_logo", "Servis Formu Logo", Tur.SECIM, secenek = listOf("1" to "Görünsün", "0" to "Görünmesin")), Alan("yazdir_islem_konum", "Yapılan İşlemler Konumu", Tur.SECIM, secenek = listOf("orta" to "Ortada", "alt" to "Altta")),
            Alan("yazdir_ucretler", "Teslim Fişinde Ücretler", Tur.SECIM, secenek = yazdir), Alan("yazdir_adres", "Servis Formunda Adres", Tur.SECIM, secenek = yazdir)
        ),
        sarmala = "a"
    )

    val musteri = Modul(
        kod = "musteri", baslik = "Müşteriler", uc = "musteriler", kayitTur = "musteri", silSayfa = "musteri-listesi",
        satir = { m -> Satir(m.metin("adsoyad"), birles(m.metin("tel"), m.metin("firma")), m.metin("email"), if (m.optInt("cihaz") > 0) "${m.optInt("cihaz")} cihaz" else "", m.optInt("durum", 1) == 0) },
        detay = { m -> listOf("Ad Soyad" to m.metin("adsoyad"), "Firma" to m.metin("firma"), "Telefon" to m.metin("tel"), "E-posta" to m.metin("email"), "Servis kaydı" to m.optInt("cihaz").toString(), "Durum" to if (m.optInt("durum", 1) == 1) "Aktif" else "Pasif") },
        form = FormTanim(
            "Müşteri", "musteri-ekle", emptyMap(),
            listOf(
                Alan("adsoyad", "Adı Soyadı", zorunlu = true), Alan("tel", "Cep Telefonu (05xx xxx xx xx)", Tur.TELEFON, zorunlu = true),
                Alan("firma", "Firma Adı"), Alan("email", "E-posta"), Alan("tc", "TC Kimlik No", Tur.SAYI),
                Alan("adres", "Adres", Tur.COKSATIR), Alan("notlar", "Notlar", Tur.COKSATIR),
                Alan("durum", "Durum", Tur.SECIM, varsayilan = "1", secenek = aktifPasif),
                Alan("bildir", "Müşteriye bilgilendirme mesajı gönder", Tur.ONAY, varsayilan = "1")
            ),
            duzenle = "musteri-duzenle"
        )
    )

    val stok = Modul(
        kod = "parca", baslik = "Stok / Parçalar", uc = "parcalar", kayitTur = "parca", silSayfa = "parca-listesi",
        satir = { p -> Satir(p.metin("adi"), birles("Satış: " + p.metin("satis_tutari_yazi"), p.metin("raf_konumu")), p.metin("firma"), "${p.optInt("adet")} adet", p.optBoolean("az")) },
        detay = { p -> listOf("Parça" to p.metin("adi"), "Stok adedi" to p.optInt("adet").toString(), "Alış fiyatı" to p.metin("alis_tutari_yazi"), "Satış fiyatı" to p.metin("satis_tutari_yazi"), "Bayi fiyatı" to p.metin("bayi_tutari_yazi"), "Raf / Konum" to p.metin("raf_konumu"), "Tedarikçi" to p.metin("firma")) },
        form = FormTanim(
            "Parça", "parca-ekle", mapOf("para_birimi" to "TL", "kritik_stok" to "1"),
            listOf(
                Alan("adi", "Parça Adı", zorunlu = true), Alan("adet", "Stok Adedi", Tur.SAYI, varsayilan = "1"),
                Alan("alis_tutari", "Alış Fiyatı (TL)", Tur.SAYI), Alan("satis_tutari", "Satış Fiyatı (TL)", Tur.SAYI), Alan("bayi_tutari", "Bayi Fiyatı (TL)", Tur.SAYI),
                Alan("raf_konumu", "Raf / Konum"), Alan("firma", "Tedarikçi Firma"), Alan("fatura_no", "Fatura No"), Alan("fatura_tarihi", "Fatura Tarihi", Tur.TARIH)
            ),
            duzenle = "parca-duzenle"
        )
    )

    private val htEylem = listOf(
        Eylem("Tamamlandı olarak işaretle", "hatirlaticilar", { it.optInt("odeme") == 0 && it.metin("durum") == "aktif" }) { mapOf("islem" to "tamamla", "id" to kimlik(it)) },
        Eylem("Ödendi", "hatirlaticilar", { it.optInt("odeme") == 1 && it.metin("durum") == "aktif" }) { mapOf("islem" to "tamamla", "id" to kimlik(it)) },
        Eylem("Ödendi ve gidere işle", "hatirlaticilar", { it.optInt("odeme") == 1 && it.metin("durum") == "aktif" }) { mapOf("islem" to "tamamla", "id" to kimlik(it), "gider" to "1") }
    )

    val hatirlatici = Modul(
        kod = "hatirlatici", baslik = "Hatırlatıcılar", uc = "hatirlaticilar", kayitTur = "hatirlatici", silSayfa = "hatirlaticilar", belge = true,
        satir = { h -> Satir(h.metin("baslik"), birles(h.metin("tarih_yazi"), h.metin("tekrar_ad")), birles(h.metin("kategori_ad"), h.metin("kisi")), h.metin("tutar_yazi"), h.optBoolean("gecikmis") || h.optBoolean("bugun")) },
        detay = { h -> listOf("Başlık" to h.metin("baslik"), "Kategori" to h.metin("kategori_ad"), "Tarih" to h.metin("tarih_yazi"), "Tekrar" to h.metin("tekrar_ad"), "Tutar" to h.metin("tutar_yazi"), "Kişi / Firma" to h.metin("kisi"), "Telefon" to h.metin("tel"), "Açıklama" to h.metin("aciklama")) },
        form = FormTanim(
            "Hatırlatıcı", "hatirlatici-ekle",
            mapOf("islem" to "kaydet", "kanal" to listOf("panel"), "hatirlat" to listOf("1", "0"), "durum" to "aktif", "oncelik" to "orta", "para_birimi" to "TL"),
            listOf(
                Alan("baslik", "Başlık", zorunlu = true), Alan("kategori", "Kategori", Tur.SECIM, varsayilan = "fatura", kaynak = "ht_kategori"),
                Alan("baslangic", "Tarih", Tur.TARIH, zorunlu = true, varsayilan = "bugun"), Alan("saat", "Saat (ör. 09:00)", Tur.SAAT, varsayilan = "09:00"),
                Alan("tekrar", "Tekrar", Tur.SECIM, varsayilan = "yok", kaynak = "ht_tekrar"), Alan("tutar", "Tutar (TL)", Tur.SAYI),
                Alan("odeme", "Bu bir ödeme kaydı (kira, fatura, taksit…)", Tur.ONAY), Alan("kisi", "Kişi / Firma"), Alan("tel", "Telefon", Tur.TELEFON),
                Alan("aciklama", "Açıklama", Tur.COKSATIR)
            ),
            duzenle = "hatirlatici-duzenle"
        ),
        eylemler = htEylem
    )

    /** Ödeme Takibi: hatırlatıcıların yalnızca ödeme olanları (kira, fatura, taksit…) */
    val odeme = Modul(
        kod = "odeme", baslik = "Ödeme Takibi", uc = "hatirlaticilar", sorgu = { mapOf("odeme" to "1") }, kayitTur = "hatirlatici", silSayfa = "hatirlaticilar", belge = true,
        satir = hatirlatici.satir, detay = hatirlatici.detay, eylemler = htEylem,
        form = FormTanim(
            "Ödeme", "hatirlatici-ekle",
            mapOf("islem" to "kaydet", "kanal" to listOf("panel"), "hatirlat" to listOf("3", "1", "0"), "durum" to "aktif", "oncelik" to "orta", "para_birimi" to "TL", "odeme" to "1"),
            listOf(
                Alan("baslik", "Ödeme Adı (ör. Dükkan kirası)", zorunlu = true), Alan("kategori", "Kategori", Tur.SECIM, varsayilan = "kira", kaynak = "ht_kategori"),
                Alan("tutar", "Tutar (TL)", Tur.SAYI), Alan("baslangic", "Ödeme Tarihi", Tur.TARIH, zorunlu = true, varsayilan = "bugun"),
                Alan("tekrar", "Tekrar", Tur.SECIM, varsayilan = "ay", kaynak = "ht_tekrar"), Alan("kisi", "Kime Ödenecek"), Alan("tel", "Telefon", Tur.TELEFON),
                Alan("aciklama", "Açıklama", Tur.COKSATIR)
            ),
            duzenle = "hatirlatici-duzenle"
        )
    )

    val alacak = Modul(
        kod = "alacak", baslik = "Alacak / Verecek", uc = "alacak_verecek", kayitTur = "alacak", silSayfa = "alacak-verecek", belge = true,
        satir = { a -> Satir(a.metin("kisi"), birles(a.metin("yon_ad"), a.metin("tur_ad"), if (a.metin("vade_yazi").isEmpty()) "" else "Vade: " + a.metin("vade_yazi")), birles(a.metin("durum_ad"), a.metin("aciklama")), a.metin("kalan_yazi"), a.optBoolean("gecikmis")) },
        detay = { a -> listOf("Kişi / Firma" to a.metin("kisi"), "Yön" to a.metin("yon_ad"), "Tür" to a.metin("tur_ad"), "Tutar" to a.metin("tutar_yazi"), "Kalan" to a.metin("kalan_yazi"), "Vade" to a.metin("vade_yazi"), "Durum" to a.metin("durum_ad"), "Telefon" to a.metin("tel"), "Açıklama" to a.metin("aciklama"), "Notlar" to a.metin("notlar")) },
        form = FormTanim(
            "Alacak / Verecek", "alacak-verecek", mapOf("islem" to "kaydet", "kisi_tipi" to "musteri", "hatirlat" to listOf("3", "0")),
            listOf(
                Alan("tur", "Tür", Tur.SECIM, varsayilan = "musteri_tahsilati", kaynak = "av_tur"), Alan("kisi", "Kişi / Firma", zorunlu = true),
                Alan("tutar", "Tutar (TL)", Tur.SAYI, zorunlu = true), Alan("vade", "Vade Tarihi", Tur.TARIH), Alan("tel", "Telefon", Tur.TELEFON),
                Alan("aciklama", "Açıklama"), Alan("notlar", "Notlar", Tur.COKSATIR)
            ),
            duzenle = "alacak-verecek"
        ),
        eylemler = listOf(
            Eylem("Kısmi ödeme / tahsilat gir", "alacak-verecek", { it.metin("durum") == "bekliyor" || it.metin("durum") == "kismi" }, alanlar = listOf(Alan("tutar", "Ödenen Tutar (TL)", Tur.SAYI, zorunlu = true))) { mapOf("islem" to "odeme", "id" to kimlik(it)) },
            Eylem("Kalanın tamamı ödendi", "alacak-verecek", { it.metin("durum") == "bekliyor" || it.metin("durum") == "kismi" }) { mapOf("islem" to "odeme", "id" to kimlik(it), "tutar" to it.metin("kalan")) }
        )
    )

    /** Bir kredinin taksitleri (Kredi Ödemeleri içinden açılır) */
    val taksit = Modul(
        kod = "taksit", baslik = "Taksitler", uc = "kredi_taksitler", aramali = false,
        satir = { t -> Satir("${t.optInt("sira")}. taksit", "Vade: " + t.metin("vade_yazi"), t.metin("odendi_yazi"), t.metin("tutar_yazi"), t.optBoolean("gecikmis")) },
        detay = { t -> listOf("Taksit" to "${t.optInt("sira")}.", "Vade" to t.metin("vade_yazi"), "Tutar" to t.metin("tutar_yazi"), "Durum" to t.metin("odendi_yazi")) },
        eylemler = listOf(
            Eylem("Ödendi", "kredi-takibi", { !it.optBoolean("odenmis") }) { mapOf("islem" to "taksit_ode", "id" to kimlik(it)) },
            Eylem("Ödendi ve gidere işle", "kredi-takibi", { !it.optBoolean("odenmis") }) { mapOf("islem" to "taksit_ode", "id" to kimlik(it), "finans" to "1") },
            Eylem("Ödemeyi geri al", "kredi-takibi", { it.optBoolean("odenmis") }) { mapOf("islem" to "taksit_geri", "id" to kimlik(it)) }
        )
    )

    val kredi = Modul(
        kod = "kredi", baslik = "Kredi Ödemeleri", uc = "krediler", kayitTur = "kredi", silSayfa = "kredi-takibi", belge = true,
        satir = { k -> Satir(birles(k.metin("banka"), k.metin("kredi_turu")), birles("Taksit: " + k.metin("taksit_yazi"), if (k.metin("sonraki_yazi").isEmpty()) "" else "Sıradaki: " + k.metin("sonraki_yazi")), "Kalan ${k.optInt("kalan_taksit")} / ${k.optInt("taksit_sayisi")} taksit", k.metin("kalan_yazi"), k.optBoolean("gecikmis")) },
        detay = { k -> listOf("Banka" to k.metin("banka"), "Kredi türü" to k.metin("kredi_turu"), "Kredi no" to k.metin("kredi_no"), "Kredi tutarı" to k.metin("tutar_yazi"), "Aylık taksit" to k.metin("taksit_yazi"), "Kalan taksit" to "${k.optInt("kalan_taksit")} / ${k.optInt("taksit_sayisi")}", "Kalan borç" to k.metin("kalan_yazi"), "Sıradaki taksit" to k.metin("sonraki_yazi"), "Durum" to k.metin("durum_ad"), "Notlar" to k.metin("notlar")) },
        form = FormTanim(
            "Kredi", "kredi-takibi", mapOf("islem" to "kaydet", "hatirlat" to listOf("3", "0")),
            listOf(
                Alan("banka", "Banka", zorunlu = true), Alan("kredi_turu", "Kredi Türü (ör. İhtiyaç, Taşıt)"), Alan("tutar", "Kredi Tutarı (TL)", Tur.SAYI, zorunlu = true),
                Alan("taksit_sayisi", "Taksit Sayısı", Tur.SAYI, zorunlu = true, varsayilan = "12"), Alan("faiz_orani", "Aylık Faiz Oranı (%)", Tur.SAYI),
                Alan("aylik_taksit_elle", "Aylık Taksit (biliyorsanız)", Tur.SAYI), Alan("baslangic", "İlk Taksit Tarihi", Tur.TARIH, zorunlu = true, varsayilan = "bugun"),
                Alan("notlar", "Notlar", Tur.COKSATIR)
            ),
            duzenle = "kredi-takibi"
        ),
        eylemler = listOf(
            Eylem("Taksitleri göster / tek tek öde", "kredi-takibi", git = { k -> Triple(taksit, mapOf("id" to kimlik(k)), birles(k.metin("banka"), "Taksitler")) }),
            Eylem("Sıradaki taksit ödendi", "kredi-takibi", { it.metin("sonraki_taksit_id").isNotEmpty() }) { mapOf("islem" to "taksit_ode", "id" to it.metin("sonraki_taksit_id")) }
        )
    )

    val garanti = Modul(
        kod = "garanti", baslik = "Garanti Takibi", uc = "garantiler", kayitTur = "garanti", silSayfa = "garanti-takibi", belge = true,
        satir = { g -> Satir(g.metin("urun_adi"), birles(g.metin("marka"), g.metin("model"), g.metin("musteri")), "Bitiş: " + g.metin("bitis_yazi"), g.metin("kalan_yazi"), g.optBoolean("bitti") || g.optBoolean("yakin")) },
        detay = { g -> listOf("Ürün" to g.metin("urun_adi"), "Marka" to g.metin("marka"), "Model" to g.metin("model"), "Seri no" to g.metin("seri_no"), "Müşteri" to g.metin("musteri"), "Telefon" to g.metin("tel"), "Garanti türü" to g.metin("tur_ad"), "Başlangıç" to g.metin("baslangic_yazi"), "Bitiş" to g.metin("bitis_yazi"), "Kalan" to g.metin("kalan_yazi"), "Sağlayıcı" to g.metin("saglayici"), "Notlar" to g.metin("notlar")) },
        form = FormTanim(
            "Garanti Kaydı", "garanti-ekle", mapOf("islem" to "kaydet", "hatirlat_gun" to "30", "durum" to "aktif"),
            listOf(
                Alan("urun_adi", "Ürün Adı", zorunlu = true), Alan("marka", "Marka"), Alan("model", "Model"), Alan("seri_no", "Seri No"),
                Alan("musteri_ad", "Müşteri Adı"), Alan("tel", "Telefon", Tur.TELEFON),
                Alan("baslangic", "Garanti Başlangıcı", Tur.TARIH, zorunlu = true, varsayilan = "bugun"), Alan("sure_ay", "Süre (ay)", Tur.SAYI, varsayilan = "24"),
                Alan("garanti_turu", "Garanti Türü", Tur.SECIM, varsayilan = "resmi", kaynak = "garanti_tur"), Alan("saglayici", "Garanti Sağlayıcı"),
                Alan("musteri_bildir", "Bitmeden önce müşteriye haber ver", Tur.ONAY), Alan("notlar", "Notlar", Tur.COKSATIR)
            ),
            duzenle = "garanti-duzenle"
        )
    )

    /** Gelir / Gider: dükkan giderleri (kira, fatura, parça alımı…) ve gelirler */
    val gelirGider = Modul(
        kod = "muhasebe", baslik = "Gelir / Gider", uc = "gelir_gider", kayitTur = "gelir_gider", silSayfa = "gelir-gider", belge = true,
        satir = { g -> Satir(g.metin("aciklama").ifEmpty { g.metin("tur_ad") }, birles(g.metin("tarih_yazi"), g.metin("musteri")), g.metin("tur_ad"), g.metin("tutar_yazi"), g.metin("tur") == "gider") },
        detay = { g -> listOf("Tür" to g.metin("tur_ad"), "Tutar" to g.metin("tutar_yazi"), "Tarih" to g.metin("tarih_yazi"), "Müşteri" to g.metin("musteri"), "Açıklama" to g.metin("aciklama")) },
        form = FormTanim(
            "Gelir / Gider", "gelir-gider-ekle", emptyMap(),
            listOf(
                Alan("tur", "İşlem Türü", Tur.SECIM, varsayilan = "gider", secenek = listOf("gider" to "Gider (kira, fatura, parça alımı…)", "gelir" to "Gelir")),
                Alan("tutar", "Tutar (TL)", Tur.SAYI, zorunlu = true), Alan("tarih", "Tarih", Tur.TARIH, varsayilan = "bugun"),
                Alan("aciklama", "Açıklama (ör. Ekim ayı kirası)", Tur.COKSATIR)
            ),
            duzenle = "gelir-gider-ekle"
        )
    )

    val cek = Modul(
        kod = "cek", baslik = "Çek / Senet", uc = "cek_senet", kayitTur = "cek", silSayfa = "cek-senet", belge = true,
        satir = { c -> Satir(c.metin("taraf"), birles(c.metin("tur_ad"), "Vade: " + c.metin("vade_yazi")), birles(c.metin("evrak_no"), c.metin("banka"), c.metin("durum_ad")), c.metin("tutar_yazi"), c.optBoolean("gecikmis")) },
        detay = { c -> listOf("Taraf" to c.metin("taraf"), "Tür" to c.metin("tur_ad"), "Evrak no" to c.metin("evrak_no"), "Vade" to c.metin("vade_yazi"), "Tutar" to c.metin("tutar_yazi"), "Durum" to c.metin("durum_ad"), "Banka" to c.metin("banka"), "Seri no" to c.metin("seri_no"), "Telefon" to c.metin("tel"), "Notlar" to c.metin("notlar")) },
        form = FormTanim(
            "Çek / Senet", "cek-senet", mapOf("islem" to "kaydet", "durum" to "portfoy", "hatirlat" to listOf("3", "0")),
            listOf(
                Alan("tur", "Tür", Tur.SECIM, varsayilan = "cek", secenek = listOf("cek" to "Çek", "senet" to "Senet")),
                Alan("yon", "Yön", Tur.SECIM, varsayilan = "alinan", secenek = listOf("alinan" to "Alınan (tahsil edilecek)", "verilen" to "Verilen (ödenecek)")),
                Alan("taraf", "Taraf (kişi / firma)", zorunlu = true), Alan("vade", "Vade Tarihi", Tur.TARIH, zorunlu = true),
                Alan("tutar", "Tutar (TL)", Tur.SAYI, zorunlu = true), Alan("banka", "Banka"), Alan("seri_no", "Seri No"),
                Alan("tel", "Telefon", Tur.TELEFON), Alan("notlar", "Notlar", Tur.COKSATIR)
            ),
            duzenle = "cek-senet"
        ),
        eylemler = listOf(
            Eylem("Tahsil edildi (gelire işle)", "cek-senet", { it.metin("yon") == "alinan" && (it.metin("durum") == "portfoy" || it.metin("durum") == "bankada") }) { mapOf("islem" to "durum", "id" to kimlik(it), "durum" to "tahsil", "finans" to "1") },
            Eylem("Ödendi (gidere işle)", "cek-senet", { it.metin("yon") == "verilen" && (it.metin("durum") == "portfoy" || it.metin("durum") == "bankada") }) { mapOf("islem" to "durum", "id" to kimlik(it), "durum" to "odendi", "finans" to "1") },
            Eylem("Bankaya verildi", "cek-senet", { it.metin("durum") == "portfoy" }) { mapOf("islem" to "durum", "id" to kimlik(it), "durum" to "bankada") },
            Eylem("Karşılıksız / protestolu", "cek-senet", { it.metin("durum") == "portfoy" || it.metin("durum") == "bankada" }) { mapOf("islem" to "durum", "id" to kimlik(it), "durum" to "karsiliksiz") }
        )
    )

    private val randevuDurum = mapOf("onayli" to "Onaylı", "talep" to "Onay bekliyor", "tamamlandi" to "Tamamlandı", "iptal" to "İptal", "red" to "Reddedildi")

    val randevu = Modul(
        kod = "takvim", baslik = "Randevular", uc = "sayfa", anahtar = "etkinlikler", kayitTur = "randevu", silSayfa = "takvim",
        sorgu = { mapOf("s" to "takvim", "veri" to "1", "bas" to bugun(), "bit" to LocalDate.now().plusDays(60).toString()) },
        satir = { r -> Satir(r.metin("baslik"), birles(trTarih(r.metin("gun")), birles(r.metin("saat"), r.metin("bitis")).replace(" · ", " – ")), birles(r.metin("kisi"), r.metin("tel")), if (r.metin("tur") == "not") "Not" else (randevuDurum[r.metin("durum")] ?: ""), r.metin("durum") == "talep") },
        detay = { r -> listOf("Başlık" to r.metin("baslik"), "Tarih" to trTarih(r.metin("gun")), "Saat" to birles(r.metin("saat"), r.metin("bitis")).replace(" · ", " – "), "Kişi" to r.metin("kisi"), "Telefon" to r.metin("tel"), "Sorumlu" to r.metin("sorumlu"), "Durum" to (randevuDurum[r.metin("durum")] ?: ""), "Açıklama" to r.metin("aciklama")) },
        form = FormTanim(
            "Randevu", "takvim", mapOf("islem" to "kaydet", "tur" to "randevu", "hatirlat" to listOf("60")),
            listOf(
                Alan("baslik", "Randevu Başlığı", zorunlu = true), Alan("tarih", "Tarih", Tur.TARIH, zorunlu = true, varsayilan = "bugun"),
                Alan("saat_bas", "Başlangıç Saati (ör. 14:30)", Tur.SAAT, varsayilan = "09:00"), Alan("saat_bit", "Bitiş Saati", Tur.SAAT),
                Alan("ad_soyad", "Ad Soyad"), Alan("tel", "Telefon", Tur.TELEFON), Alan("aciklama", "Açıklama", Tur.COKSATIR)
            ),
            duzenle = "takvim"
        ),
        eylemler = listOf(
            Eylem("Randevuyu onayla", "takvim", { it.metin("durum") == "talep" }) { mapOf("islem" to "durum", "id" to kimlik(it), "durum" to "onayli") },
            Eylem("Tamamlandı", "takvim", { it.metin("durum") == "onayli" && it.metin("tur") != "not" }) { mapOf("islem" to "durum", "id" to kimlik(it), "durum" to "tamamlandi") },
            Eylem("İptal et", "takvim", { it.metin("durum") == "onayli" || it.metin("durum") == "talep" }) { mapOf("islem" to "durum", "id" to kimlik(it), "durum" to "iptal") }
        )
    )

    /** Personel: programı kullanan kişiler ve yetkileri */
    val personel = Modul(
        kod = "teknisyen", baslik = "Personel", uc = "kullanicilar", kayitTur = "kullanici", silSayfa = "teknisyen-listesi",
        satir = { u -> Satir(u.metin("adsoyad"), birles(u.metin("email"), u.metin("tel")), u.metin("yetki_yazi"), if (u.optInt("durum", 1) == 0) "Pasif" else u.metin("rol_ad"), u.optInt("durum", 1) == 0) },
        detay = { u -> listOf("Ad Soyad" to u.metin("adsoyad"), "E-posta" to u.metin("email"), "Telefon" to u.metin("tel"), "Rol" to u.metin("rol_ad"), "Yetkiler" to u.metin("yetki_yazi"), "Kayıt görünürlüğü" to u.metin("kayit_yazi"), "Durum" to if (u.optInt("durum", 1) == 1) "Aktif" else "Pasif", "Son giriş" to u.metin("son_giris")) },
        form = FormTanim(
            "Personel", "teknisyen-ekle", mapOf("sms_izni" to "1"),
            listOf(
                Alan("adsoyad", "Adı Soyadı", zorunlu = true), Alan("email", "E-posta (giriş adı)", zorunlu = true), Alan("tel", "Telefon", Tur.TELEFON),
                Alan("parola", "Parola (düzenlerken boş bırakılırsa değişmez)", Tur.PAROLA),
                Alan("rol", "Rol", Tur.SECIM, varsayilan = "teknisyen", secenek = listOf("teknisyen" to "Personel (aşağıdaki yetkilerle)", "admin" to "Yönetici (tüm yetkiler)")),
                Alan("yetkiler", "Yetkiler", Tur.COKLU, kaynak = "moduller"),
                Alan("tum_kayitlar", "Kayıt Görünürlüğü", Tur.SECIM, varsayilan = "1", secenek = listOf("1" to "Tüm kayıtları görür", "0" to "Yalnızca kendi kayıtlarını görür")),
                Alan("durum", "Durum", Tur.SECIM, varsayilan = "1", secenek = aktifPasif)
            ),
            duzenle = "teknisyen-duzenle"
        )
    )
}
