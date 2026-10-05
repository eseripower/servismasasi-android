package com.kavissoft.servismasasi.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavissoft.servismasasi.Durum
import com.kavissoft.servismasasi.metin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject

/** Yönetim panelinden gönderilen pop-up: başlık, görsel, metin ve (varsa) bağlantı düğmesi */
@Composable
internal fun PopupPenceresi(p: JSONObject, kapat: () -> Unit) {
    val baglam = LocalContext.current
    val resim = remember(p.optInt("id")) {
        runCatching {
            val veri = p.metin("resim")
            if (!veri.startsWith("data:image/")) null
            else Base64.decode(veri.substringAfter("base64,", ""), Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }.getOrNull()
    }
    val dugme = p.optJSONObject("dugme")
    AlertDialog(
        onDismissRequest = kapat,
        title = { Text(p.metin("baslik").ifEmpty { "Duyuru" }, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (resim != null) {
                    Image(resim.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.height(12.dp))
                }
                Text(p.metin("metin"), color = Yazi2, fontSize = 14.sp)
                if (dugme != null && dugme.metin("url").startsWith("http")) {
                    Spacer(Modifier.height(14.dp))
                    Dugme(dugme.metin("text").ifEmpty { "Aç" }, birincil = true, sonIkon = "arrow-right", modifier = Modifier.fillMaxWidth()) {
                        runCatching { baglam.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dugme.metin("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        kapat()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = kapat) { Text("Kapat") } }
    )
}

/** Ekip sohbeti (kişi listesi + görüşme) ve canlı destek görüşmesi */
@Composable
internal fun SohbetEkrani(d: Durum) {
    BackHandler { d.sohbetGeri() }
    val kanal = d.sohbetKanal
    if (kanal == null) KanalListesi(d) else Gorusme(d, kanal)
}

@Composable
private fun KanalListesi(d: Durum) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SayfaBasi("Ekip Sohbeti", "Ekibinizle yazışın; mesajlar web panelindeki sohbetle ortaktır.") }
        if (d.mesaj.isNotEmpty()) item { Uyari(d.mesaj, "red") }
        items(d.sohbetKanallar) { k ->
            Kart(Modifier.clickable { d.sohbetKanalAc(k) }, ic = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        if (k.optBoolean("grup")) IkonKutu("comments", "blue", 42.dp, dolu = true) else Avatar(k.metin("ad"), 42.dp)
                        if (k.optBoolean("cevrimici")) Box(Modifier.align(Alignment.BottomEnd).size(11.dp).background(Yesil, CircleShape))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(k.metin("ad"), color = Koyu, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val son = k.optJSONObject("son")?.metin("metin").orEmpty()
                        Text(son.ifEmpty { k.metin("alt") }, color = Soluk, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (k.optInt("okunmamis") > 0) Rozet(k.optInt("okunmamis").toString(), "red")
                }
            }
        }
    }
}

@Composable
private fun Gorusme(d: Durum, kanal: JSONObject) {
    val destek = d.sohbetTur == "destek"
    var yazi by remember(kanal.metin("kanal")) { mutableStateOf("") }
    val liste = rememberLazyListState()
    // Görüşme açıkken yeni mesajlar birkaç saniyede bir alınır
    LaunchedEffect(kanal.metin("kanal"), d.sohbetTur) {
        while (isActive) {
            d.sohbetYokla()
            delay(if (destek) 3500 else 4000)
        }
    }
    LaunchedEffect(d.sohbetMesajlar.size) {
        if (d.sohbetMesajlar.isNotEmpty()) runCatching { liste.animateScrollToItem(d.sohbetMesajlar.size - 1) }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IkonDugme("arrow-left") { d.sohbetGeri() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(kanal.metin("ad"), color = Koyu, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val alt = if (destek) d.sohbetDurum else if (kanal.optBoolean("cevrimici")) "Çevrimiçi" else kanal.metin("alt")
                if (alt.isNotEmpty()) Text(alt, color = Soluk, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (d.mesaj.isNotEmpty()) Box(Modifier.padding(horizontal = 16.dp)) { Uyari(d.mesaj, "red") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = liste, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.sohbetMesajlar.isEmpty()) item {
                Text(if (destek) "Sorunuzu yazın; destek ekibi buradan yanıtlayacak." else "Henüz mesaj yok. İlk mesajı siz yazın.", color = Soluk, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 30.dp))
            }
            items(d.sohbetMesajlar) { m ->
                val ben = m.optBoolean("ben")
                val bicim = RoundedCornerShape(14.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (ben) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 290.dp).then(if (ben) Modifier.background(Gecis, bicim) else Modifier.background(KartRenk, bicim)).padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (!ben && m.metin("gonderen").isNotEmpty()) Text(m.metin("gonderen"), color = Birincil, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (m.optBoolean("silindi")) "Mesaj silindi" else m.metin("mesaj"), color = if (ben) Color.White else Koyu, fontSize = 14.sp)
                        Text(m.metin("saat"), color = if (ben) Color(0xCCFFFFFF) else Soluk, fontSize = 10.5.sp, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(KartRenk).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
            Girdi("", yazi, { yazi = it.take(2000) }, Modifier.weight(1f), ipucu = "Mesaj yazın…")
            Spacer(Modifier.width(8.dp))
            Dugme("Gönder", ikon = "paper-plane", birincil = true, etkin = yazi.isNotBlank(), mesgul = d.sohbetGonderiyor) { d.sohbetGonder(yazi) { yazi = "" } }
        }
    }
}
