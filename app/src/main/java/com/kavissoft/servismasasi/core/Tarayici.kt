package com.kavissoft.servismasasi.core

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Cihaz etiketini okur: barkod / QR kod ve etiketteki yazı. Okuma tümüyle telefonun içinde yapılır; görüntü hiçbir yere
 * gönderilmez ve saklanmaz. Ayrıştırma ve puanlama kuralları web panelindeki "telefonla okut" (tarayici.js) ile aynıdır:
 * okunan değer etiket bağlamına ("Model:", "S/N"…), biçimine, marka desenine ve daha önce kayıtlı modellere göre puanlanır.
 */
object Tarayici {
    /** Okunan kelime: [c] güven (0–100, bilinmiyorsa 0), konum ve satır numarası [s] */
    class Kelime(val t: String, val c: Float, val x0: Float, val y0: Float, val x1: Float, val y1: Float, var s: Int = 0)
    class Aday(val v: String, val p: Double, val neden: List<String>)
    /** Barkod / QR: [f] tür adı (web ile aynı adlar), [kose] görüntüdeki köşe noktaları (x, y) */
    class Kod(val v: String, val f: String, val kose: List<Pair<Float, Float>>)
    /** [yaz]: doğrudan yazılacak ham içerik; boşsa [sor] içindeki adaylardan kullanıcı seçer */
    class KodSonuc(val yaz: String?, val sor: List<Aday>, val secilen: Kod?)
    /** [yazilan]: alanlara yazılanlar (tür → değer); [bitti]: hedef alan doldu; değilse [adaylar] gösterilir */
    class YaziSonuc(val yazilan: List<Pair<String, String>>, val adaylar: List<Aday>, val bitti: Boolean)

    val ADLAR = mapOf("model" to "Model", "seri" to "Seri Numarası", "lisans" to "İşletim Sistemi Lisans Bilgileri")
    val FORMAT_AD = mapOf(
        "qr_code" to "QR kod", "data_matrix" to "Data Matrix", "pdf417" to "PDF417", "aztec" to "Aztec", "code_128" to "Code 128", "code_39" to "Code 39",
        "code_93" to "Code 93", "codabar" to "Codabar", "ean_13" to "EAN-13", "ean_8" to "EAN-8", "itf" to "ITF", "upc_a" to "UPC-A", "upc_e" to "UPC-E"
    )
    const val MODEL_ETIKETI = "“Model” etiketinin yanında"

    private val I = RegexOption.IGNORE_CASE
    private fun harfVar(t: String) = t.any { it in 'A'..'Z' || it in 'a'..'z' }
    private fun rakamVar(t: String) = t.any { it in '0'..'9' }
    private fun sade(x: String) = x.uppercase().replace(Regex("[^A-Z0-9]"), "")

    private suspend fun <T> bekle(gorev: Task<T>): T? = suspendCancellableCoroutine { devam ->
        gorev.addOnSuccessListener { if (devam.isActive) devam.resume(it) }
            .addOnFailureListener { if (devam.isActive) devam.resume(null) }
    }

    // ------------------------------------------------------------ Okunan metni alanlara ayır
    private val R_ANAHTAR = Regex("\\b([A-Z0-9]{5}-){4}[A-Z0-9]{5}\\b", I)
    private val R_ANAHTAR_TAM = Regex("^([A-Z0-9]{5}-){4}[A-Z0-9]{5}$", I)
    private val R_ANAHTAR_BOSLUKLU = Regex("\\b[A-Z0-9]{5}(?:\\s?-\\s?[A-Z0-9]{5}){4}\\b", I)
    private val R_GS_SERI = Regex("\\(21\\)([^(|]+)")
    private val R_GS_MODEL = Regex("\\(24[01]\\)([^(|]+)")
    private val R_SERI = Regex("(?:^|[^a-z])(?:s/?n|seri(?:al)?(?:\\s*(?:no|number|numaras[ıi]))?\\.?|serial\\s*#)\\s*[\\:#=\\-]?\\s*([A-Z0-9][A-Z0-9\\-/]{3,})", I)
    private val R_MODEL = Regex("(?:^|[^a-z])(?:model(?:\\s*(?:no|name|ad[ıi]))?\\.?)\\s*[\\:#=\\-]?\\s*([^\\s\\:].{1,59})", I)
    private val R_MODEL_KES = Regex("\\s+(?:s/?n|serial|seri|product\\s*key|key)\\b", I)
    private val R_LISANS = Regex("(?:product\\s*key|ürün\\s*anahtar[ıi]|lisans(?:\\s*anahtar[ıi])?|key)\\s*[\\:=]\\s*(\\S{8,})", I)

