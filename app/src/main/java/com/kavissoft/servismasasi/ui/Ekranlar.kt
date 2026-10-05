package com.kavissoft.servismasasi.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.provider.OpenableColumns
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.kavissoft.servismasasi.BuildConfig
import com.kavissoft.servismasasi.Durum
import com.kavissoft.servismasasi.Ekran
import com.kavissoft.servismasasi.Modul
import com.kavissoft.servismasasi.Moduller
import com.kavissoft.servismasasi.metin
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

@Composable
fun Uygulama(d: Durum, activity: FragmentActivity) {
    MaterialTheme(colorScheme = Renkler, typography = Yazilar) {
        Box(Modifier.fillMaxSize().background(Zemin).safeDrawingPadding()) {
            when (d.ekran) {
                Ekran.YUKLENIYOR -> Ortala { CircularProgressIndicator(color = Birincil) }
                Ekran.ENGEL -> EngelEkrani(d)
                Ekran.GUNCELLE -> GuncelleEkrani(d)
                Ekran.FIRMA -> FirmaEkrani(d)
                Ekran.GIRIS -> GirisEkrani(d)
                Ekran.KILIT -> KilitEkrani(d, activity)
                Ekran.ANA -> Kabuk(d) { AnaEkran(d) }
                Ekran.LISTE -> Kabuk(d) { ListeEkrani(d) }
                Ekran.FORM -> Kabuk(d) { FormEkrani(d) }
                Ekran.DETAY -> Kabuk(d) { DetayEkrani(d) }
                Ekran.TOPLU -> Kabuk(d) { TopluEkrani(d) }
                Ekran.SOHBET -> Kabuk(d) { SohbetEkrani(d) }
            }
        }
    }
}

@Composable
internal fun Ortala(icerik: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icerik() }
}

/** Giriş öncesi ekranların ortak düzeni (web giriş sayfasındaki gibi: logo, başlık ve ortada kart) */
@Composable
private fun Form(baslik: String, alt: String, ikon: String = "screwdriver-wrench", icerik: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val bicim = RoundedCornerShape(18.dp)
        Box(Modifier.size(64.dp).shadow(10.dp, bicim, ambientColor = Color(0x331A6CF5), spotColor = Color(0x551A6CF5)).background(Gecis, bicim), contentAlignment = Alignment.Center) {
            Ikon(ikon, Color.White, 26.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(baslik, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Koyu, textAlign = TextAlign.Center, letterSpacing = (-0.4).sp)
        if (alt.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(alt, fontSize = 14.sp, color = Soluk, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(22.dp))
        Kart(ic = 20.dp) { icerik() }
        if (BuildConfig.SSLSIZ) {
            Spacer(Modifier.height(16.dp))
            Text("Deneme sürümü: bağlantı şifresizdir. Sunucuya SSL kurulunca güvenli sürüme geçilmelidir.", color = Kirmizi, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(10.dp))
        Text("Sürüm " + BuildConfig.VERSION_NAME, color = Soluk, fontSize = 12.sp)
    }
}

@Composable
internal fun AnaDugme(yazi: String, mesgul: Boolean, ikon: String = "", sonIkon: String = "", tikla: () -> Unit) {
    Dugme(yazi, ikon = ikon, sonIkon = sonIkon, birincil = true, mesgul = mesgul, modifier = Modifier.fillMaxWidth(), tikla = tikla)
}

@Composable
private fun EngelEkrani(d: Durum) {
    Form("Uygulama açılamıyor", "", "shield-halved") {
        Hata(d.mesaj)
        Dugme("Tekrar dene", ikon = "rotate", etkin = !d.mesgul, modifier = Modifier.fillMaxWidth()) { d.baslat() }
    }
}

/** Yeni sürüm uyarısı: uygulamaya girerken sorulur; onaylanınca indirilir ve kurulur. */
@Composable
private fun GuncelleEkrani(d: Durum) {
    val baglam = LocalContext.current
    val alt = if (d.guncelleZorunlu) "Bu sürüm (${d.surum}) artık desteklenmiyor. Devam etmek için uygulamanın güncellenmesi gerekiyor."
    else "Uygulamanın yeni bir sürümü yayınlandı. Şimdi güncellemek ister misiniz?"
    Form(if (d.mesgul) "Güncelleniyor" else "Yeni sürüm hazır", alt, "download") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Yüklü sürüm", color = Soluk, fontSize = 12.sp)
                Text(d.surum, color = Koyu, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Ikon("arrow-right", Soluk, 14.sp)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Yeni sürüm", color = Soluk, fontSize = 12.sp)
                Text(d.yeniSurum, color = Birincil, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (d.guncelleMetin.isNotEmpty()) {
            Uyari(d.guncelleMetin, "blue", "circle-info")
            Spacer(Modifier.height(12.dp))
        }
        Hata(d.mesaj)
        AnaDugme("Güncelle", d.mesgul, ikon = "download") { d.guncelleBaslat() }
        if (!d.guncelleZorunlu) {
            Spacer(Modifier.height(10.dp))
            Dugme("Sonra", etkin = !d.mesgul, modifier = Modifier.fillMaxWidth()) { d.guncelleErtele() }
        }
        if (!d.mesgul && d.mesaj.isNotEmpty() && d.guncelleAdres.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { runCatching { baglam.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.guncelleAdres))) } }, modifier = Modifier.fillMaxWidth()) { Text("Tarayıcıdan indir") }
        }
    }
}

