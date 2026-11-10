# ZeroChat — Gerçek Zamanlı Arama, Bildirim, Sesli Mesaj ve Sadeleştirilmiş Koyu Siber Arayüz Planı

Kullanıcıları korkutan karmaşık kriptografik/teknik terimleri temizleyerek mevcut koyu siber temayı modern ve anlaşılır hale getiriyor; sesli/görüntülü aramaları, doğrulama emojilerini, sesli mesaj gönderimini, otomatik mesaj kaydırmayı ve Android sistem bildirimlerini uçtan uca çalışır duruma kavuşturuyoruz.

## Kullanıcı Onayı ve Kararlaştırılan Tercihler

> [!IMPORTANT]
> Sorulara verdiğiniz yanıtlar doğrultusunda aşağıdaki kararlar kesinleştirilmiştir:

- **Sohbet ve Mesaj Balonlarından Kaldırılacak Teknik Detaylar**:
  - Mesaj balonlarının altındaki `R#1 • a8f9c2...` şifreli kod satırı ve `< >` zarf inceleme ikonu tamamen kaldırılacak; yalnızca saat ve okundu (`✓` / `✓✓`) bilgisi yer alacak.
  - Sohbet üst başlığındaki `ECDH P-256 + Ratchet • Anahtarı Denetle` ifadeleri kaldırılarak yerine `Çevrimiçi • Uçtan Uca Şifreli` veya `@kullanici yazıyor...` durumu gösterilecek.
  - Sohbetin en üstündeki karmaşık `Sıfır-Bilgi (Zero-Knowledge) ECDH + Forward Secrecy Oturumu` kutusu kaldırılarak tek satırlık sade `🔒 Mesajlar ve aramalar uçtan uca şifrelidir` rozetine dönüştürülecek.
  - Sohbetler listesindeki anlaşılmaz şifreli önizleme (`AES-256-GCM: ...`) kaldırılarak doğrudan son mesajın çözülmüş anlaşılır metni gösterilecek.
  - Tüm ekranlardaki `Zarf`, `Düğüm` ve `İmha` kelimeleri `Mesaj`, `Kişi / Kullanıcı` ve `Sil` olarak değiştirilecek.
- **Profil ve Durumlar Ekranından Kaldırılacak Teknik Detaylar**:
  - Durum kartlarındaki ve hikaye görüntüleyicideki `AES-256-GCM Zarfı: ...` ve `(IV: ...)` satırları tamamen kaldırılacak.
  - Profil/Kasa sekmesindeki `Donanım Destekli Kriptografi Motoru / Keystore TEE / Hash Ratchet` teknik kartı tamamen kaldırılacak; kişisel QR kod, güvenlik numarası ve 4 emojili doğrulama korunacak.
- **Görsel Tasarım Yönü**:
  - Mevcut koyu siber tema (`ObsidianBlack`, `CarbonSurface`, `NeonCyberGreen`, `CyberCyan`) korunacak; ancak göz yoran kalın çerçeveler ve teknik kalabalık temizlenerek sade, modern ve akıcı bir görünüm sağlanacak.
- **Gerçek Zamanlı P2P Arama ve Android Sistem Bildirimleri**:
  - Sesli ve görüntülü aramalarda gerçek zamanlı ses/görüntü aktarımı ve yeni mesaj/arama/istek geldiğinde gerçek Android sistem bildirimleri (`NotificationCompat`) aktif edilecek.

---

## 1. Genel Bakış ve Çözülecek 7 Ana Sorun

1. **Çift Yönlü Gerçek Sesli ve Görüntülü Arama**: Arama başlatıldığında karşı kullanıcının gerçek bulut kimliği çözümlenecek, çağrı durumu (`RINGING`, `CONNECTED`, `ENDED`) iki cihazda canlı senkronize edilecek ve görüşme esnasında şifreli canlı ses dilimleri ile kamera görüntü kareleri karşı cihaza gerçek zamanlı aktarılacak.
2. **İki Cihazda Birebir Aynı 4 Doğrulama Emojisi**: Rastgele arama ID'si veya geçici istek ön eki (`REQ_FROM::`) yerine her iki kullanıcının sıralı ortak anahtarı kullanılarak iki telefonda da her zaman aynı 4 güvenlik emojisinin görünmesi sağlanacak.
3. **Sorunsuz Sesli Mesaj (Voice Note) Kaydı ve Gönderimi**: Tüm Android cihazlarla tam uyumlu `16 kHz / 32 kbps AAC` kayıt profili kullanılacak, mikrofon izni kontrolü iyileştirilecek ve tek dokunuşla kayıt/gönderim akışı hatasız çalışacak.
4. **Yeni Mesajda Otomatik Aşağı Kaydırma (Auto-Scroll)**: Sohbete girildiğinde, yeni mesaj yazıldığında veya karşıdan mesaj geldiğinde mesaj listesi otomatik olarak en alttaki son mesaja kayacak.
5. **Sadeleştirilmiş Koyu Siber Arayüz**: Koyu neon-yeşil/camgöbeği tema korunarak aşırı teknik yazılar, karmaşık kod satırları ve kalabalık çubuklar sadeleştirilecek.
6. **Gerçek Android Bildirimleri**: Uygulama açıkken veya arka plandayken gelen mesajlar, iletişim istekleri ve aramalar için sesli/titreşimli Android sistem bildirimleri gösterilecek.
7. **Kullanıcı Dostu Dil**: "Düğüm", "Zarf", "İmha", "Ratchet" gibi ifadeler tamamen günlük anlaşılır mesajlaşma diline çevrilecek.

