# PocoVRR — Kurulum Rehberi
### Poco X8 Pro Max / HyperOS 3
### Developer: zewzack

---

## Nasıl Çalışır?

| Durum | Refresh Rate |
|-------|-------------|
| Ekran hareketsiz | Min Hz (varsayılan 60 Hz, pil tasarrufu) |
| Kaydırma / dokunuş / hareket | Max Hz (varsayılan 120 Hz, akıcılık) |
| Bekleme süresi dolunca | tekrar Min Hz |
| Video/müzik oynatılırken | Min Hz'de kalır |
| Klavye açıkken | Min Hz'de kalır |

HyperOS'un gerçek anahtarları: `miui_refresh_rate` + `user_refresh_rate`  
Standard Android `peak_refresh_rate` bu cihazda yedek olarak da yazılır.

### Powerkeeper Koruması
PocoVRR, HyperOS Powerkeeper servisinin değerleri geri yazmasını **ContentObserver** ve **Guard Loop** mekanizmalarıyla engeller. Uygulama geçişlerinde 500ms boyunca değerler korunur.

### Medya/Klavye Algılama
- **Medya**: `AudioManager.isMusicActive()` ile algılanır (uygulama ismine bağımlı değil)
- **Klavye**: `AccessibilityWindowInfo.TYPE_INPUT_METHOD` ile algılanır

---

## Kurulum Adımları

### 1. Android Studio'da Aç
- Android Studio'yu aç → "Open an Existing Project" → `AdaptiveHz` (PocoVRR) klasörünü seç
- Gradle sync tamamlanmasını bekle

### 2. APK Derle ve Yükle
```
Build → Generate Signed Bundle/APK → APK → Debug
```
veya direkt telefona:
```
Run → Run 'app'  (USB bağlıyken)
```

### 3. ADB ile İzin Ver (TEK SEFERLIK)
```bash
adb shell pm grant com.zewzack.pocovrr android.permission.WRITE_SECURE_SETTINGS
```
Bu izin uygulama silinip yeniden kurulana kadar kalıcıdır.

### 4. Erişilebilirlik Servisini Aç
- Telefonda: **Ayarlar → Erişilebilirlik → Yüklü uygulamalar**
- **PocoVRR** → Aç
- "İzin ver" de

### 5. Pil Optimizasyonunu Kapat (ÖNEMLİ)
- **Ayarlar → Uygulamalar → Arka planda otomatik başlatma** → PocoVRR'yi seç
- **Uygulama ayarları → Pil tasarrufu → Kısıtlama Yok** olarak ayarla
- Bu sayede erişilebilirlik servisi arka planda öldürülmez

---

## Uygulama İçi Ayarlar

| Ayar | Seçenekler | Varsayılan |
|------|-----------|-----------|
| Bekleme Süresi (idle) | 1s / 2s / 3s | 2s |
| Minimum Hz | 30 / 60 / 90 | 60 |
| Maximum Hz | 60 / 90 / 120 | 120 |
| Medya Algılama | Açık / Kapalı | Açık |

> **Not**: Minimum Hz her zaman Maximum Hz'den küçük olmalıdır.
> **Uyarı**: 30 Hz minimum seçeneği bazı uygulamalarda kaydırma başlangıcında kısa süreli takılma hissi verebilir.

---

## Doğrulama

Servis çalışıyorsa:
1. **Geliştirici Seçenekleri → Ekran Yenileme Hızını Göster** aç
2. Twitter/WhatsApp'ta kaydır → Max Hz görüyorsan ✅
3. Dur → bekleme süresi sonra Min Hz'e düşüyorsa ✅
4. YouTube Shorts'ta sesli video izle → Min Hz'de kalıyorsa ✅
5. WhatsApp'ta klavye aç → Min Hz'de kalıyorsa ✅

---

## Sorun Giderme

**"Operation not permitted" hatası:**
```bash
# İzin verilmiş mi kontrol et
adb shell dumpsys package com.zewzack.pocovrr | grep WRITE_SECURE
```

**Servis devre dışı kalıyorsa:**  
Ayarlar → Pil → Uygulama → PocoVRR → "Kısıtlama Yok" yap

**Değerler değişmiyor:**
```bash
adb shell settings get secure miui_refresh_rate
adb shell settings get secure user_refresh_rate
```

---

## Geçerli Hz Değerleri (LTPS Panel)

Bu cihazda sadece şu değerler kabul edilir:
- `30` Hz
- `60` Hz  ← varsayılan minimum
- `90` Hz
- `120` Hz ← varsayılan maximum

100, 144 gibi değerler çalışmaz.

---

## Kaynak Kod — Mimari Özet

```
AccessibilityService
  ├── TYPE_VIEW_SCROLLED          ┐
  ├── TYPE_TOUCH_INTERACTION_START├─→ Etkileşim → maxHz
  ├── TYPE_VIEW_CLICKED           │
  ├── TYPE_VIEW_FOCUSED           │
  ├── TYPE_WINDOW_CONTENT_CHANGED │
  └── TYPE_WINDOW_STATE_CHANGED   ┘
          │
          ├── Klavye açık?     → minHz'de kal
          ├── Medya oynatılıyor? → minHz'de kal
          │
          ▼ [idle süresi sonra]
    setTargetHz(minHz)
          │
    ┌─────▼──────────────────────┐
    │  ContentObserver            │
    │  Powerkeeper override →    │
    │  geri yaz                  │
    └────────────────────────────┘
          │
    ┌─────▼──────────────────────┐
    │  Guard Loop (500ms)        │
    │  Her 100ms: kontrol →      │
    │  değer sapması → geri yaz  │
    └────────────────────────────┘
```

---

## Dosya Yapısı

```
com.zewzack.pocovrr/
  ├── MainActivity.kt          — UI + Ayarlar
  ├── RefreshRateService.kt     — VRR motoru (ContentObserver, Guard, Algılama)
  ├── SettingsManager.kt        — SharedPreferences yöneticisi
  └── MediaStateDetector.kt     — Medya algılama (AudioManager)
```
