package com.kavissoft.servismasasi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavissoft.servismasasi.R

/*
 * Tasarım: Servis Masası web panelinin görünümü (aynı yazı tipi, renkler, köşe yuvarlaklıkları, kart ve düğme biçimleri).
 * Değerler web panelinin stil dosyasındaki (app.css) değişkenlerden alınmıştır.
 */

internal val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium), Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold), Font(R.font.inter_extrabold, FontWeight.ExtraBold)
)
private val IkonYazi = FontFamily(Font(R.font.fa_solid, FontWeight.Black))

internal val Birincil = Color(0xFF1A6CF5)
internal val Zemin = Color(0xFFF3F6FB)
internal val Zemin2 = Color(0xFFEAF0F9)
internal val KartRenk = Color(0xFFFFFFFF)
internal val Kart2 = Color(0xFFF8FAFD)
internal val Koyu = Color(0xFF0F1B33)
internal val Yazi2 = Color(0xFF3B4A63)
internal val Soluk = Color(0xFF728099)
internal val Cizgi = Color(0xFFE6EBF3)
internal val Cizgi2 = Color(0xFFD8E0EC)
internal val Mavi = Birincil
internal val Kirmizi = Color(0xFFE03131)
internal val Yesil = Color(0xFF2F9E44)
internal val Gecis = Brush.linearGradient(listOf(Color(0xFF2F80FF), Color(0xFF1466EF)))

/** Renk tonu: simge / yazı rengi ve açık zemin rengi (web: --blue / --blue-bg …) */
internal class Ton(val renk: Color, val zemin: Color)

internal fun ton(ad: String): Ton = when (ad) {
    "slate" -> Ton(Color(0xFF51607A), Color(0xFFEEF2F8))
    "orange" -> Ton(Color(0xFFF08C00), Color(0xFFFFF4E3))
    "purple" -> Ton(Color(0xFF7048E8), Color(0xFFF2EEFF))
    "green" -> Ton(Yesil, Color(0xFFE9F9EE))
    "red" -> Ton(Kirmizi, Color(0xFFFFF0F0))
    "teal" -> Ton(Color(0xFF0C8599), Color(0xFFE6F9FB))
    else -> Ton(Birincil, Color(0xFFEAF2FF))
}

internal fun durumRengi(ad: String): Color = ton(ad).renk

internal val Renkler = lightColorScheme(
    primary = Birincil, onPrimary = Color.White, background = Zemin, onBackground = Koyu, surface = KartRenk, onSurface = Koyu,
    surfaceVariant = Zemin2, onSurfaceVariant = Yazi2, outline = Cizgi2, error = Kirmizi, surfaceContainerHigh = KartRenk, surfaceContainer = KartRenk
)

internal val Yazilar: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Inter), displayMedium = t.displayMedium.copy(fontFamily = Inter), displaySmall = t.displaySmall.copy(fontFamily = Inter),
        headlineLarge = t.headlineLarge.copy(fontFamily = Inter), headlineMedium = t.headlineMedium.copy(fontFamily = Inter),
        headlineSmall = t.headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp),
        titleLarge = t.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Bold), titleMedium = t.titleMedium.copy(fontFamily = Inter), titleSmall = t.titleSmall.copy(fontFamily = Inter),
        bodyLarge = t.bodyLarge.copy(fontFamily = Inter, fontSize = 14.sp), bodyMedium = t.bodyMedium.copy(fontFamily = Inter), bodySmall = t.bodySmall.copy(fontFamily = Inter),
        labelLarge = t.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold), labelMedium = t.labelMedium.copy(fontFamily = Inter), labelSmall = t.labelSmall.copy(fontFamily = Inter)
    )
}