    /** Etiket içeriğinden model / seri / lisans değerlerini çıkarır ("S/N: …", "Model: …", ürün anahtarı, GS1, JSON içerikli QR) */
    fun ayristir(metin: String): Map<String, String> {
        val t = metin.replace('\u001d', '|').replace(Regex("[\\r\\t]+"), "\n").trim()
        val o = LinkedHashMap<String, String>()
        R_ANAHTAR.find(t)?.let { o["lisans"] = it.value.uppercase() }
        R_GS_SERI.find(t)?.let { o["seri"] = it.groupValues[1].trim() }
        R_GS_MODEL.find(t)?.let { o["model"] = it.groupValues[1].trim() }
        for (s in t.split(Regex("\\n|;|\\|"))) {
            if ("seri" !in o) R_SERI.find(s)?.groupValues?.get(1)?.let { if (rakamVar(it)) o["seri"] = it }
            if ("model" !in o) R_MODEL.find(s)?.groupValues?.get(1)?.let { m ->
                val v = m.split(R_MODEL_KES)[0].trim().replace(Regex("\\s{2,}.*$"), "")
                if (v.isNotEmpty()) o["model"] = v
            }
            if ("lisans" !in o) R_LISANS.find(s)?.groupValues?.get(1)?.let { o["lisans"] = it }
        }
        if (t.startsWith("{")) try {
            val j = JSONObject(t)
            for (a in listOf("model", "seri", "sn", "serial", "lisans", "key")) {
                val v = j.optString(a, "")
                if (v.isNotEmpty()) o[if (a == "sn" || a == "serial") "seri" else if (a == "key") "lisans" else a] = v
            }
        } catch (e: Exception) {
        }
        return o
    }

    /** Okunan ham içeriği alanlara dağıtır; özel bir yapı yoksa değerin tamamı hedef alana yazılır */
    fun dagit(ham: String, hedef: String): List<Pair<String, String>> {
        val o = ayristir(ham)
        val y = listOf("model", "seri", "lisans").mapNotNull { a -> o[a]?.let { a to it } }
        return if (y.isEmpty()) listOf(hedef to ham.trim()) else y
    }

