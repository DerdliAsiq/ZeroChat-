# ZeroChat — Uçtan Uca Şifreli, Sunucusuz Mimari Odaklı Mesajlaşma Platformu

ZeroChat, merkezi sunucuların mesaj içeriklerini asla göremediği veya saklamadığı (Zero-Knowledge), kullanıcıların birbirlerini benzersiz `@kullanici_adi` ile küresel olarak bulabildiği ve tüm iletişimin cihazlar arası uçtan uca şifrelenerek (AES-256-GCM + ECDH anahtar değişimi) gerçekleştiği tam kapsamlı bir güvenli iletişim uygulamasıdır.

## Kullanıcı İncelemesi ve Onaylanan Kritik Kararlar

> [!IMPORTANT]
> Aşağıdaki mimari ve ürün kararları tercihleriniz doğrultusunda kesinleştirilmiştir. Onayınızın ardından Firebase kimlik/sinyal altyapısı ve yerel şifreli veritabanı katmanları oluşturulacaktır.

- **Onaylanan Karar 1 — Bağlantı ve Keşif Mimarisi**: **Hibrit E2EE Şifreli Bulut Sinyal ve Dizin + P2P Mesajlaşma**. Kullanıcılar Google hesabı ile kimlik doğrulaması yaptıktan sonra benzersiz bir `@kullanici_adi` ve cihaz üzerinde üretilen Açık Anahtar (Public Key) kaydeder. Mesaj paketleri yalnızca alıcının açık anahtarıyla şifrelenmiş zarf (encrypted envelope) olarak iletilir ve teslim alındığında cihazdaki yerel şifreli kasada (Room) saklanır.
- **Onaylanan Karar 2 — Zengin Mesajlaşma Özellikleri**: Birebir ve grup sohbetleri, medya/dosya paylaşımı, sesli not kaydı ve dalga formu oynatıcısı, sesli/görüntülü arama ekranı (WebRTC P2P sinyal simülasyonu ve canlı çağrı kontrolleri), 24 saatlik şifreli durum/hikaye paylaşımları, sohbet içi anketler, canlı konum kartları ve mesaj tepkileri (emoji reaksiyonları).
- **Onaylanan Karar 3 — İleri Düzey Gizlilik ve Güvenlik**: Kendi kendini imha eden süreli mesajlar (10 sn, 1 dk, 1 saat, Okunduktan Sonra), PIN/Biyometrik kilitli gizli sohbet arşivi, QR Kod ve 60 haneli Güvenlik Numarası (Safety Number) ile anahtar doğrulama ve **Sahte Panik PIN Kodu (Decoy Mode)** ile acil durumda hassas sohbetleri gizleyip masum bir arayüz gösterme.
- **Onaylanan Karar 4 — Görsel Tasarım ve Atmosfer**: **Siber Güvenlik Odaklı Koyu Neon Yeşil ve Siyah Tema**. Obsidyen siyah zeminler, fosforlu siber yeşil vurgular, şifreleme durum rozetleri ve monospace kriptografik parmak izi tipografisi.

---

## 1. Genel Bakış ve Temel Konsept

- **Ne Yapar?**: WhatsApp'ın sunduğu modern ve akıcı mesajlaşma deneyimini (sohbetler, gruplar, hikayeler, sesli notlar, anketler, aramalar) sıfır-güven (Zero-Trust) kriptografik mimariyle birleştirir. Her sohbet kendi oturum anahtarına sahiptir; kullanıcılar diledikleri mesajı süreli (kendini imha eden) olarak gönderebilir ve güvenlik numaralarını QR kod ile doğrulayabilir.
- **Hedef Kitle / Persona**: Mahremiyetine önem veren bireyler, güvenlik araştırmacıları, kurumsal ekipler ve verilerinin merkezi sunucularda düz metin olarak işlenmesini istemeyen günlük kullanıcılar.
- **Temel Değer Önerisi**:Telefon numarası paylaşma zorunluluğu olmadan yalnızca `@kullanici_adi` veya QR kod ile anında güvenli eşleşme, şeffaf kriptografik doğrulama (her mesajın şifreli zarf detayını inceleyebilme) ve fiziksel tehditlere karşı Panik PIN koruması.