---

## 2. Kullanıcı Deneyimi ve Görsel Tasarım

- **Sohbet Listesi Deneyimi**:
  - Her sohbet kartında kişinin adı (`@kullanici`), okunaklı son mesaj içeriği (ör. `"Merhaba, nasılsın?"`, `"📷 Fotoğraf"`, `"🎙️ Sesli mesaj"`), zaman damgası ve zarif durum rozeti yer alır.
- **Sohbet İçi Deneyimi**:
  - Üst barda yalnızca kişi adı, doğrulama kalkanı, sesli/görüntülü arama ve sadeleştirilmiş seçenekler bulunur.
  - Mesaj balonları modern yuvarlatılmış köşelere, net tipografiye ve sağ altta yalnızca saat + okundu tiklerine (`✓✓`) sahiptir.
  - Yeni mesaj gönderildiğinde veya alındığında liste yumuşak bir animasyonla (`animateScrollToItem`) en son mesaja iner.
- **Canlı Arama Ekranı**:
  - Arama çalarken (`Aranıyor...` / `Gelen Arama...`) ve bağlandığında (`00:14 • Şifreli Görüşme`) durumu canlı güncellenir; görüntülü aramada karşı tarafın canlı kamera görüntüsü ana ekranda, kendi kameramız sağ alt köşede (PiP) gösterilir.

---

## 3. Temel Ürün ve Mimari Kararlar

- **Deterministik 4-Emoji Doğrulama Algoritması**:
  - *Seçilen Yaklaşım*: Emojileri rastgele `callId` yerine `CryptoEngine.computeDirectRoomKeyHint(uidA, uidB)` ve sıralanmış kullanıcı adlarından türetmek.
  - *Nedeni*: İki cihazda farklı zamanlarda veya farklı yönlerden açılan sohbet ve aramalarda %100 aynı 4 emojinin çıkmasını garanti eder.
- **Gerçek Zamanlı Ses ve Görüntü Aktarımı**:
  - *Seçilen Yaklaşım*: Firestore `calls` dokümanı üzerinden canlı çağrı durumu senkronizasyonu + aktif görüşme sırasında düşük gecikmeli şifreli ses/görüntü çerçeveleri (`callerAudioChunk`, `calleeAudioChunk`, `callerVideoFrame`, `calleeVideoFrame`) akışı.
  - *Nedeni*: Ek bir harici TURN sunucusu gerektirmeden doğrudan mevcut şifreli bulut altyapısı üzerinde çift yönlü canlı ses ve görüntü iletimi sağlar.
- **Standart AAC Ses Kaydı (`16 kHz`)**:
  - *Seçilen Yaklaşım*: `MediaRecorder` örnekleme hızını `12000 Hz` yerine tüm Android donanım kodeklerinin desteklediği `16000 Hz` ve `24000 bps` değerine ayarlamak, izin verilmişse doğrudan kaydı başlatmak.
  - *Nedeni*: Bazı telefonlarda `prepare()` aşamasında oluşan sessiz çökmeleri tamamen ortadan kaldırır.

---

## 4. Teknik Mimari ve Veri Akışı

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        ZeroChat Android İstemcisi                       │
├───────────────────────┬─────────────────────────┬───────────────────────┤
│   Sadeleştirilmiş UI  │  Gerçek Zamanlı Medya   │  Sistem Bildirimleri  │
│  (Koyu Siber Tema)    │   (Ses & Kamera P2P)    │ (NotificationHelper)  │
└───────────┬───────────┴────────────┬────────────┴───────────┬───────────┘
            │                        │                        │
            ▼                        ▼                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           ZeroChatViewModel                             │
│  • Auto-Scroll State   • Canlı Arama Senkronu   • Yeni Mesaj/Arama Farkı│
│  • Çözülmüş Önizleme   • Deterministik 4-Emoji  • Ses Kayıt Yönetimi    │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     ZeroChatRepository & CryptoEngine                   │
│  • Gerçek Peer UID Çözümleme       • AES-256-GCM Şifreli Medya/Mesaj    │
│  • Canlı CallSignal Akışı          • Ortak Anahtar Emoji Türetimi       │
└─────────────────────────────────────────────────────────────────────────┘
```

### Etkileşim ve Durum Eşlemesi
- **Mesaj Listesi Otomatik Kaydırma**: `LazyListState` mesaj sayısı değiştiğinde (`messages.size`) veya son mesaj ID'si güncellendiğinde otomatik olarak son indekse (`messages.lastIndex`) kayar.
- **Sesli Not Kayıt Akışı**: Mikrofon butonuna basıldığında `ContextCompat.checkSelfPermission` kontrol edilir; izin varsa anında kayıt başlar, yoksa izin alınır alınmaz kayıt devreye girer.
- **Gelen ve Giden Arama Akışı**: Arama başlatıldığında karşı kullanıcının gerçek UID'si bulunur; karşı taraf kabul ettiğinde (`CONNECTED`) her iki cihazda sayaç ve canlı ses/kamera akışı başlar; herhangi bir taraf kapattığında (`ENDED`) her iki cihazda arama ekranı otomatik kapanır.
- **Android Sistem Bildirimi Akışı**: `POST_NOTIFICATIONS` izni ile yeni gelen mesajlarda gönderen adı ve çözülmüş mesaj içeriği, gelen aramalarda ise yüksek öncelikli arama bildirimi Android durum çubuğuna düşer.