    // ------------------------------------------------------------ Akıllı model seçimi
    // etikette model değerinden hemen önce gelen ifadeler
    private val R_MODEL_ET = Regex("^(model|modeli|mod\\.|m/n|mn|type|typ|mtm|name)$", I)
    private val R_MODEL_GUCLU = Regex("^(model|modeli|mod\\.|m/n|mn)$", I)
    private val R_MODEL_EK = Regex("^model(no|name|nr)?$", I)
    // arkasından gelen değer model OLMAYAN etiketler (seri no, parça no, IMEI, güç değerleri…)
    private val R_BASKA_ET = Regex("^(s/?n|sn|serial|seri|ser\\.?|imei\\d?|meid|p/?n|pn|part|sku|ean|upc|gtin|fcc|id|ic|cmiit|kc|ccc|rating|input|output|power|mac|wlan|bt|date|mfg|mfd|batch|lot|made|ver|rev|product|prod|pid|cn|ppid|service|tag|st|code|barcode|in|dc|ac|emc|reg|regulatory|cert)$", I)
    private val R_SNPN = Regex("^(s/n|p/n)[\\:.]?", I)
    private val R_BAGLAC = Regex("^(no|nr|number|numarası|name|adı)$", I)
    // model olamayacak biçimler: voltaj, akım, güç, kapasite, tarih, frekans, barkod…
    private val R_OLAMAZ = listOf(
        Regex("^\\d+([.,]\\d+)?\\s*(v|vac|vdc|a|ma|w|wh|mah|kwh|hz|khz|mhz|ghz|va|°c|c|kg|g|mm|cm|m|in|inch|db|dbm|%|x)$", I),
        Regex("^\\d+([.,]\\d+)?-\\d+([.,]\\d+)?(v|hz|a|w)?$", I), Regex("^\\d+/\\d+\\s*hz$", I), Regex("^~?\\d+v~?$", I),
        Regex("^\\d+(gb|tb|mb|kb)$", I), Regex("^(ddr\\d|lpddr\\d|usb\\d?|wifi\\d?|5g|4g|lte|nfc)$", I),
        Regex("^\\d{1,4}[/.-]\\d{1,2}[/.-]\\d{1,4}$"), Regex("^(19|20)\\d{2}$"), Regex("^\\d{4}[/.-]\\d{1,2}$"), Regex("^\\d{1,2}[/.-]\\d{4}$"), Regex("^[\\d/.,:-]+$"),
        Regex("^([0-9a-f]{2}[\\:-]){3,}[0-9a-f]{2}$", I), Regex("^\\d{8,}$"), Regex("^\\d{1,2}$")
    )
    // bilinen seri / aile adları: arkasından gelen numarayla birlikte modeldir (ör. “VivoBook 15 X515”)
    private val R_AILE = Regex("^(vivobook|zenbook|expertbook|rog|tuf|strix|zephyrus|ideapad|thinkpad|thinkbook|legion|yoga|inspiron|latitude|vostro|xps|precision|alienware|pavilion|elitebook|probook|envy|omen|victus|spectre|zbook|aspire|nitro|predator|swift|spin|extensa|travelmate|galaxy|redmi|poco|mi|iphone|ipad|macbook|imac|surface|matebook|mateview|monster|abra|tulpar|casper|excalibur|nirvana|playstation|ps\\d|xbox|switch|note|tab|book|air|pro)$", I)
    private val R_MARKA = Regex("^(hp|dell|asus|acer|lenovo|samsung|apple|huawei|xiaomi|msi|lg|sony|toshiba|casper|monster|honor|oppo|realme|vivo|nokia|motorola|microsoft|google|fujitsu|gigabyte|razer|philips|vestel|arçelik|beko|canon|epson|brother|tp-link|tplink|zyxel|jbl|nintendo)$", I)
    private val R_URUN_SON = Regex("^(notebook|pc|laptop|computer|series|tablet|phone|smartphone|inc|corp)$", I)
    private val MARKA_DESEN: Map<String, List<Regex>> = mapOf(
        "samsung" to listOf(Regex("^SM-[A-Z]\\d{3,4}[A-Z0-9/]*$", I), Regex("^NP\\d{3}[A-Z0-9]+$", I), Regex("^[A-Z]{2}\\d{2}[A-Z]{1,2}\\d{3,4}[A-Z0-9]*$", I)),
        "apple" to listOf(Regex("^A\\d{4}$", I), Regex("^M[A-Z0-9]{3,4}\\d?[A-Z]{1,2}/?A$", I)),
        "huawei" to listOf(Regex("^[A-Z]{3}-[A-Z]{1,3}\\d{2}[A-Z]?$", I)), "honor" to listOf(Regex("^[A-Z]{3}-[A-Z]{1,3}\\d{2}[A-Z]?$", I)),
        "xiaomi" to listOf(Regex("^(M|2)\\d{3,4}[A-Z]\\d{1,2}[A-Z]{1,3}$", I), Regex("^\\d{4,5}[A-Z0-9]{3,6}$", I)),
        "asus" to listOf(Regex("^[A-Z]{1,3}\\d{3,4}[A-Z]{0,3}(-[A-Z0-9]+)?$", I)),
        "lenovo" to listOf(Regex("^\\d{2}[A-Z0-9]{2}$", I), Regex("^\\d{2}[A-Z]{3}\\d{2}$", I), Regex("^\\d{2}[A-Z]{3}$", I)),
        "hp" to listOf(Regex("^\\d{2}-[A-Z]{2}\\d{3,4}[A-Z]{0,3}$", I), Regex("^\\d{3}\\s?G\\d{1,2}$", I), Regex("^TPN-[A-Z]\\d{3}$", I)),
        "dell" to listOf(Regex("^P\\d{2,3}[A-Z]$", I), Regex("^\\d{4}$")),
        "acer" to listOf(Regex("^N\\d{2}[A-Z]\\d{1,2}$", I), Regex("^[A-Z]{1,3}\\d{1,2}-\\d{2,3}[A-Z]?$", I)),
        "msi" to listOf(Regex("^MS-\\d{4,5}$", I)), "lg" to listOf(Regex("^LM-[A-Z]\\d{3,4}[A-Z]*$", I)), "oppo" to listOf(Regex("^CPH\\d{4}$", I)),
        "realme" to listOf(Regex("^RMX\\d{4}$", I)), "vivo" to listOf(Regex("^V\\d{4}[A-Z]?$", I)),
        "monster" to listOf(Regex("^[A-Z]\\d{1,2}\\.\\d+$", I)), "casper" to listOf(Regex("^[A-Z]\\d{3,4}\\.[A-Z0-9]+$", I))
    )
    private val R_BICIM = Regex("^[A-Z]{1,5}-?[A-Z]{0,2}\\d{2,5}[A-Z0-9]{0,6}(-[A-Z0-9]{1,6})?$", I)
    private val R_KISA_HARF = Regex("^[A-Za-z]{1,3}$")
    private val R_KISA_SAYI = Regex("^\\d{1,3}$")
    private val R_YALNIZ_SAYI = Regex("^\\d+$")
    private val R_KENAR = Regex("^[^A-Za-z0-9]+|[^A-Za-z0-9)]+$")

