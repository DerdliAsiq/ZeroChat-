# ZeroChat — Tam Kapsamlı Gerçek Donanım, Medya İletimi ve Sıfır-Simülasyon Mimari Planı

Bu plan, ZeroChat uygulamasının tüm katmanlarında yapılan kapsamlı denetim sonucunda tespit edilen kısıtlamaların giderilmesini, cihazlar arası gerçek şifreli medya/ses/dosya aktarımının devreye alınmasını ve uygulama içerisindeki tüm test/simülasyon bileşenlerinin kalıcı olarak kaldırılmasını kapsar.

## Kullanıcı İncelemesi ve Onaylanan Kritik Kararlar

> [!IMPORTANT]
> Yanıtlarınız doğrultusunda aşağıdaki üç temel mimari karar kesinleştirilmiştir. Onayınızın ardından tüm güncellemeler doğrudan uygulanacaktır.

- **Onaylanan Karar 1 — Cihazlar Arası Gerçek Şifreli Medya ve Ses İletimi**: Kamera veya galeriden seçilen fotoğraflar, steganografi görselleri ve kaydedilen sesli notlar yalnızca cihaz içi yerel adres (`content://`) olarak bırakılmayacak; sıkıştırılmış ve `AES-256-GCM` ile uçtan uca şifrelenmiş gerçek Base64 veri yükü olarak bulut zarfları üzerinden karşı cihaza iletilecektir. Böylece alıcı cihaz görselleri doğrudan görüntüleyebilecek ve ses kayıtlarını kesintisiz dinleyebilecektir.
- **Onaylanan Karar 2 — Etkileşimli Şifreli Belge İndirme ve Gerçek Harita Navigasyonu**: Sohbette gönderilen belgeler yalnızca dosya adı etiketi olmayacak; şifreli dosya içeriğiyle birlikte iletilecek ve üzerine dokunulduğunda Android sistem dosya kaydetme penceresi aracılığıyla cihaza indirilebilecektir. Konum mesajlarına dokunulduğunda ise gerçek GPS koordinatları doğrudan Google Haritalar veya cihazdaki harita uygulamasında açılacaktır.
- **Onaylanan Karar 3 — Tüm Test ve Simülasyon Kodlarının Tamamen Silinmesi**: Kriptografik denetçi penceresindeki MITM anahtar değişimi test butonu ile durum yönetim katmanındaki tüm simülasyon durumları ve test fonksiyonları projeden tamamen temizlenecektir.

---

## 1. Kapsamlı Denetim Bulguları ve Çözüm Özeti

- **Bulgu 1 — Galeri Fotoğrafları ve Ses Kayıtlarının Karşı Cihazda Açılmaması**:
  - *Mevcut Durum*: Kameradan çekilen fotoğraflar Base64 formatına dönüştürülürken, galeriden seçilen fotoğraflar yerel cihaz bağlantısı (`content://`) olarak gönderiliyor ve karşı cihazda görüntülenemiyordu. Ayrıca bulut güvenlik kurallarındaki düşük boyut sınırı (16 KB) nedeniyle 2 saniyeden uzun ses kayıtları sessizce engelleniyordu.
  - *Çözüm*: Galeri ve steganografi seçicilerinden gelen tüm görseller otomatik olarak optimize edilmiş JPEG verisine dönüştürülüp şifrelenecek. Bulut şifreli zarf kapasitesi 256 KB seviyesine yükseltilecek ve ses kayıt motoru optimize edilerek uzun sesli notların karşı cihaza eksiksiz ulaşması sağlanacak.
- **Bulgu 2 — Etkileşimsiz Belge ve Konum Kartları**:
  - *Mevcut Durum*: Belge gönderiminde yalnızca dosya adı ve boyutu gösteriliyor, alıcı dosyayı indiremiyordu. Konum mesajları ise düz metin balonunda kalıyor ve harita uygulamasını tetiklemiyordu.
  - *Çözüm*: Seçilen belgeler şifreli Base64 yükü olarak mesaja eklenecek; alıcı belge balonuna dokunduğunda dosyayı cihazına kaydedebilecek. Konum balonlarına özel etkileşimli kart görünümü ve haritada açma aksiyonu eklenecek.
- **Bulgu 3 — Kalan Simülasyon Fonksiyonları ve Test Düğmeleri**:
  - *Mevcut Durum*: Kriptografik denetim penceresinde yer alan manuel MITM uyarı test butonu, arka plandaki simüle edilmiş çağrı/anahtar değişimi değişkenleri ve ses kaydı sırasındaki rastgele dalga boyu yedeklemesi gerçeklik ilkesini bozuyordu.
  - *Çözüm*: Tüm test ve simülasyon fonksiyonları silinecek. Ses dalga formu yalnızca mikrofon donanımından gelen gerçek ses şiddeti (`maxAmplitude`) verisini yansıtacak; anahtar değişimi uyarıları ise yalnızca karşı kullanıcının buluttaki gerçek açık anahtarı değiştiğinde otomatik tetiklenecek.
