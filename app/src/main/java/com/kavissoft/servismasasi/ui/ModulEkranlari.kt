package com.kavissoft.servismasasi.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kavissoft.servismasasi.core.Bildirim
import com.kavissoft.servismasasi.Alan
import com.kavissoft.servismasasi.Durum
import com.kavissoft.servismasasi.Ekran
import com.kavissoft.servismasasi.Eylem
import com.kavissoft.servismasasi.Modul
import com.kavissoft.servismasasi.Moduller
import com.kavissoft.servismasasi.Tur
import com.kavissoft.servismasasi.metin
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate


private fun kpiIkon(durum: String): String = when (durum) {
    "10" -> "desktop"
    "30" -> "gear"
    "50" -> "box"
    "40" -> "clock"
    "60" -> "circle-check"
    "gec" -> "triangle-exclamation"
    else -> "screwdriver-wrench"
}

/** Ana sayfa: web panelindeki ana sayfanın aynı bölümleri, aynı sırayla. */
@Composable
internal fun AnaEkran(d: Durum) {
    val p = d.panel
    val baglam = LocalContext.current
    var arama by remember { mutableStateOf("") }
    // Android 13 ve üzeri: vade bildirimleri için izin bir kez istenir
    val izinIste = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { verildi -> if (verildi) d.yenile() }
    LaunchedEffect(p != null) {
        if (p != null && Build.VERSION.SDK_INT >= 33 && !Bildirim.izinVar(baglam)) runCatching { izinIste.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SayfaBasi("Ana Sayfa", "Hoş geldiniz" + (d.kullanici?.metin("adsoyad")?.let { ", $it" } ?: "") + (if (d.firmaNo.isNotEmpty()) " · Firma No: " + d.firmaNo else "")) }
        d.kcDuyurular.forEach { duyuru -> item { Uyari(duyuru, "blue", "bullhorn") } }
        if (d.yeniSurum.isNotEmpty()) item {
            Kart {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IkonKutu("download", "orange")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Yeni sürüm hazır", fontWeight = FontWeight.Bold, color = Koyu, fontSize = 14.sp)
                        Text("Sürüm ${d.yeniSurum}", color = Soluk, fontSize = 12.5.sp)
                    }
                    Dugme("Güncelle", birincil = true, kucuk = true) { d.guncelleGoster() }
                }
            }
        }
        if (d.mesaj.isNotEmpty()) item { Uyari(d.mesaj, "red") }
        if (d.ozetMesaj.isNotEmpty()) item { Uyari(d.ozetMesaj, "red") }
        if (p != null) {
            // Duyurular (web: .alert-info)
            val duyurular = p.optJSONArray("duyurular")
            if (duyurular != null) for (i in 0 until duyurular.length()) {
                val x = duyurular.optJSONObject(i) ?: continue
                item { Uyari(x.metin("baslik") + if (x.metin("icerik").isNotEmpty()) " — " + x.metin("icerik") else "", "blue", "bullhorn") }
            }
            // Durum kartları (web: .kpi, telefonda iki sütun)
            val kartlar = p.optJSONArray("kartlar")
            if (kartlar != null && kartlar.length() > 0) {
                val toplam = p.optInt("toplam")
                (0 until kartlar.length()).mapNotNull { kartlar.optJSONObject(it) }.chunked(2).forEach { cift ->
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            cift.forEach { k ->
                                Kpi(k, toplam, Modifier.weight(1f)) {
                                    val kod = k.metin("durum")
                                    d.modulAc(Moduller.servis, if (kod == "gec") mapOf("gecikmis" to "1") else mapOf("durum" to kod), k.metin("baslik"))
                                }
                            }
                            if (cift.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            // Servis Kaydı Ara (web: .searchbar)
            if (p.optJSONObject("moduller")?.optBoolean("servis", true) != false) item {
                Kart {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ikon("magnifying-glass", Koyu, 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("Servis Kaydı Ara", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Koyu)
                    }
                    Spacer(Modifier.height(12.dp))
                    Girdi("", arama, { arama = it.take(60) }, ipucu = "Aranacak kelimeyi girin...", klavye = KeyboardOptions(imeAction = ImeAction.Search), eylem = KeyboardActions(onSearch = { d.servisAra(arama) }))
                    Spacer(Modifier.height(10.dp))
                    Dugme("Filtrele", ikon = "filter", etkin = !d.mesgul, modifier = Modifier.fillMaxWidth()) { d.servisAra(arama) }
                }
            }
            // Son Eklenen Servis Kayıtları
            val son = p.optJSONArray("son_servisler")
            if (son != null && son.length() > 0) item {
                Kart {
                    KartBas("Son Eklenen Servis Kayıtları", "En son açılan teknik servis kayıtları", "list-ul", "blue")
                    val liste = (0 until son.length()).mapNotNull { son.optJSONObject(it) }
                    liste.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = Cizgi)
                        TabloSatiri(Moduller.servis.satir(s), durumTonu(s.optInt("durum"))) { d.servisAc(s.optInt("id")) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Dugme("Tüm Kayıtları Gör", sonIkon = "chevron-right", kucuk = true, modifier = Modifier.fillMaxWidth()) { d.modulAc(Moduller.servis) }
                }
            }
            // Servis İstatistikleri (web: .stat-row)
            val donem = p.optJSONArray("donem")
            if (donem != null && donem.length() > 0) item {
                Kart {
                    KartBas("Servis İstatistikleri", "Kayıt ve ciro performansı", "chart-column", "blue") { Rozet("Canlı", "green") }
                    val simge = listOf("calendar-day" to "blue", "chart-simple" to "teal", "calendar-week" to "purple", "calendar-days" to "orange", "calendar" to "green", "chart-column" to "slate")
                    for (i in 0 until donem.length()) {
                        val x = donem.optJSONObject(i) ?: continue
                        val (ik, tn) = simge[i % simge.size]
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().border(1.dp, Cizgi, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            IkonKutu(ik, tn, 44.dp, dolu = true)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(x.metin("baslik"), fontWeight = FontWeight.Bold, color = Koyu, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(x.metin("alt"), color = Soluk, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${x.optInt("sayi")}", fontWeight = FontWeight.ExtraBold, color = Koyu, fontSize = 16.sp)
                                Text(x.metin("ciro").ifEmpty { "kayıt" }, color = Soluk, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Durum kartı (web: .kpi) */
@Composable
private fun Kpi(k: JSONObject, toplam: Int, modifier: Modifier, tikla: () -> Unit) {
    val tonAd = k.metin("renk")
    val t = ton(tonAd)
    Kart(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = tikla), ic = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            IkonKutu(kpiIkon(k.metin("durum")), tonAd)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(k.metin("baslik"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (tonAd == "blue" || tonAd == "slate") Yazi2 else t.renk, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(k.optInt("sayi").toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Koyu)
                    if (k.metin("yuzde").isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        val artis = k.optBoolean("artis")
                        Ikon(if (artis) "arrow-up" else "arrow-down", if (artis) Yesil else Kirmizi, 9.sp, Modifier.padding(bottom = 5.dp))
                        Text(" %" + k.metin("yuzde"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (artis) Yesil else Kirmizi, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Toplam kayıt: $toplam", fontSize = 11.5.sp, color = Soluk, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** Liste satırı (web tablolarındaki satırın telefon karşılığı: başlık, alt bilgiler ve sağda rozet) */
@Composable
private fun TabloSatiri(s: com.kavissoft.servismasasi.Satir, rozetTon: String = "", tikla: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = tikla).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.baslik, fontWeight = FontWeight.SemiBold, color = Koyu, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.alt.isNotEmpty()) Text(s.alt, fontSize = 12.5.sp, color = if (s.uyari) Kirmizi else Yazi2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.alt2.isNotEmpty()) Text(s.alt2, fontSize = 11.5.sp, color = Soluk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (s.sag.isNotEmpty()) { Spacer(Modifier.width(10.dp)); Rozet(s.sag, if (s.uyari) "red" else rozetTon.ifEmpty { "blue" }) }
    }
}

/** Bir bölümün liste sayfası: başlık, "Yeni" düğmesi, arama kartı ve kayıtlar (web liste sayfalarının düzeni). */
@Composable
internal fun ListeEkrani(d: Durum) {
    BackHandler { d.listeKapat() }
    val m = d.modul
    val baglam = LocalContext.current
    if (m != null) LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SayfaBasi(d.listeBaslik, if (d.listeToplam >= 0) "${d.listeToplam} kayıt" else "") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (m.form != null) Dugme("Yeni " + m.form.baslik, ikon = "plus", birincil = true, etkin = !d.mesgul) { d.formAc() }
                    Dugme("Geri", ikon = "arrow-left", etkin = !d.mesgul) { d.listeKapat() }
                }
            }
        }
        if (m.aramali) item {
            Kart {
                Row(verticalAlignment = Alignment.Bottom) {
                    Girdi("", d.listeAra, { d.listeAra = it.take(60) }, Modifier.weight(1f), ipucu = "Ara...", ikon = "magnifying-glass",
                        klavye = KeyboardOptions(imeAction = ImeAction.Search), eylem = KeyboardActions(onSearch = { d.listeYenile() }))
                    Spacer(Modifier.width(10.dp))
                    Dugme("Filtrele", ikon = "filter", etkin = !d.mesgul) { d.listeYenile() }
                }
            }
        }
        if (d.listeOzet.isNotEmpty()) item { Uyari(d.listeOzet, "blue", "circle-info") }
        if (d.bilgi.isNotEmpty()) item { Uyari(d.bilgi, "green", "circle-check") }
        if (d.mesaj.isNotEmpty()) item { Uyari(d.mesaj, "red") }
        item {
            Kart {
                KartBas(d.listeBaslik, "Kayda dokunarak ayrıntıları ve işlemleri görün", modulIkon(m.kod), "blue")
                if (d.liste.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Ikon(modulIkon(m.kod), Cizgi2, 34.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(if (d.mesgul) "Yükleniyor..." else "Kayıt bulunamadı", color = Soluk, fontSize = 14.sp)
                    }
                }
                d.liste.forEachIndexed { i, k ->
                    if (i > 0) HorizontalDivider(color = Cizgi)
                    TabloSatiri(m.satir(k), if (m.servisMi) durumTonu(k.optInt("durum")) else "") { if (m.servisMi) d.servisAc(k.optInt("id")) else d.kayitSec(k) }
                }
                if (d.listeSayfa < d.listeSayfaSayisi) {
                    Spacer(Modifier.height(12.dp))
                    Dugme("Daha fazla göster", ikon = "chevron-down", etkin = !d.mesgul, modifier = Modifier.fillMaxWidth()) { d.listeDevam() }
                }
            }
        }
    }
    var onay by remember { mutableStateOf<Pair<Eylem, JSONObject>?>(null) }
    var silOnay by remember { mutableStateOf<JSONObject?>(null) }
    var fotoDosya by remember { mutableStateOf<File?>(null) }
    // Fotoğraf: kameradan çekilir ya da galeriden seçilir; yüklenmeden önce küçültülür
    val izinle = kameraIzni()
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { cekildi ->
        val f = fotoDosya
        if (cekildi && f != null) fotoKucult { BitmapFactory.decodeFile(f.absolutePath, it) to runCatching { ExifInterface(f.absolutePath) }.getOrNull() }?.let { d.belgeYukle(it) }
        runCatching { f?.delete() }
        fotoDosya = null
    }
    val galeri = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { adres ->
        if (adres != null) fotoKucult { secenek ->
            val resim = baglam.contentResolver.openInputStream(adres)?.use { BitmapFactory.decodeStream(it, null, secenek) }
            val exif = runCatching { baglam.contentResolver.openInputStream(adres)?.use { ExifInterface(it) } }.getOrNull()
            resim to exif
        }?.let { d.belgeYukle(it) }
    }
    onay?.let { (e, kayit) ->
        AlertDialog(
            onDismissRequest = { onay = null },
            title = { Text(e.etiket) },
            text = { Text(m?.satir?.invoke(kayit)?.baslik.orEmpty() + " için bu işlem yapılsın mı?") },
            confirmButton = { TextButton(onClick = { onay = null; d.eylemYap(e, kayit) }) { Text("Evet") } },
            dismissButton = { TextButton(onClick = { onay = null }) { Text("Vazgeç") } }
        )
    }
    silOnay?.let { kayit ->
        AlertDialog(
            onDismissRequest = { silOnay = null },
            title = { Text("Kayıt silinsin mi?") },
            text = { Text(m?.satir?.invoke(kayit)?.baslik.orEmpty() + " kalıcı olarak silinecek. Bu işlem geri alınamaz.") },
            confirmButton = { TextButton(onClick = { silOnay = null; d.kayitSil(kayit) }) { Text("Sil", color = Kirmizi) } },
            dismissButton = { TextButton(onClick = { silOnay = null }) { Text("Vazgeç") } }
        )
    }
    d.belgeResim?.let { resim ->
        AlertDialog(
            onDismissRequest = { d.belgeResim = null },
            text = { Image(bitmap = resim.asImageBitmap(), contentDescription = "Belge", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { d.belgeResim = null }) { Text("Kapat") } }
        )
    }
    val secili = d.secili
    if (secili != null && m != null && d.belgeResim == null) {
        val tel = secili.metin("tel")
        AlertDialog(
            onDismissRequest = { d.secili = null },
            title = { Text(m.satir(secili).baslik) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    m.detay(secili).forEach { (ad, deger) -> Satir(ad, deger) }
                    if (d.bilgi.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Uyari(d.bilgi, "green", "circle-check") }
                    m.eylemler.filter { it.kosul(secili) }.forEach { e ->
                        Spacer(Modifier.height(8.dp))
                        Dugme(e.etiket, birincil = true, etkin = !d.mesgul, modifier = Modifier.fillMaxWidth()) {
                            if (e.git != null || e.alanlar.isNotEmpty()) d.eylemYap(e, secili) else { d.secili = null; onay = e to secili }
                        }
                    }
                    if (m.belge) {
                        Spacer(Modifier.height(14.dp))
                        Text("Belgeler / fotoğraflar", color = Yazi2, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (d.belgeler.isEmpty()) Text("Ekli belge yok.", color = Soluk, fontSize = 13.sp)
                        d.belgeler.forEach { b ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = !d.mesgul) { d.belgeAc(b.metin("kimlik")) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Ikon("image", Birincil, 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(b.metin("ad"), color = Koyu, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dugme("Fotoğraf çek", ikon = "camera", kucuk = true, etkin = !d.mesgul, modifier = Modifier.weight(1f)) {
                                izinle { runCatching {
                                    val klasor = File(baglam.cacheDir, "foto").apply { mkdirs() }
                                    val f = File(klasor, "cekim.jpg")
                                    fotoDosya = f
                                    kamera.launch(FileProvider.getUriForFile(baglam, baglam.packageName + ".dosya", f))
                                } }
                            }
                            Dugme("Galeriden", ikon = "image", kucuk = true, etkin = !d.mesgul, modifier = Modifier.weight(1f)) { runCatching { galeri.launch("image/*") } }
                        }
                    }
                    if (m.form != null && m.form.duzenle.isNotEmpty() || m.silSayfa.isNotEmpty() || tel.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (m.form != null && m.form.duzenle.isNotEmpty()) Dugme("Düzenle", ikon = "pen-to-square", kucuk = true, etkin = !d.mesgul, modifier = Modifier.weight(1f)) { d.formAc(secili) }
                            if (tel.isNotEmpty()) Dugme("Ara", ikon = "phone", kucuk = true, modifier = Modifier.weight(1f)) {
                                runCatching { baglam.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + tel.filter { it.isDigit() || it == '+' }))) }
                            }
                            if (m.silSayfa.isNotEmpty()) Dugme("Sil", ikon = "trash", kucuk = true, tehlike = true, etkin = !d.mesgul, modifier = Modifier.weight(1f)) { d.secili = null; silOnay = secili }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { d.secili = null }) { Text("Kapat") } }
        )
    }
}

/**
 * Fotoğrafı en uzun kenarı [uzunKenar] piksel olacak biçimde küçültür ve yönünü düzeltir.
 * [coz]: verilen seçeneklerle resmi çözer ve (varsa) yön bilgisini verir.
 */
internal fun fotoBitmap(uzunKenar: Int = 1600, coz: (BitmapFactory.Options) -> Pair<Bitmap?, ExifInterface?>): Bitmap? = try {
    val olcu = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    coz(olcu)
    var ornek = 1
    while (maxOf(olcu.outWidth, olcu.outHeight) / ornek > uzunKenar * 2) ornek *= 2
    val (ham, exif) = coz(BitmapFactory.Options().apply { inSampleSize = ornek })
    if (ham == null) null else {
        val uzun = maxOf(ham.width, ham.height)
        val oran = if (uzun > uzunKenar) uzunKenar.toFloat() / uzun else 1f
        val aci = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val m = Matrix().apply { postScale(oran, oran); if (aci != 0f) postRotate(aci) }
        Bitmap.createBitmap(ham, 0, 0, ham.width, ham.height, m, true)
    }
} catch (e: Throwable) {
    null
}

/** Yüklenecek fotoğraf: küçültülmüş JPEG (yeniden kodlama konum gibi üst verileri de atar). */
internal fun fotoKucult(coz: (BitmapFactory.Options) -> Pair<Bitmap?, ExifInterface?>): ByteArray? = try {
    val son = fotoBitmap(1600, coz)
    if (son == null) null else {
        val cikis = ByteArrayOutputStream()
        son.compress(Bitmap.CompressFormat.JPEG, 82, cikis)
        cikis.toByteArray()
    }
} catch (e: Throwable) {
    null
}

/** Kayıt ekleme / düzenleme formu (web form sayfalarının düzeni: başlık, kart içinde alanlar, Kaydet düğmesi). */
@Composable
internal fun FormEkrani(d: Durum) {
    BackHandler { d.formKapat() }
    val f = d.form ?: return
    val modulFormu = f === d.modul?.form
    val baslik = if (!modulFormu) f.baslik else if (d.duzenleniyor) f.baslik + " Düzenle" else "Yeni " + f.baslik
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
        SayfaBasi(baslik, if (modulFormu) "Yıldızlı alanlar zorunludur; diğer alanlar isteğe bağlıdır." else "") {
            Dugme("Vazgeç", ikon = "arrow-left", kucuk = true, etkin = !d.mesgul) { d.formKapat() }
        }
        Spacer(Modifier.height(14.dp))
        Hata(d.mesaj)
        if (d.bilgi.isNotEmpty()) { Uyari(d.bilgi, "green", "circle-check"); Spacer(Modifier.height(12.dp)) }
        Kart {
            KartBas(f.baslik, "", modulIkon(d.modul?.kod ?: ""), "blue")
            f.alanlar.forEachIndexed { i, a ->
                if (i > 0) Spacer(Modifier.height(14.dp))
                AlanGirisi(d, a)
            }
        }
        Spacer(Modifier.height(16.dp))
        AnaDugme("Kaydet", d.mesgul, ikon = "floppy-disk") { d.formGonder() }
    }
}

@Composable
private fun AlanGirisi(d: Durum, a: Alan) {
    val deger = d.formDeger[a.ad] ?: ""
    val baglam = LocalContext.current
    when (a.tur) {
        Tur.ONAY -> Row(Modifier.fillMaxWidth().clickable { d.formDeger[a.ad] = if (deger == "1") "" else "1" }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = deger == "1", onCheckedChange = { d.formDeger[a.ad] = if (it) "1" else "" })
            Text(a.etiket, color = Koyu, fontSize = 14.sp)
        }
        Tur.TARIH -> SecimKutusu(a.etiket, if (deger.isEmpty()) "gg.aa.yyyy" else trTarihYaz(deger), a.zorunlu, "calendar-days") {
            val t = runCatching { LocalDate.parse(deger) }.getOrDefault(LocalDate.now())
            DatePickerDialog(baglam, { _, y, ay, g -> d.formDeger[a.ad] = LocalDate.of(y, ay + 1, g).toString() }, t.year, t.monthValue - 1, t.dayOfMonth).show()
        }
        Tur.SECIM -> {
            var acik by remember { mutableStateOf(false) }
            val secenekler = secenekler(d, a)
            SecimKutusu(a.etiket, secenekler.firstOrNull { it.first == deger }?.second ?: "Seçin", a.zorunlu) { acik = true }
            if (acik) AlertDialog(
                onDismissRequest = { acik = false },
                title = { Text(a.etiket) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (!a.zorunlu && deger.isNotEmpty()) Text("— Seçimi kaldır —", color = Soluk, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable { d.formDeger[a.ad] = ""; acik = false }.padding(vertical = 12.dp))
                        secenekler.forEach { (kod, ad) ->
                            Text(ad, color = if (kod == deger) Birincil else Koyu, fontWeight = if (kod == deger) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().clickable { d.formDeger[a.ad] = kod; acik = false }.padding(vertical = 12.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { acik = false }) { Text("Kapat") } }
            )
        }
        Tur.ARA -> {
            var acik by remember { mutableStateOf(false) }
            var soru by remember { mutableStateOf("") }
            var yeniAd by remember { mutableStateOf("") }
            var yeniTel by remember { mutableStateOf("") }
            val musteriMi = a.kaynak == "musteriler"
            SecimKutusu(a.etiket, d.formEtiket[a.ad] ?: if (deger.isEmpty()) "Seçmek için dokunun" else "#$deger", a.zorunlu, "magnifying-glass") { d.araTemizle(); soru = ""; acik = true }
            if (acik) AlertDialog(
                onDismissRequest = { acik = false },
                title = { Text(a.etiket.substringBefore(" (")) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Girdi("", soru, { soru = it.take(60) }, Modifier.weight(1f), ipucu = if (musteriMi) "Ad, telefon ya da firma" else "Parça adı", ikon = "magnifying-glass",
                                klavye = KeyboardOptions(imeAction = ImeAction.Search), eylem = KeyboardActions(onSearch = { d.araYap(a.kaynak, soru) }))
                            Spacer(Modifier.width(8.dp))
                            Dugme("Ara", kucuk = true, etkin = !d.mesgul) { d.araYap(a.kaynak, soru) }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (d.mesgul) Text("Aranıyor...", color = Soluk, fontSize = 13.sp)
                        d.araSonuc.take(25).forEach { r ->
                            val ad = if (musteriMi) r.metin("adsoyad") else r.metin("adi")
                            val alt = if (musteriMi) listOf(r.metin("tel"), r.metin("firma")).filter { it.isNotEmpty() }.joinToString(" · ") else "${r.optInt("adet")} adet · ${r.metin("satis_tutari_yazi")}"
                            Column(Modifier.fillMaxWidth().clickable { d.formDeger[a.ad] = r.optInt("id").toString(); d.formEtiket[a.ad] = ad; acik = false }.padding(vertical = 9.dp)) {
                                Text(ad, color = Koyu, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                if (alt.isNotEmpty()) Text(alt, color = Soluk, fontSize = 12.sp)
                            }
                            HorizontalDivider(color = Cizgi)
                        }
                        if (deger.isNotEmpty() && !a.zorunlu) {
                            Spacer(Modifier.height(8.dp))
                            Dugme("Seçimi kaldır", kucuk = true, modifier = Modifier.fillMaxWidth()) { d.formDeger[a.ad] = ""; d.formEtiket.remove(a.ad); acik = false }
                        }
                        if (musteriMi) {
                            Spacer(Modifier.height(14.dp))
                            Text("Yeni müşteri ekle", color = Yazi2, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Girdi("", yeniAd, { yeniAd = it.take(120) }, ipucu = "Adı Soyadı", ikon = "user")
                            Spacer(Modifier.height(8.dp))
                            Girdi("", yeniTel, { yeniTel = it.take(20) }, ipucu = "Cep telefonu (05xx xxx xx xx)", ikon = "phone", klavye = KeyboardOptions(keyboardType = KeyboardType.Phone))
                            Spacer(Modifier.height(8.dp))
                            if (d.mesaj.isNotEmpty()) { Uyari(d.mesaj, "red"); Spacer(Modifier.height(8.dp)) }
                            Dugme("Ekle ve seç", ikon = "user-plus", birincil = true, kucuk = true, etkin = !d.mesgul && yeniAd.isNotBlank(), modifier = Modifier.fillMaxWidth()) { d.hizliMusteri(a.ad, yeniAd, yeniTel) { yeniAd = ""; yeniTel = ""; acik = false } }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { acik = false }) { Text("Kapat") } }
            )
        }
        Tur.COKLU -> {
            var acik by remember { mutableStateOf(false) }
            val secenekler = secenekler(d, a)
            val secili = deger.split(",").filter { it.isNotBlank() }.toSet()
            SecimKutusu(a.etiket, if (secili.isEmpty()) "Seçilmedi" else if (secili.size == secenekler.size) "Tümü (${secili.size})" else "${secili.size} seçili", a.zorunlu) { acik = true }
            if (acik) AlertDialog(
                onDismissRequest = { acik = false },
                title = { Text(a.etiket) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dugme("Tümünü seç", ikon = "check", kucuk = true) { d.formDeger[a.ad] = secenekler.joinToString(",") { it.first } }
                            Dugme("Temizle", kucuk = true) { d.formDeger[a.ad] = "" }
                        }
                        secenekler.forEach { (kod, ad) ->
                            val isaretli = kod in secili
                            val degistir = { d.formDeger[a.ad] = (if (isaretli) secili - kod else secili + kod).joinToString(",") }
                            Row(Modifier.fillMaxWidth().clickable { degistir() }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isaretli, onCheckedChange = { degistir() })
                                Text(ad, color = Koyu, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { acik = false }) { Text("Tamam") } }
            )
        }
        Tur.DESEN -> {
            var acik by remember { mutableStateOf(false) }
            SecimKutusu(a.etiket, if (deger.isEmpty()) "Desen çiz" else "Desen: $deger", a.zorunlu, "pen-to-square") { acik = true }
            if (acik) DesenPenceresi(deger, { acik = false }) { d.formDeger[a.ad] = it; acik = false }
        }
        Tur.METIN -> if (a.tara.isEmpty()) Girdi(a.etiket, deger, { d.formDeger[a.ad] = it.take(190) }, zorunlu = a.zorunlu) else {
            // Etiketi kamerayla okutma (web panelindeki "telefonla okut" ile aynı)
            var acik by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.Bottom) {
                Girdi(a.etiket, deger, { d.formDeger[a.ad] = it.take(190) }, Modifier.weight(1f), zorunlu = a.zorunlu)
                Spacer(Modifier.width(8.dp))
                IkonDugme("camera", etkin = !d.mesgul) { acik = true }
            }
            if (acik) TaraPenceresi(d, a) { acik = false }
        }
        else -> Girdi(
            a.etiket, deger, { d.formDeger[a.ad] = it.take(if (a.tur == Tur.COKSATIR) 1000 else 190) }, zorunlu = a.zorunlu, cokSatir = a.tur == Tur.COKSATIR,
            klavye = KeyboardOptions(
                keyboardType = when (a.tur) {
                    Tur.SAYI -> KeyboardType.Decimal
                    Tur.TELEFON -> KeyboardType.Phone
                    Tur.PAROLA -> KeyboardType.Password
                    else -> KeyboardType.Text
                }
            ),
            gorunum = if (a.tur == Tur.PAROLA) PasswordVisualTransformation() else VisualTransformation.None
        )
    }
}

private fun secenekler(d: Durum, a: Alan): List<Pair<String, String>> {
    if (a.kaynak.isEmpty()) return a.secenek
    val dizi = d.sabitler?.optJSONArray(a.kaynak) ?: return a.secenek
    return (0 until dizi.length()).mapNotNull { dizi.optJSONObject(it) }.map { it.metin("kod") to it.metin("ad") }
}

private fun trTarihYaz(t: String): String = runCatching { LocalDate.parse(t).let { "%02d.%02d.%d".format(it.dayOfMonth, it.monthValue, it.year) } }.getOrDefault(t)

/** Ekran kilidi deseni: 3 × 3 noktada parmağı sürükleyerek ya da noktalara sırayla dokunarak çizilir (noktalar 1–9). */
@Composable
private fun DesenPenceresi(mevcut: String, kapat: () -> Unit, kaydet: (String) -> Unit) {
    val secilen = remember { mutableStateListOf<Int>().apply { addAll(mevcut.mapNotNull { it.digitToIntOrNull() }.filter { it in 1..9 }) } }
    fun nokta(konum: Offset, boyut: Float): Int? {
        val hucre = boyut / 3f
        for (i in 0 until 9) {
            val merkez = Offset(hucre * (i % 3) + hucre / 2f, hucre * (i / 3) + hucre / 2f)
            if ((konum - merkez).getDistance() <= hucre * 0.34f) return i + 1
        }
        return null
    }
    AlertDialog(
        onDismissRequest = kapat,
        title = { Text("Kilit Desenini Çizin") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Parmağınızı noktaların üzerinde sürükleyin ya da noktalara sırayla dokunun.", color = Soluk, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Canvas(
                    Modifier.size(240.dp).background(Zemin, RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { p -> secilen.clear(); nokta(p, size.width.toFloat())?.let { secilen.add(it) } },
                                onDrag = { degisim, _ -> nokta(degisim.position, size.width.toFloat())?.let { if (it !in secilen) secilen.add(it) } }
                            )
                        }
                        .pointerInput(Unit) { detectTapGestures { p -> nokta(p, size.width.toFloat())?.let { if (it !in secilen) secilen.add(it) } } }
                ) {
                    val hucre = size.width / 3f
                    fun merkez(n: Int) = Offset(hucre * ((n - 1) % 3) + hucre / 2f, hucre * ((n - 1) / 3) + hucre / 2f)
                    for (i in 1 until secilen.size) drawLine(Birincil, merkez(secilen[i - 1]), merkez(secilen[i]), strokeWidth = 8f, cap = StrokeCap.Round)
                    for (n in 1..9) {
                        val dolu = n in secilen
                        drawCircle(if (dolu) Birincil else Cizgi2, radius = if (dolu) hucre * 0.16f else hucre * 0.11f, center = merkez(n))
                        if (dolu && secilen.firstOrNull() == n) drawCircle(Color.White, radius = hucre * 0.06f, center = merkez(n))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(if (secilen.isEmpty()) "Henüz çizilmedi" else "Desen: " + secilen.joinToString(""), color = Koyu, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Beyaz nokta desenin başladığı yeri gösterir.", color = Soluk, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { kaydet(secilen.joinToString("")) }) { Text("Kaydet") } },
        dismissButton = {
            Row {
                TextButton(onClick = { secilen.clear() }) { Text("Temizle") }
                TextButton(onClick = kapat) { Text("Vazgeç") }
            }
        }
    )
}