---

## 2. Kullanıcı Deneyimi ve Görsel Tasarım

### Temel Kullanıcı Akışları
1. **Kimlik Oluşturma ve Anahtar Üretimi (Onboarding)**:
   - Google Sign-In ile güvenli oturum açma.
   - Benzersiz `@kullanici_adi` seçimi ve cihaz üzerinde otomatik **ECDH / RSA-2048 & AES-256** kimlik anahtar çiftinin üretilmesi.
   - Normal Kasa PIN kodu ve opsiyonel **Panik PIN Kodu** belirleme.
2. **Kullanıcı Adı ile Keşif ve El Sıkışma (Handshake)**:
   - Arama çubuğundan `@kullanici_adi` araması veya QR kod tarama/paylaşma.
   - Karşı tarafın Açık Anahtarı (Public Key) ve Güvenlik Parmak İzi (Safety Fingerprint) alınarak şifreli oturum başlatma.
3. **Tam Özellikli Şifreli Sohbet Deneyimi**:
   - Mesaj yazma, emoji tepkileri, fotoğraf/medya seçici (Android Photo Picker), sesli mesaj kaydetme/dinleme, anket oluşturma ve konum/dosya kartı gönderme.
   - Üst bardaki kilit ikonuna dokunarak **Kriptografik Denetim (Encryption Inspector)** penceresini açma: Oturum parmak izi, QR doğrulama ve mesajın ham şifreli (ciphertext) yükünü görüntüleme.
   - **Süreli Mesaj (Burn-on-Read)** sayacı seçimi: Süresi dolan mesajların hem ekrandan hem yerel veritabanından otomatik silinmesi.
4. **Durum (24 Saatlik Hikayeler) ve Aramalar**:
   - Şifreli metin/görsel durum paylaşımı, görüntüleyenler ve 24 saat geri sayım halkası.
   - Sesli ve görüntülü P2P arama ekranı: Kamera/mikrofon izin yönetimi, uçtan uca şifreleme doğrulama emojileri (Key Verification Emojis), hoparlör/sessize alma ve bağlantı telemetrisi.
5. **Gizli Arşiv ve Panik PIN (Decoy Mode)**:
   - Sohbetleri basılı tutarak Gizli Kasaya taşıma.
   - Kasa ekranında **Normal PIN** girildiğinde gizli sohbetler açılır; **Panik PIN** girildiğinde uygulama tüm hassas verileri gizleyerek masum/boş bir görünüm sunar.

### Görsel Kimlik ve Tema
- **Estetik Yönelim**: Taktiksel Siber Güvenlik ve Yüksek Hassasiyetli Karanlık Arayüz (Cyber-Stealth Minimalism).
- **Renk Paleti**:
  - `Background`: `#060A0D` (Derin Obsidyen Siyahı)
  - `Surface / Card`: `#0E161B` (Koyu Karbon Yeşili-Gri)
  - `SurfaceVariant`: `#152229` (Yükseltilmiş Kriptografik Kart Zemini)
  - `Primary`: `#00FF88` (Siber Neon Yeşil — aktif şifreleme, ana aksiyonlar, okundu/doğrulandı göstergeleri)
  - `Secondary`: `#00D2FF` (Siber Camgöbeği — P2P düğüm durumu, grup rozetleri, medya dalga formları)
  - `Tertiary / Warning`: `#FFB300` (Kehribar — süreli mesaj geri sayımı ve anahtar değişim uyarıları)
  - `Error / Burn`: `#FF3366` (Kırmızı/Mercan — mesaj imha animasyonu, panik modu, çağrı sonlandırma)
- **Tipografi ve Hiyerarşi**:
  - *Başlıklar (`Space Grotesk`)*: Keskin, teknik ve modern görünüm.
  - *Gövde (`Inter` / `Plus Jakarta Sans`)*: Uzun mesajlarda yüksek okunabilirlik.
  - *Kriptografik Veriler (`JetBrains Mono`)*: Parmak izleri, güvenlik numaraları, zamanlayıcılar ve şifreli paket özetleri için sabit genişlikli font.