@Composable
private fun FirmaEkrani(d: Durum) {
    var no by rememberSaveable { mutableStateOf("") }
    Form("Servis Masası", "Başlamak için firma numaranızı yazın. Numarayı bilmiyorsanız yöneticinize sorun.") {
        Hata(d.mesaj)
        Girdi("Firma No", no, { no = it.take(20) }, ipucu = "Örn. SM-482913", ikon = "building", klavye = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
        Spacer(Modifier.height(16.dp))
        AnaDugme("Devam", d.mesgul, sonIkon = "arrow-right") { d.firmaSec(no) }
    }
}

@Composable
private fun GirisEkrani(d: Durum) {
    var email by rememberSaveable { mutableStateOf("") }
    var parola by remember { mutableStateOf("") }   // parola kalıcı durumda tutulmaz
    var kod by remember { mutableStateOf("") }
    Form(d.firmaAd.ifEmpty { "Giriş" }, "Hesabınızla giriş yapın") {
        Hata(d.mesaj)
        Girdi("E-posta adresi", email, { email = it.take(120) }, ipucu = "ornek@firma.com", ikon = "envelope", klavye = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(14.dp))
        Girdi("Parola", parola, { parola = it.take(200) }, ipucu = "••••••••", ikon = "lock", klavye = KeyboardOptions(keyboardType = KeyboardType.Password), gorunum = PasswordVisualTransformation())
        if (d.kodGerekli) {
            Spacer(Modifier.height(14.dp))
            Girdi("Doğrulama kodu", kod, { kod = it.take(10) }, ipucu = "000 000", ikon = "shield-halved", klavye = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Spacer(Modifier.height(18.dp))
        AnaDugme("Giriş Yap", d.mesgul, sonIkon = "arrow-right") { d.giris(email, parola, kod) }
        Spacer(Modifier.height(10.dp))
        if (d.biyometrikAcik) {
            Dugme("Parmak izi ile gir", ikon = "fingerprint", etkin = !d.mesgul, modifier = Modifier.fillMaxWidth()) { d.baslat() }
            Spacer(Modifier.height(10.dp))
        }
        TextButton(onClick = { d.firmaDegistir() }, enabled = !d.mesgul, modifier = Modifier.fillMaxWidth()) { Text("Firmayı değiştir") }
    }
}

@Composable
private fun KilitEkrani(d: Durum, activity: FragmentActivity) {
    var denendi by remember { mutableStateOf(false) }
    LaunchedEffect(d.mesgul) {
        if (!d.mesgul && !denendi) {
            denendi = true
            d.kilitAc(activity)
        }
    }
    Form(d.firmaAd.ifEmpty { "Servis Masası" }, "Devam etmek için kimliğinizi doğrulayın", "fingerprint") {
        Hata(d.mesaj)
        AnaDugme("Parmak izi / yüz ile aç", d.mesgul, ikon = "fingerprint") { d.kilitAc(activity) }
        Spacer(Modifier.height(10.dp))
        Dugme("Parola ile gir", ikon = "key", etkin = !d.mesgul, modifier = Modifier.fillMaxWidth()) { d.parolaIleGir() }
    }
}

/** Menüdeki bir bölümün simgesi (web panelindeki menü simgeleri) */
internal fun modulIkon(kod: String): String = when (kod) {
    "servis" -> "screwdriver-wrench"
    "musteri" -> "users"
    "parca" -> "boxes-stacked"
    "muhasebe" -> "circle-dollar-to-slot"
    "takvim" -> "calendar-days"
    "teknisyen" -> "user-gear"
    "cek" -> "money-check"
    "kredi" -> "landmark"
    "garanti" -> "shield-halved"
    "odeme" -> "file-invoice-dollar"
    "taksit" -> "credit-card"
    "dis", "io" -> "building"
    "onay" -> "circle-check"
    "bakim" -> "bell"
    "aksesuar", "sonuc", "cihaz", "marka" -> "list-ul"
    else -> "bell-concierge"
}

private class AltOge(val baslik: String, val modulKod: String, val rozet: Int = 0, val git: () -> Unit)
private class MenuOge(val baslik: String, val ikon: String, val kodlar: List<String>, val alt: List<AltOge> = emptyList(), val git: (() -> Unit)? = null)

/**
 * Uygulama kabuğu: web panelindeki gibi üstte çubuk, soldan açılan menü ve içerik.
 */
@Composable
private fun Kabuk(d: Durum, icerik: @Composable () -> Unit) {
    val cekmece = rememberDrawerState(DrawerValue.Closed)
    val kapsam = rememberCoroutineScope()
    var hesap by remember { mutableStateOf(false) }
    val kapat: () -> Unit = { kapsam.launch { cekmece.close() } }
    BackHandler(enabled = cekmece.isOpen) { kapat() }
    ModalNavigationDrawer(
        drawerState = cekmece,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = KartRenk, drawerShape = RoundedCornerShape(0.dp), modifier = Modifier.width(284.dp)) {
                Menu(d, kapat)
            }
        }
    ) {
        Column(Modifier.fillMaxSize().background(Zemin)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IkonDugme("bars") { kapsam.launch { cekmece.open() } }
                Spacer(Modifier.width(12.dp))
                Text(d.firmaAd.ifEmpty { "Servis Masası" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Koyu, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IkonDugme("rotate", etkin = !d.mesgul) { if (d.ekran == Ekran.LISTE) d.listeYenile() else if (d.ekran == Ekran.ANA) d.yenile() else if (d.ekran == Ekran.DETAY) d.detay?.optInt("id")?.let { d.servisAc(it) } }
                Spacer(Modifier.width(10.dp))
                Avatar(d.kullanici?.metin("adsoyad") ?: "") { hesap = true }
            }
            if (d.mesgul) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Birincil, trackColor = Zemin2) else Spacer(Modifier.height(2.dp))
            Box(Modifier.fillMaxSize()) {
                icerik()
                // Canlı destek düğmesi (web panelindeki gibi sağ altta)
                if (d.destekVar && d.ekran == Ekran.ANA) Box(Modifier.align(Alignment.BottomEnd).padding(18.dp)) {
                    Row(
                        Modifier.shadow(8.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x331A6CF5), spotColor = Color(0x4D1A6CF5)).background(Gecis, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp)).clickable { d.destekAc() }.padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Ikon("headset", Color.White, 16.sp)
                        Spacer(Modifier.width(9.dp))
                        Text("Canlı Destek", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (d.destekDavet) Box(Modifier.align(Alignment.TopEnd).size(13.dp).background(Kirmizi, RoundedCornerShape(7.dp)))
                }
            }
        }
    }
    d.popuplar.firstOrNull()?.let { p -> PopupPenceresi(p) { d.popupKapat() } }
    if (hesap) {
        AlertDialog(
            onDismissRequest = { hesap = false },
            title = { Text(d.kullanici?.metin("adsoyad") ?: "Hesap") },
            text = {
                Column {
                    Text(d.kullanici?.metin("email") ?: "", color = Soluk, fontSize = 13.sp)
                    if (d.firmaNo.isNotEmpty()) Text("Firma No: ${d.firmaNo}", color = Koyu, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Sürüm ${d.surum}", color = Soluk, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    if (d.biyometrikAcik) Dugme("Parmak izi ile girişi kapat", ikon = "fingerprint", modifier = Modifier.fillMaxWidth()) { hesap = false; d.biyometrikKapat() }
                    else Dugme("Parmak izi ile girişi aç", ikon = "fingerprint", modifier = Modifier.fillMaxWidth()) { hesap = false; d.biyometrikAc() }
                    Spacer(Modifier.height(10.dp))
                    Dugme("Çıkış yap", ikon = "right-from-bracket", tehlike = true, modifier = Modifier.fillMaxWidth()) { hesap = false; d.cikis() }
                }
            },
            confirmButton = { TextButton(onClick = { hesap = false }) { Text("Kapat") } }
        )
    }
    if (d.biyometrikTeklif) {
        AlertDialog(
            onDismissRequest = { d.biyometrikTeklif = false },
            title = { Text("Parmak izi ile giriş") },
            text = { Text("Sonraki girişlerde parola yazmadan parmak iziniz ya da yüzünüzle girmek ister misiniz?") },
            confirmButton = { TextButton(onClick = { d.biyometrikAc() }) { Text("Evet, aç") } },
            dismissButton = { TextButton(onClick = { d.biyometrikTeklif = false }) { Text("Şimdi değil") } }
        )
    }
}

/** Soldan açılan menü: web panelindeki menünün aynı sırası ve başlıkları (uygulamada bulunan bölümler). */
@Composable
private fun Menu(d: Durum, kapat: () -> Unit) {
    val p = d.panel
    val izin = p?.optJSONObject("moduller")
    val ozet = p?.optJSONObject("ozet")
    fun acik(ad: String) = izin == null || izin.optBoolean(ad, false)
    fun say(ad: String) = ozet?.optInt(ad, 0) ?: 0
    fun liste(m: Modul, ek: Map<String, String> = emptyMap(), baslik: String = m.baslik): () -> Unit = { kapat(); d.modulAc(m, ek, baslik) }
    fun ekle(m: Modul): () -> Unit = { kapat(); d.yeniKayit(m) }

    val ogeler = mutableListOf(MenuOge("Ana Sayfa", "house", emptyList(), git = { kapat(); d.anaSayfa() }))
    if (acik("servis")) {
        ogeler.add(
            MenuOge(
                "Servis İşlemleri", "screwdriver-wrench", listOf("servis", "onay", "bakim", "aksesuar", "sonuc"),
                listOf(
                    AltOge("Yeni Servis Kaydı", "servis", git = ekle(Moduller.servis)),
                    AltOge("Servis Kayıtları", "servis", git = liste(Moduller.servis)),
                    AltOge("Serviste", "servis", git = liste(Moduller.servis, mapOf("durum" to "10"), "Serviste")),
                    AltOge("Müşteri Onayında", "servis", git = liste(Moduller.servis, mapOf("durum" to "20"), "Müşteri Onayında")),
                    AltOge("Parça Temininde", "servis", git = liste(Moduller.servis, mapOf("durum" to "30"), "Parça Temininde")),
                    AltOge("Onarım Sırasında", "servis", git = liste(Moduller.servis, mapOf("durum" to "40"), "Onarım Sırasında")),
                    AltOge("Teslime Hazır", "servis", git = liste(Moduller.servis, mapOf("durum" to "50"), "Teslime Hazır")),
                    AltOge("Teslim Edilenler", "servis", git = liste(Moduller.servis, mapOf("durum" to "60"), "Teslim Edilenler")),
                    AltOge("Gecikenler", "servis", git = liste(Moduller.servis, mapOf("gecikmis" to "1"), "Gecikenler")),
                    AltOge("Onay Talepleri", "onay", git = liste(Moduller.onay)),
                    AltOge("Bakım Hatırlatmaları", "bakim", git = liste(Moduller.bakim)),
                    AltOge("İşlem Sonuçları", "sonuc", git = liste(Moduller.islemSonucu)),
                    AltOge("Aksesuarlar", "aksesuar", git = liste(Moduller.aksesuar)),
                    AltOge("Toplu Kayıt", "toplu", git = { kapat(); d.topluAc() }),
                    AltOge("Servis Ayarları", "ayar", git = { kapat(); d.ayarAc(Moduller.servisAyarlari, "ayarlar_servis") })
                )
            )
        )
        ogeler.add(
            MenuOge(
                "Dış Servis", "building", listOf("dis", "io"),
                listOf(
                    AltOge("Yeni Dış Servis Kaydı", "dis", git = ekle(Moduller.disServis)),
                    AltOge("Dış Servis Kayıtları", "dis", git = liste(Moduller.disServis)),
                    AltOge("Serviste (Dış)", "dis", git = liste(Moduller.disServis, mapOf("durum" to "acik"), "Serviste (Dış)")),
                    AltOge("İş Ortakları", "io", git = liste(Moduller.isOrtagi)),
                    AltOge("Yeni İş Ortağı", "io", git = ekle(Moduller.isOrtagi))
                )
            )
        )
    }
    if (acik("musteri")) ogeler.add(MenuOge("Müşteriler", "users", listOf("musteri"), listOf(AltOge("Müşteri Ekle", "musteri", git = ekle(Moduller.musteri)), AltOge("Müşteri Listesi", "musteri", git = liste(Moduller.musteri)))))
    if (izin == null || izin.optBoolean("cihaz", acik("servis"))) ogeler.add(MenuOge("Cihazlar", "laptop", listOf("cihaz", "marka"), listOf(AltOge("Cihaz Türleri", "cihaz", git = liste(Moduller.cihazTuru)), AltOge("Markalar", "marka", git = liste(Moduller.marka)))))
    if (acik("parca")) ogeler.add(MenuOge("Stok / Parçalar", "boxes-stacked", listOf("parca"), listOf(AltOge("Yeni Parça Ekle", "parca", git = ekle(Moduller.stok)), AltOge("Parça Listesi", "parca", say("stok_kritik"), liste(Moduller.stok)))))
    if (acik("muhasebe")) ogeler.add(MenuOge("Finans", "circle-dollar-to-slot", listOf("muhasebe"), listOf(AltOge("Gelir / Gider Ekle", "muhasebe", git = ekle(Moduller.gelirGider)), AltOge("Gelir / Gider Listesi", "muhasebe", git = liste(Moduller.gelirGider)))))
    if (acik("takvim")) ogeler.add(MenuOge("Takvim / Randevu", "calendar-days", listOf("takvim"), git = liste(Moduller.randevu)))
    if (acik("hatirlatici")) ogeler.add(
        MenuOge(
            "Hatırlatıcı", "bell-concierge", listOf("hatirlatici", "odeme", "garanti", "alacak", "cek", "kredi", "taksit"),
            listOf(
                AltOge("Hatırlatıcılar", "hatirlatici", say("hatirlatici_geciken") + say("hatirlatici_bugun"), liste(Moduller.hatirlatici)),
                AltOge("Yeni Hatırlatıcı", "hatirlatici", git = ekle(Moduller.hatirlatici)),
                AltOge("Ödeme Takibi", "odeme", say("odeme_hafta"), liste(Moduller.odeme)),
                AltOge("Garanti Takibi", "garanti", say("garanti_yakin"), liste(Moduller.garanti)),
                AltOge("Alacak / Verecek", "alacak", say("av_geciken"), liste(Moduller.alacak)),
                AltOge("Çek / Senet", "cek", say("cek_geciken") + say("cek_hafta"), liste(Moduller.cek)),
                AltOge("Kredi Ödemeleri", "kredi", say("kredi_hafta"), liste(Moduller.kredi))
            )
        )
    )
    if (izin != null && izin.optBoolean("teknisyen", false)) ogeler.add(MenuOge("Personel", "user-gear", listOf("teknisyen"), listOf(AltOge("Personel Ekle", "teknisyen", git = ekle(Moduller.personel)), AltOge("Personel Listesi", "teknisyen", git = liste(Moduller.personel)))))

    ogeler.add(MenuOge("Ekip Sohbeti", "comments", listOf("sohbet-ekip"), git = { kapat(); d.sohbetAc() }))
    if (d.destekVar) ogeler.add(MenuOge("Canlı Destek", "headset", listOf("sohbet-destek"), git = { kapat(); d.destekAc() }))

    val etkinKod = if (d.ekran == Ekran.ANA) "" else if (d.ekran == Ekran.SOHBET) "sohbet-" + d.sohbetTur else (d.modul?.kod ?: "")
    var acikGrup by remember { mutableStateOf(ogeler.firstOrNull { etkinKod in it.kodlar }?.baslik ?: "") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Gecis, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Ikon("screwdriver-wrench", Color.White, 17.sp) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Servis Masası", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Koyu)
                Text(d.firmaAd, fontSize = 12.sp, color = Soluk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (d.firmaNo.isNotEmpty()) Text("Firma No: ${d.firmaNo}", fontSize = 12.sp, color = Birincil, fontWeight = FontWeight.SemiBold)
            }
        }
        ogeler.forEach { o ->
            val etkin = if (o.kodlar.isEmpty()) d.ekran == Ekran.ANA else etkinKod in o.kodlar
            val bicim = RoundedCornerShape(12.dp)
            val toplamRozet = o.alt.sumOf { it.rozet }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    .then(if (etkin) Modifier.shadow(6.dp, bicim, ambientColor = Color(0x331A6CF5), spotColor = Color(0x4D1A6CF5)).background(Gecis, bicim) else Modifier)
                    .clip(bicim).clickable { if (o.alt.isEmpty()) o.git?.invoke() else acikGrup = if (acikGrup == o.baslik) "" else o.baslik }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ikon(o.ikon, if (etkin) Color.White else Color(0xFF4A5B78), 16.sp, Modifier.width(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(o.baslik, color = if (etkin) Color.White else Yazi2, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (toplamRozet > 0 && !etkin) { Rozet(toplamRozet.toString(), "red"); Spacer(Modifier.width(6.dp)) }
                if (o.alt.isNotEmpty()) Ikon(if (acikGrup == o.baslik) "chevron-down" else "chevron-right", if (etkin) Color.White else Soluk, 10.sp)
            }
            if (o.alt.isNotEmpty() && acikGrup == o.baslik) {
                Column(Modifier.padding(start = 26.dp, top = 2.dp, bottom = 6.dp)) {
                    o.alt.forEach { a ->
                        val altEtkin = etkin && a.modulKod == etkinKod && a.baslik == d.listeBaslik
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable(onClick = a.git).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).background(if (altEtkin) Birincil else Cizgi2, RoundedCornerShape(3.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text(a.baslik, color = if (altEtkin) Birincil else Yazi2, fontSize = 13.5.sp, fontWeight = if (altEtkin) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            if (a.rozet > 0) Rozet(a.rozet.toString(), "red")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Cizgi)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { kapat(); d.cikis() }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Ikon("right-from-bracket", Kirmizi, 15.sp, Modifier.width(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("Çıkış Yap", color = Kirmizi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text("Sürüm ${d.surum}", color = Soluk, fontSize = 11.5.sp, modifier = Modifier.padding(start = 14.dp, top = 6.dp))
    }
}

/** Etiket – değer satırı (web: .kv) */
@Composable
internal fun Satir(ad: String, deger: String) {
    if (deger.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(ad, color = Soluk, fontSize = 13.sp, modifier = Modifier.width(112.dp))
        Text(deger, color = Koyu, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

internal fun durumTonu(kod: Int): String = when (kod) {
    10 -> "blue"
    20 -> "teal"
    30 -> "slate"
    40 -> "purple"
    50 -> "orange"
    60 -> "green"
    70 -> "red"
    else -> "slate"
}

/** Servis kaydı ayrıntısı: web panelindeki servis detay sayfasının bölümleri ve işlemleri. */
@Composable
private fun DetayEkrani(d: Durum) {
    BackHandler { d.detayKapat() }
    val s = d.detay ?: return
    val tam = d.detayTam
    val baglam = LocalContext.current
    var onay by remember { mutableStateOf<Pair<String, Map<String, String>>?>(null) }
    var silOnay by remember { mutableStateOf(false) }
    val galeri = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { adres ->
        if (adres != null) fotoKucult { secenek ->
            val resim = baglam.contentResolver.openInputStream(adres)?.use { BitmapFactory.decodeStream(it, null, secenek) }
            val exif = runCatching { baglam.contentResolver.openInputStream(adres)?.use { ExifInterface(it) } }.getOrNull()
            resim to exif
        }?.let { d.servisFoto(it) }
    }
    var fotoDosya by remember { mutableStateOf<File?>(null) }
    val izinle = kameraIzni()
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { cekildi ->
        val f = fotoDosya
        if (cekildi && f != null) fotoKucult { BitmapFactory.decodeFile(f.absolutePath, it) to runCatching { ExifInterface(f.absolutePath) }.getOrNull() }?.let { d.servisFoto(it) }
        runCatching { f?.delete() }
        fotoDosya = null
    }
    fun dizi(ad: String): List<JSONObject> = tam?.optJSONArray(ad).let { a -> if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
    val dis = s.optBoolean("dis")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SayfaBasi((if (dis) "Dış Servis " else "Servis ") + s.metin("takip_kodu"), "Kayıt: " + s.metin("olusturma_yazi").ifEmpty { s.metin("olusturma") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Dugme("Geri", ikon = "arrow-left", kucuk = true) { d.detayKapat() }
                    Dugme("Düzenle", ikon = "pen-to-square", kucuk = true, etkin = !d.mesgul) { d.servisDuzenle() }
                    Rozet(s.metin("durum_ad"), durumTonu(s.optInt("durum")))
                }
            }
        }
        if (d.bilgi.isNotEmpty()) item { Uyari(d.bilgi, "green", "circle-check") }
        if (d.mesaj.isNotEmpty()) item { Uyari(d.mesaj, "red") }
        item {
            Kart {
                KartBas("İşlemler", "Kayıtla ilgili yapılabilecek işlemler", "gear", "blue")
                val eylemler = Moduller.servisEylemleri.filter { it.kosul(s) }
                eylemler.chunked(2).forEachIndexed { i, cift ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cift.forEach { e -> Dugme(e.etiket, ikon = e.ikon, kucuk = true, birincil = e.etiket == "Durum Değiştir", etkin = !d.mesgul, modifier = Modifier.weight(1f)) { d.eylemYap(e, s) } }
                        if (cift.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Kart {
                KartBas(if (dis) "Firma / Müşteri" else "Müşteri", "", "user", "blue")
                if (dis) {
                    Satir("Firma", s.metin("firma"))
                    Satir("Dış servis no", s.metin("firma_is_emri"))
                    Satir("Son kullanıcı", s.metin("son_kullanici"))
                    Satir("Firma tespiti", s.metin("firma_tespit"))
                    Satir("Geliş şekli", listOf(s.metin("gelis_ad"), s.metin("gelis_kargo")).filter { it.isNotEmpty() }.joinToString(" · "))
                    Satir("Hedef tarih", s.metin("hedef_yazi"))
                    Satir("Dönüş kargo", s.metin("donus_kargo"))
                }
                Satir("Ad", s.metin("musteri_adi"))
                Satir("Telefon", s.metin("musteri_tel"))
                if (s.metin("musteri_tel").isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Dugme("Ara", ikon = "phone", kucuk = true) { runCatching { baglam.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + s.metin("musteri_tel").filter { it.isDigit() || it == '+' }))) } }
                }
            }
        }
        item {
            Kart {
                KartBas("Cihaz", "", "laptop", "purple")
                Satir("Tür", s.metin("cihaz_adi"))
                Satir("Marka", s.metin("marka_adi"))
                Satir("Model", s.metin("model"))
                Satir("Seri no", s.metin("seri_no"))
                Satir("Aksesuar", s.metin("aksesuar"))
                Satir("Dış görünüm", s.metin("dis_gorunum"))
                Satir("Garanti", s.metin("garanti_ad"))
                Satir("Öncelik", s.metin("oncelik_ad"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.optBoolean("resim_var")) Dugme("Fotoğrafı gör", ikon = "image", kucuk = true, etkin = !d.mesgul, modifier = Modifier.weight(1f)) { d.servisFotoAc() }
                    Dugme("Fotoğraf çek", ikon = "camera", kucuk = true, etkin = !d.mesgul, modifier = Modifier.weight(1f)) {
                        izinle { runCatching {
                            val f = File(File(baglam.cacheDir, "foto").apply { mkdirs() }, "servis.jpg")
                            fotoDosya = f
                            kamera.launch(FileProvider.getUriForFile(baglam, baglam.packageName + ".dosya", f))
                        } }
                    }
                    Dugme("Galeriden", ikon = "image", kucuk = true, etkin = !d.mesgul, modifier = Modifier.weight(1f)) { runCatching { galeri.launch("image/*") } }
                }
            }
        }
        item {
            Kart {
                KartBas("Servis", "", "screwdriver-wrench", "orange")
                Satir("Arıza", s.metin("ariza"))
                Satir("Yapılan işlem", s.metin("yapilan_islem"))
                Satir("Teknisyen notu", s.metin("teknisyen_notu"))
                Satir("İşlem sonucu", s.metin("onarim_sonuc"))
                Satir("Teknisyen", s.metin("teknisyen_adi"))
                Satir("Kabul eden", s.metin("kabul_eden"))
                Satir("Teslim tarihi", s.metin("teslim_tarihi"))
            }
        }
        val parcalar = dizi("parcalar")
        item {
            Kart {
                KartBas("Parçalar", if (parcalar.isEmpty()) "Henüz parça eklenmedi" else "Kaldırmak için parçaya dokunun", "cube", "teal")
                parcalar.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(color = Cizgi)
                    Row(Modifier.fillMaxWidth().clickable(enabled = !d.mesgul) { onay = ("\"" + p.metin("ad") + "\" kaldırılsın ve stoğa iade edilsin mi?") to mapOf("islem" to "parca_sil", "sp" to p.optInt("id").toString()) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.metin("ad"), fontWeight = FontWeight.SemiBold, color = Koyu, fontSize = 14.sp)
                            Text("${p.optInt("adet")} adet × ${p.metin("fiyat_yazi")}", color = Soluk, fontSize = 12.sp)
                        }
                        Text(p.metin("tutar_yazi"), fontWeight = FontWeight.Bold, color = Koyu, fontSize = 14.sp)
                    }
                }
            }
        }
        if (dis) {
            val iscilikler = dizi("iscilikler")
            val masraflar = dizi("masraflar")
            if (iscilikler.isNotEmpty()) item {
                Kart {
                    KartBas("İşçilik Kalemleri", "Silmek için kaleme dokunun", "screwdriver-wrench", "purple")
                    iscilikler.forEachIndexed { i, k ->
                        if (i > 0) HorizontalDivider(color = Cizgi)
                        Row(Modifier.fillMaxWidth().clickable(enabled = !d.mesgul) { onay = "İşçilik kalemi silinsin mi?" to mapOf("islem" to "iscilik_sil", "kid" to k.optInt("id").toString()) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(k.metin("aciklama"), fontWeight = FontWeight.SemiBold, color = Koyu, fontSize = 14.sp)
                                if (k.optInt("sure_dk") > 0) Text("${k.optInt("sure_dk")} dakika", color = Soluk, fontSize = 12.sp)
                            }
                            Text(k.metin("ucret_yazi"), fontWeight = FontWeight.Bold, color = Koyu, fontSize = 14.sp)
                        }
                    }
                }
            }
            if (masraflar.isNotEmpty()) item {
                Kart {
                    KartBas("Ek Masraflar", "Silmek için masrafa dokunun", "file-invoice-dollar", "orange")
                    masraflar.forEachIndexed { i, k ->
                        if (i > 0) HorizontalDivider(color = Cizgi)
                        Row(Modifier.fillMaxWidth().clickable(enabled = !d.mesgul) { onay = "Masraf silinsin mi?" to mapOf("islem" to "masraf_sil", "mid" to k.optInt("id").toString()) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(listOf(k.metin("tur_ad"), k.metin("aciklama")).filter { it.isNotEmpty() }.joinToString(" · "), fontWeight = FontWeight.SemiBold, color = Koyu, fontSize = 14.sp)
                                Text(listOf(k.metin("tedarikci"), if (k.metin("maliyet_yazi").isEmpty()) "" else "Maliyet: " + k.metin("maliyet_yazi")).filter { it.isNotEmpty() }.joinToString(" · "), color = Soluk, fontSize = 12.sp)
                            }
                            Text(k.metin("yansit_yazi"), fontWeight = FontWeight.Bold, color = Koyu, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        item {
            Kart {
                KartBas("Ücret", "", "circle-dollar-to-slot", "green")
                Satir("Parça", s.metin("parca_ucreti_yazi"))
                Satir("İşçilik", s.metin("iscilik_yazi"))
                Satir("İnceleme", s.metin("inceleme_ucreti_yazi"))
                Satir("Taşıma", s.metin("tasima_ucreti_yazi"))
                Satir("İndirim", s.metin("indirim_yazi"))
                HorizontalDivider(color = Cizgi, modifier = Modifier.padding(vertical = 6.dp))
                Satir("Toplam", s.metin("toplam_yazi").ifEmpty { s.metin("toplam") })
                Satir("Ödenen", s.metin("odenen_yazi").ifEmpty { s.metin("odenen") })
                Satir("Kalan", s.metin("kalan_yazi"))
                dizi("tahsilatlar").forEach { t ->
                    HorizontalDivider(color = Cizgi)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.metin("aciklama").ifEmpty { "Tahsilat" }, color = Koyu, fontSize = 13.sp)
                            Text(t.metin("tarih_yazi"), color = Soluk, fontSize = 11.5.sp)
                        }
                        Rozet(t.metin("tutar_yazi"), "green")
                    }
                }
            }
        }
        val onaylar = dizi("onaylar")
        if (onaylar.isNotEmpty()) item {
            Kart {
                KartBas("Müşteri Onayları", "Silmek için talebe dokunun", "circle-check", "teal")
                onaylar.forEachIndexed { i, o ->
                    if (i > 0) HorizontalDivider(color = Cizgi)
                    Row(Modifier.fillMaxWidth().clickable(enabled = !d.mesgul) { onay = "Onay talebi silinsin mi?" to mapOf("islem" to "onay_sil", "oid" to o.optInt("id").toString()) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(listOf(o.metin("tur_ad"), o.metin("fiyat_yazi")).filter { it.isNotEmpty() }.joinToString(" · "), fontWeight = FontWeight.SemiBold, color = Koyu, fontSize = 14.sp)
                            if (o.metin("aciklama").isNotEmpty()) Text(o.metin("aciklama"), color = Yazi2, fontSize = 12.5.sp)
                            Text(o.metin("tarih_yazi"), color = Soluk, fontSize = 11.5.sp)
                        }
                        Rozet(o.metin("durum_ad"), when (o.optInt("durum")) { 1 -> "green"; 2 -> "red"; else -> "orange" })
                    }
                }
            }
        }
        val bakimlar = dizi("hatirlaticilar")
        if (bakimlar.isNotEmpty()) item {
            Kart {
                KartBas("Bakım Hatırlatıcıları", "", "bell", "orange")
                bakimlar.forEach { h -> Satir(h.metin("tarih_yazi"), h.metin("aciklama").ifEmpty { if (h.optInt("durum") == 1) "Tamamlandı" else "Bekliyor" }) }
            }
        }
        if (d.gecmis.isNotEmpty()) item {
            Kart {
                KartBas("Geçmiş", "Durum değişiklikleri", "clock", "slate")
                d.gecmis.forEachIndexed { i, g ->
                    if (i > 0) HorizontalDivider(color = Cizgi)
                    Column(Modifier.padding(vertical = 9.dp)) {
                        Text(g.metin("durum_ad"), fontWeight = FontWeight.SemiBold, color = Koyu, fontSize = 14.sp)
                        if (g.metin("aciklama").isNotEmpty()) Text(g.metin("aciklama"), color = Yazi2, fontSize = 13.sp)
                        Text(listOf(g.metin("olusturma"), g.metin("kullanici")).filter { it.isNotEmpty() }.joinToString(" · "), color = Soluk, fontSize = 11.5.sp)
                    }
                }
            }
        }
        item { Dugme("Servis Kaydını Sil", ikon = "trash", tehlike = true, etkin = !d.mesgul, modifier = Modifier.fillMaxWidth()) { silOnay = true } }
    }
    onay?.let { (soru, govde) ->
        AlertDialog(
            onDismissRequest = { onay = null },
            title = { Text("Emin misiniz?") },
            text = { Text(soru) },
            confirmButton = { TextButton(onClick = { onay = null; d.detayIslem(govde) }) { Text("Evet", color = Kirmizi) } },
            dismissButton = { TextButton(onClick = { onay = null }) { Text("Vazgeç") } }
        )
    }
    if (silOnay) AlertDialog(
        onDismissRequest = { silOnay = false },
        title = { Text("Servis kaydı silinsin mi?") },
        text = { Text("Kayıt; geçmişi, parçaları ve onaylarıyla birlikte kalıcı olarak silinir. Bu işlemi yalnızca yöneticiler yapabilir.") },
        confirmButton = { TextButton(onClick = { silOnay = false; d.detayIslem(mapOf("islem" to "sil"), kapat = true) }) { Text("Sil", color = Kirmizi) } },
        dismissButton = { TextButton(onClick = { silOnay = false }) { Text("Vazgeç") } }
    )
    d.belgeResim?.let { resim ->
        AlertDialog(
            onDismissRequest = { d.belgeResim = null },
            text = { Image(bitmap = resim.asImageBitmap(), contentDescription = "Fotoğraf", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { d.belgeResim = null }) { Text("Kapat") } }
        )
    }
}

/** Toplu Kayıt: CSV / Excel dosyasından çok sayıda servis kaydı ekleme (web panelindeki Toplu Kayıt sayfası). */
@Composable
private fun TopluEkrani(d: Durum) {
    BackHandler { d.anaSayfa() }
    val baglam = LocalContext.current
    val sec = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { adres ->
        if (adres != null) runCatching {
            var ad = "kayitlar.csv"
            baglam.contentResolver.query(adres, null, null, null, null)?.use { c -> val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0 && c.moveToFirst()) ad = c.getString(i) ?: ad }
            val veri = baglam.contentResolver.openInputStream(adres)?.use { it.readBytes() }
            if (veri != null && veri.size <= 8 * 1024 * 1024) d.topluYukle(ad, veri) else d.mesaj = "Dosya okunamadı ya da 8 MB sınırını aşıyor."
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
        SayfaBasi("Toplu Kayıt", "CSV ya da Excel dosyasından birden çok servis kaydı ekleyin.")
        Spacer(Modifier.height(14.dp))
        if (d.bilgi.isNotEmpty()) { Uyari(d.bilgi, "green", "circle-check"); Spacer(Modifier.height(12.dp)) }
        Hata(d.mesaj)
        Kart {
            KartBas("Dosya Yükle", "Sütunlar aşağıdaki sırada olmalıdır", "list-ul", "blue")
            Text("Müşteri · Telefon · E-posta · Cihaz Türü · Marka · Model · Seri No · Arıza · Aksesuar · Dış Görünüm · Kayıt Tarihi (gg.aa.yyyy) · Durum (1-7) · Ücret", color = Yazi2, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("İlk satır başlık satırıdır. Listede olmayan müşteri, cihaz türü ve markalar kendiliğinden eklenir. Örnek dosyayı web panelindeki Toplu Kayıt sayfasından indirebilirsiniz.", color = Soluk, fontSize = 12.5.sp)
            Spacer(Modifier.height(14.dp))
            AnaDugme("Dosya Seç ve Yükle", d.mesgul, ikon = "download") { runCatching { sec.launch("*/*") } }
        }
    }
}