    private fun olamaz(t: String) = R_OLAMAZ.any { it.matches(t) }
    private fun markaDesenleri(marka: String): List<Regex> = MARKA_DESEN.filterKeys { marka.contains(it) }.values.flatten()

    private fun bicimPuani(t: String): Int {
        val h = harfVar(t)
        val r = rakamVar(t)
        var p = 0
        if (h && r) p += 3
        if (t.contains('-') && h && r) p += 1
        if (R_BICIM.matches(t) && h && r) p += 3
        if (t.length in 4..14) p += 1
        if (t.length > 18) p -= 3 else if (t.length > 12 && !t.contains('-')) p -= 2
        return p
    }

    private class Baglam(val atla: Boolean, val baglam: Int = 0, val guclu: Boolean = false, val t: String = "")
    private class Kayit(val v: String, val p: Double, val neden: List<String>)

    /**
     * Model adaylarını puanlar: etiket bağlamı (“Model:” sonrası artı; “S/N”, “Input”, “P/N” sonrası eksi), biçim, marka deseni,
     * daha önce kayıtlı modellerle eşleşme, okuma güveni ve okunan bölgenin ortasına yakınlık. En olası ilk sırada.
     * [bilinen]: (model, seçili markaya ait mi) çiftleri; [marka]: seçili markanın adı (küçük harf).
     */
    fun akilliAdaylar(kel: List<Kelime>, bilinen: List<Pair<String, Boolean>>, marka: String): List<Aday> {
        val adaylar = LinkedHashMap<String, Kayit>()
        val ymin = kel.minOfOrNull { it.y0 } ?: 0f
        val ymax = kel.maxOfOrNull { it.y1 } ?: 0f
        val orta = (ymin + ymax) / 2
        val yuk = max(1f, ymax - ymin)
        val bil = HashMap<String, Int>()
        bilinen.forEach { (m, kendi) -> val k = sade(m); if (k.length >= 3) bil[k] = max(bil[k] ?: 0, if (kendi) 2 else 1) }
        val desen = markaDesenleri(marka)
        val markaIlk = marka.split(' ').firstOrNull().orEmpty()
        fun temiz(t: String) = t.replace(R_KENAR, "")
        fun ekle(deger0: String, p: Double, neden: List<String>) {
            val deger = deger0.trim()
            if (deger.length < 3 || deger.length > 40) return
            val k = sade(deger)
            if (k.isEmpty()) return
            val o = adaylar[k]
            if (o == null || o.p < p) adaylar[k] = Kayit(deger, p, neden)
        }
        for ((_, ws) in kel.groupBy { it.s }) {
            val sat = ws.sortedBy { it.x0 }
            var baglam = 0   // +1: model etiketi sonrası, -1: başka etiket (S/N, P/N, Input…) sonrası
            var guclu = false
            val durum = arrayOfNulls<Baglam>(sat.size)
            for (i in sat.indices) {
                val w = sat[i]
                val ham = w.t
                val t = temiz(ham)
                val low = t.lowercase().replace(Regex("[.\\:#]+$"), "")
                durum[i] = Baglam(true)
                if (t.isEmpty()) continue
                if (R_MODEL_ET.matches(low) || R_MODEL_EK.matches(low)) { baglam = 1; guclu = R_MODEL_GUCLU.matches(low) || low.startsWith("model"); continue }
                if (R_BASKA_ET.matches(low) || R_SNPN.find(ham) != null) { baglam = -1; continue }
                if (R_BAGLAC.matches(low)) continue
                durum[i] = Baglam(false, baglam, guclu, t)
                var tek = true
                var p = bicimPuani(t).toDouble()
                val neden = ArrayList<String>()
                if (olamaz(t)) { if (baglam != 1) tek = false; p -= 4 }
                if (!rakamVar(t)) tek = false
                if (!harfVar(t) && baglam != 1) tek = false   // yalnız rakam: yalnızca “Model:” etiketinden sonra
                if (baglam == 1) { p += if (guclu) 8 else 2; neden.add(if (guclu) MODEL_ETIKETI else "“Type” etiketinin yanında") }
                if (baglam == -1) p -= 7
                if (w.c > 0f && w.c < 40f) p -= 4 else if (w.c > 0f && w.c < 60f) p -= 2 else if (w.c >= 85f) p += 1
                p += max(0.0, 2 - abs((w.y0 + w.y1) / 2 - orta) / yuk * 4.0)
                if (desen.any { it.matches(t) }) { p += 5; neden.add("Marka model biçimine uygun") }
                val kk = sade(t)
                val b = bil[kk]
                if (b != null) { p += if (b == 2) 10 else 7; neden.add("Daha önce kayıtlı model") }
                else if (kk.length >= 4 && bil.keys.any { it.length >= 4 && (it.contains(kk) || kk.contains(it)) }) { p += 3; neden.add("Kayıtlı bir modele benziyor") }
                if (R_YALNIZ_SAYI.matches(t)) p -= 3
                if (tek && p > 0) ekle(t, p, neden)
                if (baglam == 1 && rakamVar(t) && !(i + 1 < sat.size && R_KISA_HARF.matches(temiz(sat[i + 1].t)))) baglam = 0   // etiketin değeri alındı
            }
            // öbekler: aile / marka adıyla veya etiketten sonra gelen 2-4 kelime (ör. “VivoBook 15 X515EA”, “HP 250 G8”, “IdeaPad 3 15IIL05”)
            for (j in sat.indices) for (n in 2..4) {
                if (j + n > sat.size) break
                val dd = durum.slice(j until j + n)
                if (dd.any { it == null || it.atla }) break
                val par = sat.subList(j, j + n)
                val metin = dd.map { it!!.t }
                if (metin.any { olamaz(it) && !R_KISA_SAYI.matches(it) }) break
                if (metin.none { rakamVar(it) }) continue
                if (R_URUN_SON.matches(metin[n - 1])) continue
                val ilkMarka = (markaIlk.isNotEmpty() && metin[0].lowercase() == markaIlk) || R_MARKA.matches(metin[0])
                val aile = metin.any { R_AILE.matches(it) } || ilkMarka
                val ilk = dd[0]!!
                if (!aile && ilk.baglam != 1) continue
                if (ilk.baglam == -1) continue
                var pp = 4 + n * 0.6 + (if (aile) 6 else 0) + (if (ilk.baglam == 1) (if (ilk.guclu) 7 else 2) else 0)
                val nd = ArrayList<String>()
                if (aile) nd.add("Ürün serisi adıyla birlikte")
                if (ilk.baglam == 1) nd.add(if (ilk.guclu) MODEL_ETIKETI else "“Type” etiketinin yanında")
                val ph = metin.joinToString(" ")
                val kalan = metin.drop(if (ilkMarka) 1 else 0).joinToString(" ")
                if (desen.any { it.matches(ph) || it.matches(kalan) }) { pp += 5; nd.add("Marka model biçimine uygun") }
                if (bil[sade(ph)] != null) { pp += 10; nd.add("Daha önce kayıtlı model") }
                if (par.any { it.c > 0f && it.c < 50f }) pp -= 3
                pp += max(0.0, 2 - abs((par[0].y0 + par[0].y1) / 2 - orta) / yuk * 4.0)
                ekle(ph, pp, nd)
            }
        }
        // aynı kelimeyi içeren daha zayıf öbekleri ele (ör. “X515EA” en iyiyse “15 X515EA”)
        val sonuc = ArrayList<Kayit>()
        for (a in adaylar.values.sortedByDescending { it.p }) {
            if (sonuc.size >= 6) break
            val ak = sade(a.v)
            if (sonuc.any { s -> val sk = sade(s.v); (sk.contains(ak) && s.p >= a.p) || (ak.contains(sk) && s.p >= a.p + 2) }) continue
            sonuc.add(a)
        }
        return sonuc.map { Aday(it.v, it.p, it.neden) }
    }