- **Bileşen Stili ve Düzen**:
  - İnce `1.dp` yarı saydam neon yeşil/camgöbeği kenarlıklı mesaj balonları ve kartlar.
  - Alt gezinme çubuğunda (NavigationBar) 4 ana sekme: **Sohbetler**, **Durumlar (24s)**, **Aramalar** ve **Güvenlik Kasası (Profil & Anahtarlar)**.

---

## 3. Temel Ürün Kararları ve Mimari Dengeler

- **Karar 1: Hibrit Sıfır-Bilgi (Zero-Knowledge) Sinyal + Yerel Şifreli Kasa**
  - *Seçilen Yaklaşım*: Firestore yalnızca `@kullanici_adi` -> `publicKey` dizini ve uçtan uca şifrelenmiş (`ciphertext` + `iv` + `senderPublicKey`) mesaj zarflarının iletimi için kullanılır. Mesajın düz metni (plaintext) yalnızca gönderici ve alıcı cihazda `javax.crypto.Cipher` (`AES/GCM/NoPadding`) ile çözülür ve cihaz içi Room veritabanında tutulur.
  - *Neden?*: Kullanıcıların aynı Wi-Fi ağında olma zorunluluğunu ortadan kaldırarak dünyanın her yerinden `@kullanici_adi` ile birbirlerini bulmalarını sağlar; aynı zamanda sunucuda hiçbir düz metin mesaj barındırmayarak "sunucusuz/sıfır-bilgi" gizlilik vaadini gerçek kriptografiyle karşılar.
- **Karar 2: Gerçek Cihaz Üzeri Kriptografi (`AES-256-GCM` & `SHA-256` Güvenlik Numaraları)**
  - *Seçilen Yaklaşım*: Sahte "şifrelendi" etiketleri yerine her mesaj gerçek `AES-256-GCM` ile şifrelenir; kullanıcılar sohbet içindeki "Şifreli Zarfı İncele" butonuyla mesajın gerçek Base64 şifreli yükünü (ciphertext), başlatma vektörünü (IV) ve SHA-256 kimlik parmak izini görebilir.
  - *Neden?*: Uygulamanın güvenilirliğini kanıtlar ve kullanıcıya şeffaf denetim imkanı verir.

---

## 4. Teknik Mimari ve Veri Stratejisi *(Teknik Referans)*

### Sistem ve Bileşen Mimarisi
```
┌──────────────────────────────────────────────────────────────────────────┐
│                        ZeroChat Android İstemcisi                        │
│                                                                          │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────┐  ┌────────────┐  │
│  │   Sohbetler    │  │  Durumlar (24s)│  │  Aramalar  │  │ Güvenlik & │  │
│  │ (1:1 & Gruplar)│  │ (Şifreli Story)│  │ (P2P Ses/  │  │ Gizli Kasa │  │
│  │ + Süreli Mesaj │  │ + Geri Sayım   │  │  Görüntü)  │  │ (Panik PIN)│  │
│  └───────┬────────┘  └───────┬────────┘  └─────┬──────┘  └─────┬──────┘  │
│          └───────────────────┴────────┬────────┴───────────────┘         │
│                                       ▼                                  │
│                     ┌───────────────────────────────────┐                │
│                     │   ZeroChatViewModel (StateFlow)   │                │
│                     │  - Aktif Oturum & Panik PIN Modu  │                │
│                     │  - Süreli Mesaj İmha Zamanlayıcısı│                │
│                     └────────┬─────────────────┬────────┘                │
│                              │                 │                         │
│                              ▼                 ▼                         │
│        ┌──────────────────────────┐       ┌──────────────────────────┐   │
│        │   CryptoEngine (E2EE)    │       │  LocalVaultRepository    │   │
│        │ - AES-256-GCM Şifreleme  │       │  (Room SQLite Veritabanı)│   │
│        │ - ECDH / Anahtar Türetme │       │ - Çözülmüş Mesajlar      │   │
│        │ - SHA-256 Safety Number  │       │ - Gizli Arşiv & PIN Hash │   │
│        └─────────────┬────────────┘       └──────────────────────────┘   │
└──────────────────────┼───────────────────────────────────────────────────┘
                       │ Yalnızca Şifreli Zarflar (Ciphertext + IV)
                       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│            Şifreli Bulut Dizin & Sinyal Katmanı (Firestore)              │
│  • /users/{uid}          -> @username, displayName, publicKey, fingerprint│
│  • /usernames/{username} -> uid, publicKey (Global Kullanıcı Adı Dizini) │
│  • /rooms/{roomId}       -> participantUids, isGroup, encryptedMeta      │
│  • /rooms/{roomId}/envelopes/{msgId} -> ciphertext, iv, type, expiresAt  │
│  • /statuses/{statusId}  -> authorUid, ciphertext, iv, expiresAt         │
│  • /calls/{callId}       -> callerUid, calleeUid, signalingState         │
└──────────────────────────────────────────────────────────────────────────┘
```