/** Web panelindeki simgeler (Font Awesome) */
private val Simgeler = mapOf(
    "house" to "\uf015",
    "screwdriver-wrench" to "\uf7d9",
    "users" to "\uf0c0",
    "boxes-stacked" to "\uf468",
    "circle-dollar-to-slot" to "\uf4b9",
    "calendar-days" to "\uf073",
    "bell-concierge" to "\uf562",
    "user-gear" to "\uf4fe",
    "bars" to "\uf0c9",
    "magnifying-glass" to "\uf002",
    "plus" to "\u002b",
    "filter" to "\uf0b0",
    "floppy-disk" to "\uf0c7",
    "arrow-right" to "\uf061",
    "arrow-left" to "\uf060",
    "chevron-right" to "\uf054",
    "chevron-down" to "\uf078",
    "desktop" to "\uf390",
    "gear" to "\uf013",
    "box" to "\uf466",
    "clock" to "\uf017",
    "circle-check" to "\uf058",
    "triangle-exclamation" to "\uf071",
    "bullhorn" to "\uf0a1",
    "list-ul" to "\uf0ca",
    "chart-column" to "\ue0e3",
    "calendar-day" to "\uf783",
    "chart-simple" to "\ue473",
    "right-from-bracket" to "\uf2f5",
    "fingerprint" to "\uf577",
    "rotate" to "\uf2f1",
    "pen-to-square" to "\uf044",
    "trash" to "\uf1f8",
    "camera" to "\uf030",
    "image" to "\uf03e",
    "phone" to "\uf095",
    "bell" to "\uf0f3",
    "money-check" to "\uf53c",
    "shield-halved" to "\uf3ed",
    "building" to "\uf1ad",
    "envelope" to "\uf0e0",
    "lock" to "\uf023",
    "user" to "\uf007",
    "xmark" to "\uf00d",
    "download" to "\uf019",
    "hourglass-half" to "\uf252",
    "file-invoice-dollar" to "\uf571",
    "landmark" to "\uf66f",
    "user-plus" to "\uf234",
    "arrow-up" to "\uf062",
    "arrow-down" to "\uf063",
    "cube" to "\uf1b2",
    "id-card" to "\uf2c2",
    "calendar-week" to "\uf784",
    "calendar" to "\uf133",
    "check" to "\uf00c",
    "money-bill-transfer" to "\ue528",
    "credit-card" to "\uf09d",
    "laptop" to "\uf109",
    "key" to "\uf084",
    "circle-info" to "\uf05a",
    "barcode" to "\uf02a",
    "font" to "\uf031",
    "lightbulb" to "\uf0eb",
    "comments" to "\uf086",
    "headset" to "\uf590",
    "paper-plane" to "\uf1d8"
)

@Composable
internal fun Ikon(ad: String, renk: Color = Yazi2, boyut: TextUnit = 16.sp, modifier: Modifier = Modifier) {
    Text(Simgeler[ad] ?: "", fontFamily = IkonYazi, fontWeight = FontWeight.Black, color = renk, fontSize = boyut, textAlign = TextAlign.Center, modifier = modifier)
}

/** Renkli zeminli simge kutusu (web: .ch-ico / .kpi-ico) */
@Composable
internal fun IkonKutu(ikon: String, tonAd: String = "blue", boyut: Dp = 40.dp, dolu: Boolean = false) {
    val t = ton(tonAd)
    Box(Modifier.size(boyut).clip(RoundedCornerShape(12.dp)).background(if (dolu) t.renk else t.zemin), contentAlignment = Alignment.Center) {
        Ikon(ikon, if (dolu) Color.White else t.renk, 18.sp)
    }
}

/** Kart (web: .card) */
@Composable
internal fun Kart(modifier: Modifier = Modifier, ic: Dp = 16.dp, icerik: @Composable () -> Unit) {
    val bicim = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().shadow(6.dp, bicim, ambientColor = Color(0x14102248), spotColor = Color(0x1A102248))
            .background(KartRenk, bicim).border(1.dp, Cizgi, bicim).padding(ic)
    ) { icerik() }
}

/** Kart başlığı (web: .card-head) */
@Composable
internal fun KartBas(baslik: String, alt: String = "", ikon: String = "list-ul", tonAd: String = "blue", sag: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IkonKutu(ikon, tonAd)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(baslik, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Koyu)
            if (alt.isNotEmpty()) Text(alt, fontSize = 12.5.sp, color = Soluk)
        }
        if (sag != null) sag()
    }
}