- **Bulgu 4 — Kasa Sekmesindeki Kişisel QR Kodun Tam Eşleşme Bilgisi İçermemesi**:
  - *Mevcut Durum*: Kasa ve Profil ekranındaki kişisel QR kod yalnızca ham açık anahtar dizisini barındırdığı için başka bir cihaz tarafından tarandığında kullanıcı adı ve kimlik bilgilerini tam olarak çözümleyemiyordu.
  - *Çözüm*: Kasa ekranındaki QR kod bileşeni, sohbet ekranındakiyle birebir aynı standart `zerochat://peer` bağlantı protokolünü (`@kullanici_adi`, kullanıcı kimliği, güvenlik parmak izi ve açık anahtar) üretecek şekilde birleştirilecek.

---

## 2. Teknik Mimari ve Gerçek Veri Akışı

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   ZeroChat Gönderici Cihaz (Donanım & E2EE)                │
│                                                                            │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐  │
│  │ Kamera & Galeri      │  │ Gerçek Mikrofon      │  │ Belge & Canlı    │  │
│  │ Görsel Sıkıştırma    │  │ Donanımı (AAC Ses &  │  │ GPS Konum        │  │
│  │ (JPEG -> Base64)     │  │ Gerçek Genlik Ölçümü)│  │ Okuyucu          │  │
│  └──────────┬───────────┘  └──────────┬───────────┘  └────────┬─────────┘  │
│             └─────────────────────────┼───────────────────────┘            │
│                                       ▼                                    │
│                     ┌───────────────────────────────────┐                  │
│                     │   AES-256-GCM Şifreleme Motoru    │                  │
│                     │  (Şifreli Yük + Şifreli Üstveri)  │                  │
│                     └─────────────────┬─────────────────┘                  │
└───────────────────────────────────────┼────────────────────────────────────┘
                                        │
                                        ▼ (256 KB'a Kadar Şifreli Zarf)
┌────────────────────────────────────────────────────────────────────────────┐
│                Güvenli Bulut Zarf & Dizin Katmanı (Firestore)              │
│  • Doğrulanmış Zarf Boyutu Kapasitesi (262.144 Bayt Şifreli Yük & Meta)    │
│  • Gerçek Zamanlı Açık Anahtar (Public Key) ve Parmak İzi Takibi           │
└───────────────────────────────────────┬────────────────────────────────────┘
                                        │
                                        ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                    ZeroChat Alıcı Cihaz (Çözümleme & Etkileşim)            │
│                                                                            │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐  │
│  │ Şifreli Görsel &     │  │ Sesli Not Çözümleme  │  │ Belge İndirme    │  │
│  │ Steganografi Çözücü  │  │ ve MediaPlayer ile   │  │ (Cihaza Kaydet) &│  │
│  │ (Anında Görüntüleme) │  │ Gerçek Ses Oynatma   │  │ Haritalarda Aç   │  │
│  └──────────────────────┘  └──────────────────────┘  └──────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Uygulama Adımları

1. **Bulut Güvenlik Kuralları ve Zarf Kapasitesinin Güncellenmesi**:
   - Şifreli mesaj zarflarındaki (`ciphertext` ve `encryptedMeta`) boyut sınırı 262.144 bayta çıkarılarak şifrelenmiş fotoğrafların, ses kayıtlarının ve belgelerin bulut üzerinden reddedilmeden karşı cihaza aktarılması sağlanacak.
   - Güncellenen güvenlik kuralları yerel emülatör testleriyle doğrulanıp canlı bulut ortamına dağıtılacak.
2. **Gerçek Medya, Ses ve Steganografi Aktarımı**:
   - Galeriden seçilen fotoğraflar ve steganografi görselleri otomatik ölçeklendirilip JPEG Base64 veri yapısına dönüştürülerek şifrelenecek.
   - Ses kayıt motorundaki rastgele dalga boyu üretimi kaldırılarak yalnızca gerçek mikrofon genliği kullanılacak; AAC bit hızı ve örnekleme frekansı optimize edilerek uzun sesli mesajların karşı cihaza iletilmesi sağlanacak.
3. **Etkileşimli Belge İndirme ve Harita Entegrasyonu**:
   - Sohbet içinden seçilen belgeler okunup şifreli üstveri içerisine dahil edilecek; belge mesajına tıklandığında sistem dosya kaydetme arayüzü açılarak dosyanın cihaza indirilmesi sağlanacak.
   - Konum mesajları için özel etkileşimli kart tasarımı eklenecek ve dokunulduğunda koordinatları doğrudan harita uygulamasında açacak sistem bağlantısı tetiklenecek.
4. **Simülasyonların Temizlenmesi ve QR Kimlik Standardizasyonu**:
   - Denetçi penceresindeki MITM test butonu ve durum yönetimindeki tüm simülasyon metodları tamamen kaldırılacak; anahtar değişimi uyarıları yalnızca gerçek bulut profilindeki açık anahtar değişimlerine bağlanacak.
   - Kasa ve Profil sekmesindeki QR kod bileşeni tam `zerochat://peer` bağlantı yapısıyla güncellenecek ve ardından tüm proje derlenerek doğrulanacak.