    /** Seri / lisans için adaylar */
    fun adaylar(metin: String, hedef: String): List<Aday> {
        val puan = LinkedHashMap<String, Int>()
        for (sat in metin.split(Regex("\\n+"))) for (t in sat.replace(Regex("\\s{2,}"), " ").trim().split(' ')) {
            val h = harfVar(t)
            val r = rakamVar(t)
            if (!r || t.endsWith(":")) continue
            val p = when (hedef) {
                "seri" -> {
                    if (R_OLAMAZ.take(8).any { it.matches(t) }) continue
                    (if (h) 3 else 1) + (if (t.length in 8..20) 3 else 0) + (if (t == t.uppercase()) 1 else 0) - (if (t.contains(Regex("[-/.]"))) 1 else 0)
                }
                "lisans" -> (if (R_ANAHTAR_TAM.matches(t)) 10 else 0) + (if (t.length > 10) 1 else 0)
                else -> bicimPuani(t)
            }
            val v = t.replace(R_KENAR, "").trim()
            if (v.length < 3 || v.length > 40) continue
            puan[v] = max(puan[v] ?: -99, p)
        }
        return puan.entries.sortedByDescending { it.value }.take(8).map { Aday(it.key, it.value.toDouble(), emptyList()) }
    }

    /**
     * Okunan yazıyı değerlendirir. Etikette açıkça yazan diğer bilgiler (seri no, ürün anahtarı) kendi alanlarına yazılır;
     * hedef alan için tek ve çok güçlü bir aday varsa doğrudan yazılır, yoksa adaylar seçim için döner.
     */
    fun yaziDegerlendir(metin: String, kel: List<Kelime>, hedef: String, bilinen: List<Pair<String, Boolean>>, marka: String): YaziSonuc {
        val o = ayristir(metin)
        if (hedef != "model" && o[hedef] != null) return YaziSonuc(dagit(metin, hedef), emptyList(), true)
        val ek = listOf("seri", "lisans").filter { it != hedef }.mapNotNull { a -> o[a]?.let { a to it } }
        val ad = if (hedef == "model") akilliAdaylar(kel, bilinen, marka) else adaylar(metin, hedef)
        if (ad.isEmpty()) return YaziSonuc(ek, emptyList(), false)
        val etiketli = ad[0].neden.contains(MODEL_ETIKETI)
        if (etiketli && ad[0].p >= 16 && (ad.size < 2 || ad[0].p - ad[1].p >= 5)) return YaziSonuc(listOf(hedef to ad[0].v) + ek, emptyList(), true)
        return YaziSonuc(ek, ad, false)
    }

