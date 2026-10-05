package com.kavissoft.servismasasi.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kavissoft.servismasasi.Alan
import com.kavissoft.servismasasi.Durum
import com.kavissoft.servismasasi.core.Tarayici
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Kamera izni varsa işlemi hemen çalıştırır; yoksa önce izni ister (izin verilmezse kullanıcıya bildirir). */
@Composable
internal fun kameraIzni(): (() -> Unit) -> Unit {
    val baglam = LocalContext.current
    var bekleyen by remember { mutableStateOf<(() -> Unit)?>(null) }
    val izin = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { verildi ->
        val b = bekleyen
        bekleyen = null
        if (verildi) b?.invoke()
        else Toast.makeText(baglam, "Kamera izni verilmedi. Telefon ayarlarından uygulamaya kamera izni verin.", Toast.LENGTH_LONG).show()
    }
    return { islem ->
        if (ContextCompat.checkSelfPermission(baglam, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) islem()
        else { bekleyen = islem; izin.launch(Manifest.permission.CAMERA) }
    }
}

// Okuma bölgeleri (görüntünün oranı): şerit ve çerçevenin tamamı — web paneliyle aynı
private val SERIT = floatArrayOf(0.05f, 0.37f, 0.90f, 0.26f)
private val CERCEVE = floatArrayOf(0.06f, 0.14f, 0.88f, 0.72f)

/**
 * Etiket okutma penceresi (web panelindeki "telefonla okut" ile aynı işleyiş): canlı kamera görüntüsünde barkod / QR
 * kendiliğinden okunur; yazı için numara şeride hizalanıp "Yazıyı / Numarayı Oku"ya basılır ve yalnızca şeridin içi okunur.
 * Emin olunamayan durumlarda en olası adaylar listelenir, doğru olan dokunarak seçilir.
 */
@Composable
internal fun TaraPenceresi(d: Durum, a: Alan, kapat: () -> Unit) {
    val baglam = LocalContext.current
    val yasam = LocalLifecycleOwner.current
    val kapsam = rememberCoroutineScope()
    val dokunus = LocalHapticFeedback.current
    val hedef = a.tara
    val hedefAd = Tarayici.ADLAR[hedef] ?: a.etiket
    val marka = remember { d.taraMarka() }

    var izinVar by remember { mutableStateOf(ContextCompat.checkSelfPermission(baglam, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var durum by remember { mutableStateOf("") }
    var hataMi by remember { mutableStateOf(false) }
    var serit by remember { mutableStateOf(true) }
    var taraniyor by remember { mutableStateOf(true) }
    var okunuyor by remember { mutableStateOf(false) }
    var adaylar by remember { mutableStateOf<List<Tarayici.Aday>>(emptyList()) }
    var adayKod by remember { mutableStateOf(false) }
    var sonuc by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var kamera by remember { mutableStateOf<Camera?>(null) }
    var fener by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var kodCizgi by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var odak by remember { mutableStateOf<Offset?>(null) }
    var elle by remember { mutableStateOf("") }
    val ek = remember { mutableStateListOf<Pair<String, String>>() }   // bu oturumda başka alanlara yazılanlar
    val gorulen = remember { HashMap<String, Long>() }
    val onizleme = remember {
        PreviewView(baglam).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    fun bitir(yazilan: List<Pair<String, String>>) {
        val hepsi = yazilan + ek.filter { e -> yazilan.none { it.first == e.first } }
        taraniyor = false
        adaylar = emptyList()
        durum = ""
        hataMi = false
        d.taraYaz(hepsi)
        sonuc = hepsi
        dokunus.performHapticFeedback(HapticFeedbackType.LongPress)
        kapsam.launch { delay(900); kapat() }
    }

    fun bulundu(ham: String) = bitir(Tarayici.dagit(ham, hedef))

    fun kodUygula(k: Tarayici.KodSonuc) {
        val ham = k.yaz
        if (ham != null) bulundu(ham)
        else {
            // birden fazla kod ya da emin olunamayan tek kod: seçtir
            taraniyor = false
            adayKod = true
            adaylar = k.sor
            dokunus.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    suspend fun yaziOku(resim: Bitmap) {
        okunuyor = true
        hataMi = false
        durum = "Yazı okunuyor…"
        try {
            val (metin, kel) = Tarayici.yaziOku(resim)
            val bilinen = if (hedef == "model") d.modelOneri() else emptyList()
            val r = Tarayici.yaziDegerlendir(metin, kel, hedef, bilinen, marka)
            durum = ""
            if (r.bitti) bitir(r.yazilan)
            else {
                if (r.yazilan.isNotEmpty()) {
                    d.taraYaz(r.yazilan)
                    r.yazilan.forEach { y -> ek.removeAll { it.first == y.first }; ek.add(y) }
                }
                if (r.adaylar.isEmpty()) {
                    hataMi = true
                    durum = "Uygun " + hedefAd.lowercase() + " bulunamadı. Numarayı şeride hizalayıp, yaklaşıp ışığı artırarak tekrar deneyin."
                } else {
                    adayKod = false
                    adaylar = r.adaylar
                }
            }
        } catch (e: Exception) {
            hataMi = true
            durum = "Yazı okunamadı. Tekrar deneyin."
        } finally {
            okunuyor = false
        }
    }

    fun kameradanOku() {
        if (okunuyor) return
        val kare = if (izinVar) runCatching { onizleme.bitmap }.getOrNull() else null
        if (kare == null) {
            hataMi = true
            durum = "Önce kamera açılmalı; ya da “Galeriden oku”yu kullanın."
            return
        }
        dokunus.performHapticFeedback(HapticFeedbackType.LongPress)
        adaylar = emptyList()
        okunuyor = true
        kapsam.launch {
            // önce karede barkod / QR var mı bak (varsa yazı okumaya gerek yok)
            val k = Tarayici.kodDegerlendir(Tarayici.kodOku(kare), hedef, marka, gorulen)
            if (k != null) {
                okunuyor = false
                kodUygula(k)
                return@launch
            }
            // yalnızca ortadaki okuma şeridi okunur: çevredeki numaralar karışmaz
            val b = if (serit) SERIT else CERCEVE
            val kesit = runCatching { Tarayici.hazirla(kare, b[0], b[1], b[2], b[3]) }.getOrDefault(kare)
            yaziOku(kesit)
        }
    }

    fun odakla(o: Offset) {
        val k = kamera ?: return
        runCatching {
            val nokta = onizleme.meteringPointFactory.createPoint(o.x, o.y)
            k.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(nokta).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
        }
        odak = o
        kapsam.launch { delay(900); odak = null }
    }

    val izin = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { verildi ->
        izinVar = verildi
        if (!verildi) {
            hataMi = true
            durum = "Kamera izni verilmedi. Telefon ayarlarından uygulamaya kamera izni verin veya “Galeriden oku”yu kullanın."
        }
    }
    val galeri = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { adres ->
        if (adres != null) {
            val resim = fotoBitmap(2200) { secenek ->
                val r = baglam.contentResolver.openInputStream(adres)?.use { BitmapFactory.decodeStream(it, null, secenek) }
                val exif = runCatching { baglam.contentResolver.openInputStream(adres)?.use { ExifInterface(it) } }.getOrNull()
                r to exif
            }
            if (resim == null) {
                hataMi = true
                durum = "Fotoğraf açılamadı."
            } else {
                adaylar = emptyList()
                okunuyor = true
                durum = "Fotoğraf okunuyor…"
                kapsam.launch {
                    val kodlar = Tarayici.kodOku(resim)
                    if (kodlar.isNotEmpty()) {
                        okunuyor = false
                        durum = ""
                        val k = Tarayici.kodDegerlendir(kodlar, hedef, marka, gorulen)
                        if (k != null) kodUygula(k) else bulundu(kodlar[0].v)
                    } else yaziOku(resim)   // barkod yoksa fotoğraftaki yazı / numara okunur
                }
            }
        }
    }

    LaunchedEffect(Unit) { if (!izinVar) izin.launch(Manifest.permission.CAMERA) }

    // Kamera: arka kamera, sürekli netleme; pencere kapanınca bırakılır
    DisposableEffect(izinVar) {
        var saglayici: ProcessCameraProvider? = null
        var kapandi = false
        if (izinVar) {
            val gelecek = ProcessCameraProvider.getInstance(baglam)
            gelecek.addListener({
                if (!kapandi) try {
                    val s = gelecek.get()
                    saglayici = s
                    val on = Preview.Builder().build()
                    on.setSurfaceProvider(onizleme.surfaceProvider)
                    s.unbindAll()
                    kamera = s.bindToLifecycle(yasam, CameraSelector.DEFAULT_BACK_CAMERA, on)
                } catch (e: Exception) {
                    hataMi = true
                    durum = "Kamera açılamadı. “Galeriden oku” ile devam edebilirsiniz."
                }
            }, ContextCompat.getMainExecutor(baglam))
        }
        onDispose {
            kapandi = true
            runCatching { saglayici?.unbindAll() }
            kamera = null
        }
    }

    // Barkod / QR: canlı görüntüde kendiliğinden aranır
    LaunchedEffect(izinVar, taraniyor) {
        if (!izinVar || !taraniyor) return@LaunchedEffect
        while (isActive) {
            delay(220)
            if (okunuyor || adaylar.isNotEmpty()) continue
            val kare = runCatching { onizleme.bitmap }.getOrNull() ?: continue
            val kodlar = Tarayici.kodOku(kare)
            runCatching { kare.recycle() }
            if (okunuyor || adaylar.isNotEmpty() || !taraniyor) continue
            kodCizgi = kodlar.map { k -> k.kose.map { Offset(it.first, it.second) } }
            val k = Tarayici.kodDegerlendir(kodlar, hedef, marka, gorulen) ?: continue
            kodUygula(k)
        }
    }

    Dialog(onDismissRequest = kapat, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Zemin).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Ikon("barcode", Birincil, 18.sp)
                Spacer(Modifier.width(10.dp))
                Text("$hedefAd okut", color = Koyu, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IkonDugme("xmark") { kapat() }
            }
            Spacer(Modifier.height(12.dp))

            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
                val kenar = maxWidth
                if (izinVar) AndroidView(factory = { onizleme }, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { odakla(it) } }) {
                    val b = if (serit) SERIT else CERCEVE
                    val sol = b[0] * size.width
                    val ust = b[1] * size.height
                    val gen = b[2] * size.width
                    val yuk = b[3] * size.height
                    val golge = Color(0x73000000)
                    drawRect(golge, Offset(0f, 0f), Size(size.width, ust))
                    drawRect(golge, Offset(0f, ust + yuk), Size(size.width, size.height - ust - yuk))
                    drawRect(golge, Offset(0f, ust), Size(sol, yuk))
                    drawRect(golge, Offset(sol + gen, ust), Size(size.width - sol - gen, yuk))
                    drawRoundRect(Color.White, Offset(sol, ust), Size(gen, yuk), CornerRadius(14f, 14f), style = Stroke(3f))
                    kodCizgi.forEach { n ->
                        if (n.size >= 2) {
                            val yol = Path()
                            yol.moveTo(n[0].x, n[0].y)
                            n.drop(1).forEach { yol.lineTo(it.x, it.y) }
                            yol.close()
                            drawPath(yol, Color(0xFF2FD16B), style = Stroke(5f))
                        }
                    }
                    odak?.let { drawCircle(Color.White, 46f, it, style = Stroke(3f)) }
                }
                Text(
                    if (serit) "Model / numarayı bu şeride hizalayın" else "Etiketi çerçevenin içine getirin",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = kenar * (if (serit) SERIT[1] else CERCEVE[1]) - 26.dp)
                        .background(Color(0x99000000), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                )
                // yakınlaştırma: yakından netlemeyen telefonlarda biraz uzaktan 2x ile okumak en iyi sonucu verir
                val azami = kamera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 2f
                Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (kamera != null) listOf(1f, 1.5f, 2f, 3f).filter { it <= azami }.forEach { z ->
                        val secili = zoom == z
                        Text(
                            (if (z % 1f == 0f) z.toInt().toString() else z.toString()) + "x",
                            color = if (secili) Color.White else Koyu, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (secili) Birincil else Color(0xE6FFFFFF))
                                .clickable { runCatching { kamera?.cameraControl?.setZoomRatio(z) }; zoom = z }.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                val alt = if (durum.isNotEmpty()) durum else if (!izinVar) "Kamera izni bekleniyor…" else if (taraniyor && adaylar.isEmpty()) "Barkod / QR otomatik aranıyor · bulanıksa netlemek için görüntüye dokunun" else ""
                if (alt.isNotEmpty()) Text(
                    alt, color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth()
                        .background(if (hataMi && durum.isNotEmpty()) Color(0xE6C92A2A) else Color(0x99000000), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
            Spacer(Modifier.height(12.dp))

            if (sonuc.isNotEmpty()) {
                Uyari("Okundu — " + sonuc.joinToString(" · ") { (Tarayici.ADLAR[it.first] ?: it.first) + ": " + it.second }, "green", "circle-check")
                Spacer(Modifier.height(12.dp))
            }

            if (adaylar.isNotEmpty()) {
                Kart {
                    Text(
                        if (adayKod) "Etikette " + (if (adaylar.size > 1) adaylar.size.toString() + " kod bulundu" else "bir kod bulundu") + " — $hedefAd için doğru olana dokunun:"
                        else "$hedefAd için en uygun eşleşmeler — doğru olana dokunun:",
                        color = Koyu, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    adaylar.forEachIndexed { i, ad ->
                        val enIyi = i == 0 && (!adayKod || ad.p > 0)
                        val bicim = RoundedCornerShape(11.dp)
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(bicim).background(if (enIyi) Color(0xFFEDF4FF) else KartRenk)
                                .border(1.dp, if (enIyi) Birincil else Cizgi2, bicim)
                                .clickable { if (adayKod) bulundu(ad.v) else bitir(listOf(hedef to ad.v)) }.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ad.v, color = Koyu, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (enIyi) Rozet("En olası")
                            }
                            if (ad.neden.isNotEmpty()) Text(ad.neden.take(2).joinToString(" · "), color = Soluk, fontSize = 12.sp)
                        }
                    }
                    if (adayKod) Dugme("Taramaya devam et", ikon = "rotate", kucuk = true) {
                        adaylar = emptyList()
                        gorulen.clear()
                        kodCizgi = emptyList()
                        taraniyor = true
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Kart {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dugme("Yazıyı / Numarayı Oku", ikon = "font", birincil = true, mesgul = okunuyor, modifier = Modifier.weight(1f)) { kameradanOku() }
                    Spacer(Modifier.width(6.dp))
                    Row(Modifier.clickable { serit = !serit }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = serit, onCheckedChange = { serit = it })
                        Text("Yalnız şerit", color = Koyu, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Barkod ve QR kendiliğinden okunur. Etikette yalnızca yazı varsa (ör. “Model: X515EA”) numarayı şeride getirip bu düğmeye basın. “Yalnız şerit” kapalıysa çerçevenin tamamı okunur.",
                    color = Soluk, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dugme("Galeriden oku", ikon = "image", kucuk = true, etkin = !okunuyor, modifier = Modifier.weight(1f)) { runCatching { galeri.launch("image/*") } }
                    if (kamera?.cameraInfo?.hasFlashUnit() == true) Dugme(if (fener) "Feneri kapat" else "Fener", ikon = "lightbulb", kucuk = true, modifier = Modifier.weight(1f)) {
                        fener = !fener
                        runCatching { kamera?.cameraControl?.enableTorch(fener) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Girdi("", elle, { elle = it.take(190) }, Modifier.weight(1f), ipucu = "veya kodu elle yazın")
                    Spacer(Modifier.width(8.dp))
                    Dugme("Uygula", birincil = true, etkin = elle.isNotBlank()) { if (elle.isNotBlank()) bulundu(elle.trim()) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Birden fazla bilgi içeren etiketlerde (S/N, Model, ürün anahtarı) her bilgi kendi alanına yazılır. Okuma telefonun içinde yapılır; görüntü kaydedilmez ve hiçbir yere gönderilmez.",
                color = Soluk, fontSize = 12.sp
            )
        }
    }
}