### Veri Modeli ve Durum Yönetimi
- **Kullanıcı Profili & Anahtar Dizini (`UserProfile`)**:
  - `uid`, `username` (küçük harf, benzersiz), `displayName`, `bio`, `publicKey` (Base64), `safetyFingerprint` (60 haneli blok formatında), `onlineStatus`.
- **Şifreli Mesaj Zarfı (`EncryptedEnvelope` & Yerel `DecryptedMessage`)**:
  - `id`, `roomId`, `senderUid`, `senderUsername`, `ciphertext` (AES-256-GCM ile şifrelenmiş içerik), `iv` (Base64), `messageType` (`TEXT`, `IMAGE`, `VOICE`, `POLL`, `LOCATION`, `FILE`), `metadataJson` (şifreli anket seçenekleri/oyları, ses süresi, konum koordinatı), `reactions` (kullanıcı -> emoji eşleşmesi), `ttlSeconds` (kendini imha süresi), `expiresAt`, `isVaultLocked`.
- **Durum Paylaşımı (`EncryptedStatus`)**:
  - `id`, `authorUid`, `authorUsername`, `ciphertext`, `iv`, `backgroundHex`, `createdAt`, `expiresAt` (24 saat), `viewerUids`.
- **P2P Arama Oturumu (`CallSession`)**:
  - `id`, `callerUid`, `callerUsername`, `calleeUid`, `calleeUsername`, `isVideo`, `status` (`RINGING`, `CONNECTED`, `ENDED`), `verificationEmojis` (4'lü kriptografik emoji dizisi).

### Etkileşimli Bileşen ve Durum Eşleşmesi
- **Kullanıcı Adı Arama & Eşleşme**: Kullanıcı `@` ile arama yaptığında `usernames` koleksiyonundan tam veya önek eşleşmesi sorgulanır; tek dokunuşla ortak paylaşımlı anahtar türetilerek şifreli oda (`roomId`) başlatılır.
- **Çoklu Mesaj Türleri**:
  - *Sesli Notlar*: Mikrofon izni ile gerçek ses kaydı veya anlık dalga formu üretimi, oynatma/duraklatma ilerleme çubuğu.
  - *Anketler*: Başlık ve çoklu seçenek oluşturma, gerçek zamanlı şifreli oy kullanma ve yüzdelik çubuk gösterimi.
  - *Konum & Medya*: Android Photo Picker (`PickVisualMedia`) ile sıfır izinli görsel paylaşımı ve cihaz GPS/konum kartı paylaşımı.
  - *Süreli Mesajlar*: `ViewModel` içindeki saniyelik coroutine ticker ile `expiresAt` süresi dolan mesajların gerçek zamanlı olarak imha edilmesi.
- **Güvenlik Denetçisi & QR Doğrulama**: Her sohbetteki kalkan/kilit ikonuna basıldığında 60 haneli Safety Number, taranabilir özel Canvas QR kodu ve mesajların şifreli (ciphertext) vs çözülmüş (plaintext) karşılaştırma tablosu gösterilir.
- **Panik Modu & Gizli Kasa**: Kullanıcı PIN ekranında Panik PIN'ini girdiğinde `isPanicModeActive = true` olur; tüm gizli ve hassas sohbetler arayüzden gizlenir ve acil durum temiz görünümü devreye girer.