    // ------------------------------------------------------------ Barkod / QR
    /** Kod türüne ve içeriğe göre hangi alana uygun olduğunu puanlar */
    fun kodPuani(k: Kod, hedef: String, marka: String): Int {
        val v = k.v.trim()
        val urunBarkodu = k.f.startsWith("ean") || k.f.startsWith("upc") || Regex("^\\d{8}$|^\\d{12,14}$").matches(v)   // ürün (EAN/UPC) barkodu: model / seri değildir
        if (ayristir(v)[hedef] != null) return 20
        if (hedef == "lisans") return if (R_ANAHTAR_TAM.matches(v)) 20 else -10
        if (R_ANAHTAR_TAM.matches(v)) return -10   // ürün anahtarı başka alana
        var p = 0
        if (urunBarkodu) p -= 12
        if (Regex("^\\d{15}$").matches(v)) p -= 8   // IMEI
        if (hedef == "seri") p += (if (harfVar(v) && rakamVar(v)) 4 else 1) + (if (v.length in 7..22) 3 else -2) + (if (v.contains(Regex("\\s"))) -3 else 0)
        if (hedef == "model") {
            p += bicimPuani(v) + (if (v.length <= 20) 1 else -4)
            MARKA_DESEN.forEach { (b, l) -> if (marka.contains(b) && l.any { it.matches(v) }) p += 6 }
        }
        return p
    }