/** Düğme (web: .btn / .btn-primary / .btn-sm) */
@Composable
internal fun Dugme(
    yazi: String, ikon: String = "", birincil: Boolean = false, kucuk: Boolean = false, tehlike: Boolean = false,
    etkin: Boolean = true, mesgul: Boolean = false, sonIkon: String = "", modifier: Modifier = Modifier, tikla: () -> Unit
) {
    val bicim = RoundedCornerShape(if (kucuk) 9.dp else 11.dp)
    val renk = if (birincil) Color.White else if (tehlike) Kirmizi else Koyu
    val zemin = if (birincil) Modifier.shadow(6.dp, bicim, ambientColor = Color(0x331A6CF5), spotColor = Color(0x4D1A6CF5)).background(Gecis, bicim)
    else Modifier.background(KartRenk, bicim).border(1.dp, Cizgi2, bicim)
    Row(
        modifier.height(if (kucuk) 34.dp else 42.dp).alpha(if (etkin) 1f else 0.55f).then(zemin).clip(bicim).clickable(enabled = etkin && !mesgul, onClick = tikla)
            .padding(horizontal = if (kucuk) 12.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (mesgul) CircularProgressIndicator(Modifier.size(18.dp), color = renk, strokeWidth = 2.dp)
        else {
            if (ikon.isNotEmpty()) { Ikon(ikon, renk, if (kucuk) 12.sp else 14.sp); Spacer(Modifier.width(8.dp)) }
            Text(yazi, color = renk, fontSize = if (kucuk) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sonIkon.isNotEmpty()) { Spacer(Modifier.width(8.dp)); Ikon(sonIkon, renk, 13.sp) }
        }
    }
}

/** Kare simge düğmesi (web: .icon-btn) */
@Composable
internal fun IkonDugme(ikon: String, etkin: Boolean = true, tikla: () -> Unit) {
    val bicim = RoundedCornerShape(11.dp)
    Box(Modifier.size(42.dp).background(KartRenk, bicim).border(1.dp, Cizgi, bicim).clip(bicim).clickable(enabled = etkin, onClick = tikla), contentAlignment = Alignment.Center) {
        Ikon(ikon, Yazi2, 16.sp)
    }
}

/** Rozet (web: .badge) */
@Composable
internal fun Rozet(yazi: String, tonAd: String = "blue") {
    if (yazi.isEmpty()) return
    val t = ton(tonAd)
    Text(yazi, color = t.renk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.background(t.zemin, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Bilgi / uyarı bandı (web: .alert) */
@Composable
internal fun Uyari(yazi: String, tonAd: String = "red", ikon: String = "triangle-exclamation") {
    if (yazi.isEmpty()) return
    val t = ton(tonAd)
    Row(Modifier.fillMaxWidth().background(t.zemin, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Ikon(ikon, t.renk, 15.sp)
        Spacer(Modifier.width(10.dp))
        Text(yazi, color = if (tonAd == "orange") Color(0xFFB35C00) else t.renk, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun Hata(m: String) {
    if (m.isEmpty()) return
    Uyari(m, "red")
    Spacer(Modifier.height(12.dp))
}

/** Alan etiketi (web: .field > label) */
@Composable
internal fun Etiket(yazi: String, zorunlu: Boolean = false) {
    Row {
        Text(yazi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Yazi2)
        if (zorunlu) Text(" *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Kirmizi)
    }
}

/** Metin girişi (web: input) — etiket üstte, 42 yüksekliğinde çerçeveli kutu */
@Composable
internal fun Girdi(
    etiket: String, deger: String, degis: (String) -> Unit, modifier: Modifier = Modifier, ipucu: String = "", zorunlu: Boolean = false, ikon: String = "",
    cokSatir: Boolean = false, klavye: KeyboardOptions = KeyboardOptions.Default, eylem: KeyboardActions = KeyboardActions.Default,
    gorunum: VisualTransformation = VisualTransformation.None
) {
    var odak by remember { mutableStateOf(false) }
    val bicim = RoundedCornerShape(11.dp)
    Column(modifier) {
        if (etiket.isNotEmpty()) { Etiket(etiket, zorunlu); Spacer(Modifier.height(6.dp)) }
        BasicTextField(
            value = deger, onValueChange = degis, singleLine = !cokSatir, minLines = if (cokSatir) 3 else 1,
            textStyle = TextStyle(fontFamily = Inter, fontSize = 14.sp, color = Koyu), keyboardOptions = klavye, keyboardActions = eylem,
            visualTransformation = gorunum, cursorBrush = SolidColor(Birincil),
            modifier = Modifier.fillMaxWidth().onFocusChanged { odak = it.isFocused },
            decorationBox = { ic ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 42.dp).background(KartRenk, bicim).border(if (odak) 1.5.dp else 1.dp, if (odak) Birincil else Cizgi2, bicim)
                        .padding(horizontal = 14.dp, vertical = if (cokSatir) 10.dp else 0.dp),
                    verticalAlignment = if (cokSatir) Alignment.Top else Alignment.CenterVertically
                ) {
                    if (ikon.isNotEmpty()) { Ikon(ikon, Soluk, 14.sp); Spacer(Modifier.width(10.dp)) }
                    Box(Modifier.weight(1f)) {
                        if (deger.isEmpty() && ipucu.isNotEmpty()) Text(ipucu, color = Soluk, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ic()
                    }
                }
            }
        )
    }
}

/** Seçim kutusu (web: select) */
@Composable
internal fun SecimKutusu(etiket: String, deger: String, zorunlu: Boolean = false, ikon: String = "chevron-down", tikla: () -> Unit) {
    val bicim = RoundedCornerShape(11.dp)
    Column(Modifier.fillMaxWidth()) {
        Etiket(etiket, zorunlu)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(42.dp).background(KartRenk, bicim).border(1.dp, Cizgi2, bicim).clip(bicim).clickable(onClick = tikla).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(deger, color = Koyu, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Ikon(ikon, Soluk, 12.sp)
        }
    }
}

/** Sayfa başlığı (web: .page-head) */
@Composable
internal fun SayfaBasi(baslik: String, alt: String = "", sag: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
        Text(baslik, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Koyu, letterSpacing = (-0.4).sp)
        if (alt.isNotEmpty()) Text(alt, fontSize = 14.sp, color = Soluk, modifier = Modifier.padding(top = 2.dp))
        if (sag != null) { Spacer(Modifier.height(12.dp)); sag() }
    }
}

/** Baş harflerden yuvarlak kullanıcı simgesi */
@Composable
internal fun Avatar(ad: String, boyut: Dp = 40.dp, tikla: (() -> Unit)? = null) {
    val harf = ad.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
    Box(Modifier.size(boyut).clip(CircleShape).background(Gecis).then(if (tikla != null) Modifier.clickable(onClick = tikla) else Modifier), contentAlignment = Alignment.Center) {
        Text(harf, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
