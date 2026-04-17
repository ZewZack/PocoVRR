# PocoVRR — Poco X8 Pro Max / HyperOS 3 VRR Motoru

[![Developer](https://img.shields.io/badge/Developer-@zewzack-white?style=flat-square)](https://github.com/zewzack)
[![Platform](https://img.shields.io/badge/Platform-Android_14+_HyperOS-black?style=flat-square)](https://github.com/zewzack/PocoVRR)

PocoVRR, Poco X8 Pro Max (LTPS Panel) ve HyperOS cihazlar için özel olarak geliştirilmiş, akıllı ve optimize edilmiş bir **Yenileme Hızı (VRR) Kontrolcüsü**dür. 

## 🚀 Öne Çıkan Özellikler

- **Akıllı Geçiş:** Parmak ekrana değdiği an 120 Hz, hareketsizlikte belirlediğiniz süre sonunda (1s-3s) Min Hz (60, 90, 120).
- **Powerkeeper Koruması:** Sistem servislerinin (Powerkeeper) yenileme hızını düşürmesini engelleyen **Guard Loop** ve **ContentObserver** mekanizması.
- **Tam Ekran Deneyimi:** Tamamen siyah ve uçtan uca (Edge-to-Edge) modern arayüz tasarımı.
- **Düşük Gecikme:** Erişilebilirlik servisleri üzerinden dokunma ve kaydırma eventlerini anlık yakalama.

---

## 🛠️ Kurulum ve Kullanım

Uygulamanın stabil çalışması için aşağıdaki adımları sırasıyla uygulayın:

### 1. Erişilebilirlik Servisi
Uygulama içindeki butonu kullanarak **Erişilebilirlik Ayarları > İndirilen Uygulamalar > PocoVRR** yolunu izleyin ve servisi aktif edin.

### 2. Otomatik Başlatma (Auto-Start)
HyperOS'un uygulamayı kapatmaması için "Arka Planda Otomatik Başlatma" iznini verin.

### 3. Pil Optimizasyonu (Kısıtlama Yok)
"Arkaplan Kısıtlamasını Kaldır" butonuyla uygulama bilgisi ekranına gidin ve Pil Tasarrufu ayarını **"Kısıtlama Yok"** olarak değiştirin. Bu, servisin sürekli aktif kalması için kritiktir.

---

## ⚙️ Uygulama Ayarları

- **Minimum Hz:** 30 (Deneysel), 60, 90, 120. (Pil tasarrufu ve akıcılık dengesi)
- **Maximum Hz:** 60, 90, 120. (Etkileşim anındaki hız)
- **Bekleme Süresi (Idle):** Hareketsizlikten kaç saniye sonra Hz düşürüleceği (1s, 2s, 3s).

---

## 🖥️ Teknik Detaylar

PocoVRR, HyperOS'un özel secure settings anahtarlarını kontrol eder:
- `miui_refresh_rate`
- `user_refresh_rate`

Uygulama geçişlerinde veya sistem müdahalelerinde 500ms boyunca her 100ms'de bir değeri kontrol eden ve hedef değerde tutan bir **Guard** mekanizmasına sahiptir.

---

## ⚠️ Önemli Notlar

- Bu uygulama sadece LTPS panel destekleyen cihazlarda (Poco X8 Pro Max vb.) tam verimle çalışır.
- Minimum Hz değerini 120 seçerseniz, cihaz tüm uygulamalarda sürekli 120 Hz modunda kalacaktır.

---

## 👨‍💻 Developer
**@zewzack**

---

*Bu proje açık kaynak olarak paylaşılmıştır. Herhangi bir batarya veya ekran süresi sorunu oluşturmaz, aksine statik görüntülerde Hz düşürerek pil ömrüne katkı sağlar.*