    /**
     * Görüntüde bulunan kodları değerlendirir. null: taramaya devam. [gorulen]: sağlama toplamı olmayan türlerde
     * (Code 39, Codabar, ITF) yanlış okumaya karşı aynı değerin iki kez okunması beklenir.
     */
    fun kodDegerlendir(liste0: List<Kod>, hedef: String, marka: String, gorulen: MutableMap<String, Long>): KodSonuc? {
        val simdi = System.currentTimeMillis()
        val liste = liste0.filter { it.v.isNotBlank() }
        val kararli = liste.filter { k ->
            if (k.f != "code_39" && k.f != "codabar" && k.f != "itf") true
            else { val son = gorulen[k.v]; gorulen[k.v] = simdi; son != null && simdi - son < 1500 }
        }
        if (kararli.isEmpty()) return null
        // yapılandırılmış içerik (S/N + Model, GS1, ürün anahtarı…) her zaman doğrudan dağıtılır
        val yapili = kararli.filter { ayristir(it.v).isNotEmpty() }
        if (yapili.isNotEmpty()) return KodSonuc(yapili.joinToString("\n") { it.v }, emptyList(), yapili[0])
        val puanli = kararli.map { it to kodPuani(it, hedef, marka) }.sortedByDescending { it.second }
        // tek kod: uygun değilse (ör. seri beklerken EAN ürün barkodu) kullanıcıya sorulur
        if (puanli.size == 1 && puanli[0].second > -5) return KodSonuc(puanli[0].first.v.trim(), emptyList(), puanli[0].first)
        if (puanli.size > 1 && puanli[0].second - puanli[1].second >= 5 && puanli[0].second > 0) return KodSonuc(puanli[0].first.v.trim(), emptyList(), puanli[0].first)
        return KodSonuc(null, puanli.map { (k, p) ->
            Aday(k.v.trim(), p.toDouble(), listOfNotNull(FORMAT_AD[k.f] ?: "Kod", if (p <= -5) "ürün barkodu olabilir" else null))
        }, null)
    }

    private fun formatAdi(f: Int) = when (f) {
        Barcode.FORMAT_QR_CODE -> "qr_code"
        Barcode.FORMAT_DATA_MATRIX -> "data_matrix"
        Barcode.FORMAT_PDF417 -> "pdf417"
        Barcode.FORMAT_AZTEC -> "aztec"
        Barcode.FORMAT_CODE_128 -> "code_128"
        Barcode.FORMAT_CODE_39 -> "code_39"
        Barcode.FORMAT_CODE_93 -> "code_93"
        Barcode.FORMAT_CODABAR -> "codabar"
        Barcode.FORMAT_EAN_13 -> "ean_13"
        Barcode.FORMAT_EAN_8 -> "ean_8"
        Barcode.FORMAT_ITF -> "itf"
        Barcode.FORMAT_UPC_A -> "upc_a"
        Barcode.FORMAT_UPC_E -> "upc_e"
        else -> "kod"
    }

    private val barkodcu by lazy { BarcodeScanning.getClient() }
    private val yazici by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun kodOku(resim: Bitmap): List<Kod> = try {
        bekle(barkodcu.process(InputImage.fromBitmap(resim, 0)))?.mapNotNull { b ->
            val v = b.rawValue?.trim().orEmpty()
            if (v.isEmpty()) null else Kod(v, formatAdi(b.format), b.cornerPoints?.map { it.x.toFloat() to it.y.toFloat() } ?: emptyList())
        } ?: emptyList()
    } catch (e: Throwable) {
        emptyList()
    }

