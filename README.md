# Servis Masası — Android uygulaması

Servis Masası'nın telefon uygulaması (Kotlin + Jetpack Compose). Web sayfası göstermez; kendi ekranları vardır ve
bütün işlemler firmanın kendi Servis Masası hesabına yazılır.

## Derleme
`main` dalına her gönderimde GitHub Actions APK'yı derler ve **Releases** bölümüne ekler.
Sürüm numarası `SURUM` dosyasından okunur.

## Bu depoda ASLA bulunmaması gerekenler
- İmza anahtarı (`*.jks`, `*.keystore`) ve parolası → yalnızca GitHub **Secrets**: `IMZA_ANAHTARI`, `IMZA_PAROLASI`
- `.env`, `ayarlar.env` gibi ayar dosyaları, veritabanı parolaları, API anahtarları
- Müşteri verisi

Uygulamanın içinde hiçbir parola ya da gizli anahtar yoktur: firma adresi KavisCloud'dan Firma No ile sorulur,
oturum anahtarları girişte sunucudan alınır ve telefonun güvenli kasasında (Android Keystore) saklanır.