    // ------------------------------------------------------------ Yazı okuma
    /** Görüntünün yalnızca istenen bölgesini (0–1 oranları) keser ve küçükse büyütür: çevredeki numaralar karışmaz */
    fun hazirla(kaynak: Bitmap, fx: Float, fy: Float, fw: Float, fh: Float): Bitmap {
        val x = (fx * kaynak.width).toInt().coerceIn(0, kaynak.width - 1)
        val y = (fy * kaynak.height).toInt().coerceIn(0, kaynak.height - 1)
        val w = (fw * kaynak.width).toInt().coerceIn(1, kaynak.width - x)
        val h = (fh * kaynak.height).toInt().coerceIn(1, kaynak.height - y)
        val k = if (w < 1600) min(3f, 1600f / w) else 1f
        return Bitmap.createBitmap(kaynak, x, y, w, h, Matrix().apply { postScale(k, k) }, true)
    }

    private val R_YAPISIK = Regex("^([A-Za-z][A-Za-z/.]{1,9})[\\:#]([A-Za-z0-9].*)$")

    /**
     * Görüntüdeki yazıyı okur. Kelimeler ekrandaki konumlarına göre satırlara dizilir (etiket ile değeri ayrı sütunlarda
     * olsa da aynı satırda kalır); dönen metin de bu satır düzenindedir.
     */
    suspend fun yaziOku(resim: Bitmap): Pair<String, List<Kelime>> {
        val okunan = try {
            bekle(yazici.process(InputImage.fromBitmap(resim, 0)))
        } catch (e: Throwable) {
            null
        }
        val sonuc = okunan ?: return "" to emptyList()
        val ham = ArrayList<Kelime>()
        for (b in sonuc.textBlocks) for (l in b.lines) for (e in l.elements) {
            val k = e.boundingBox ?: continue
            val t = e.text.trim()
            if (t.isEmpty()) continue
            val c = try { e.confidence * 100f } catch (x: Throwable) { 0f }
            val x0 = k.left.toFloat()
            val x1 = k.right.toFloat()
            val y0 = k.top.toFloat()
            val y1 = k.bottom.toFloat()
            // "Model:X515EA" gibi yapışık okunan etiket ve değer ayrılır
            val m = R_YAPISIK.find(t)
            if (m != null) {
                val sol = m.groupValues[1] + ":"
                val ara = x0 + (x1 - x0) * sol.length / t.length
                ham.add(Kelime(sol, c, x0, y0, ara, y1))
                ham.add(Kelime(m.groupValues[2], c, ara, y0, x1, y1))
            } else ham.add(Kelime(t, c, x0, y0, x1, y1))
        }
        // satırlara diz: dikey ortası birbirine yakın kelimeler aynı satırdır
        val satirlar = ArrayList<ArrayList<Kelime>>()
        val ortalar = ArrayList<Float>()
        for (w in ham.sortedBy { it.y0 + it.y1 }) {
            val cy = (w.y0 + w.y1) / 2
            val yuk = max(1f, w.y1 - w.y0)
            val i = satirlar.indices.lastOrNull { abs(ortalar[it] - cy) <= yuk * 0.55f }
            if (i == null) { satirlar.add(arrayListOf(w)); ortalar.add(cy) }
            else { satirlar[i].add(w); ortalar[i] = satirlar[i].map { (it.y0 + it.y1) / 2 }.average().toFloat() }
        }
        val kel = ArrayList<Kelime>()
        val metin = StringBuilder()
        satirlar.forEachIndexed { si, sat ->
            val sirali = sat.sortedBy { it.x0 }
            sirali.forEach { it.s = si; kel.add(it) }
            if (si > 0) metin.append('\n')
            metin.append(sirali.joinToString(" ") { it.t })
        }
        // ürün anahtarında tire çevresine giren boşlukları temizle
        val duz = R_ANAHTAR_BOSLUKLU.replace(metin.toString()) { it.value.replace(" ", "") }
        return duz to kel
    }
}
