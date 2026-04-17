package com.zewzack.pocovrr

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * PocoVRR — Poco X8 Pro Max (LTPS / HyperOS 3) için VRR motoru.
 *
 * Yalnızca gerçek dokunma ve kaydırma eventlerine tepki verir.
 *
 * Mimari:
 * 1. TYPE_TOUCH_INTERACTION_START / ACTION_OUTSIDE → parmak ekrana değdi → maxHz
 * 2. TYPE_VIEW_SCROLLED → kaydırma yapıldı → maxHz
 * 3. Idle timeout → minHz'e dön
 * 4. ContentObserver → Powerkeeper override algılama + geri yazma
 * 5. Guard Loop → Geçişlerde değeri koruma
 * 6. Accessibility Overlay → Her türlü dokunmatik ekran etkileşimini algılama
 */
class RefreshRateService : AccessibilityService() {

    companion object {
        private const val TAG = "PocoVRR"

        // HyperOS secure settings anahtarları
        private const val KEY_MIUI_REFRESH   = "miui_refresh_rate"
        private const val KEY_USER_REFRESH   = "user_refresh_rate"
        // Android standart anahtarlar (yedek)
        private const val KEY_PEAK_REFRESH   = "peak_refresh_rate"
        private const val KEY_MIN_REFRESH    = "min_refresh_rate"

        // Guard mekanizması
        private const val GUARD_INTERVAL_MS  = 100L
        private const val GUARD_COUNT        = 5       // 5 × 100ms = 500ms koruma

        var instance: RefreshRateService? = null
            private set
        var currentHz = 60
            private set
        var isRunning = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settingsManager: SettingsManager

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    // Hedef Hz — ContentObserver ve Guard bu değere göre koruma yapar
    private var targetHz = 60
    private var isHighHz = false

    // ContentObserver'ın kendi yazdığımız değişikliği yok sayması için
    @Volatile
    private var selfWriteFlag = false

    // Guard sayacı
    private var guardCount = 0

    // ─── ContentObserver: Powerkeeper'ın override etmesini algıla ────

    private val refreshRateObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (selfWriteFlag) return  // Kendi yazdığımız değişiklik

            // Powerkeeper mevcut değeri değiştirdi mi kontrol et
            handler.postDelayed({
                val miuiVal = readSecureInt(KEY_MIUI_REFRESH, targetHz)
                val userVal = readSecureInt(KEY_USER_REFRESH, targetHz)

                if (miuiVal != targetHz || userVal != targetHz) {
                    Log.d(TAG, "Powerkeeper override algılandı: miui=$miuiVal, user=$userVal → geri yazılıyor: $targetHz")
                    applyHz(targetHz)
                }
            }, 50) // 50ms debounce
        }
    }

    // ─── Idle Runnable: Hareketsizlik sonrası minHz'e dön ────────────

    private val idleRunnable = Runnable {
        val minHz = settingsManager.minHz
        setTargetHz(minHz)
        isHighHz = false
        broadcastState()
    }

    // ─── Guard Runnable: Geçişlerde değeri koru ─────────────────────

    private val guardRunnable = object : Runnable {
        override fun run() {
            val miuiVal = readSecureInt(KEY_MIUI_REFRESH, targetHz)
            val userVal = readSecureInt(KEY_USER_REFRESH, targetHz)

            if (miuiVal != targetHz || userVal != targetHz) {
                Log.d(TAG, "Guard: değer sapması → geri yazılıyor: $targetHz")
                applyHz(targetHz)
            }

            guardCount++
            if (guardCount < GUARD_COUNT) {
                handler.postDelayed(this, GUARD_INTERVAL_MS)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Servis Yaşam Döngüsü
    // ═══════════════════════════════════════════════════════════════════

    override fun onServiceConnected() {
        instance = this
        isRunning = true

        settingsManager = SettingsManager(this)

        // Ayar değişikliklerini dinle
        settingsManager.onSettingsChanged = {
            Log.d(TAG, "Ayarlar değişti: idle=${settingsManager.idleTimeoutSeconds}s, " +
                    "min=${settingsManager.minHz}, max=${settingsManager.maxHz}")
        }

        // Accessibility service yapılandırması
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = (
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START   or  // Parmak ekrana değdi
                AccessibilityEvent.TYPE_VIEW_SCROLLED             or  // Kaydırma yapıldı
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED      or  // Pencere değişti
                AccessibilityEvent.TYPE_WINDOWS_CHANGED               // Ekran güncellendi
            )
            feedbackType        = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags               = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                                  AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 16L  // ~1 frame @ 60Hz
        }

        setupTouchOverlay()

        // ContentObserver kayıt
        registerObservers()

        // Başlangıçta minHz'e ayarla
        val minHz = settingsManager.minHz
        setTargetHz(minHz)
        broadcastState()

        Log.d(TAG, "Servis başlatıldı: min=${settingsManager.minHz}, max=${settingsManager.maxHz}, " +
                "idle=${settingsManager.idleTimeoutSeconds}s")
    }

    // ═══════════════════════════════════════════════════════════════════
    // Accessibility Events — SADECE dokunma ve kaydırma
    // ═══════════════════════════════════════════════════════════════════

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Sadece ilgili eventleri işle
        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                handleUserInteraction()
            }
        }
    }

    /**
     * Kullanıcı ekrana dokundu veya kaydırdı.
     */
    private fun handleUserInteraction() {
        // Idle timer'ı sıfırla
        handler.removeCallbacks(idleRunnable)

        val maxHz = settingsManager.maxHz

        // Normal etkileşim → maxHz'e çık
        if (!isHighHz) {
            setTargetHz(maxHz)
            isHighHz = true
            broadcastState()
        }

        // Idle timer'ı ayarla
        handler.postDelayed(idleRunnable, settingsManager.idleTimeoutMs)
    }

    /**
     * minHz'de olduğundan emin ol (zaten minHz'deyse tekrar yazma).
     */
    private fun ensureMinHz(minHz: Int) {
        if (isHighHz) {
            setTargetHz(minHz)
            isHighHz = false
            broadcastState()
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Hz Yazma ve Koruma
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Hedef Hz'i belirle, uygula ve guard başlat.
     */
    private fun setTargetHz(hz: Int) {
        targetHz = hz
        applyHz(hz)
        startGuard()
    }

    /**
     * Hz değerini tüm anahtarlara yaz.
     * selfWriteFlag ile ContentObserver'ın kendi yazımımızı yok sayması sağlanır.
     */
    private fun applyHz(hz: Int) {
        try {
            selfWriteFlag = true
            val cr: ContentResolver = contentResolver

            // HyperOS ana anahtarları (kritik)
            Settings.Secure.putInt(cr, KEY_MIUI_REFRESH, hz)
            Settings.Secure.putInt(cr, KEY_USER_REFRESH, hz)

            // Android standart anahtarları (yedek destek)
            try {
                Settings.System.putFloat(cr, KEY_PEAK_REFRESH, hz.toFloat())
                Settings.System.putFloat(cr, KEY_MIN_REFRESH, hz.toFloat())
            } catch (_: Exception) {
                // Bu anahtarlar her cihazda çalışmayabilir — yok say
            }

            currentHz = hz

            // selfWriteFlag'i kısa süre sonra kapat (observer'ın callback'i async gelir)
            handler.postDelayed({ selfWriteFlag = false }, 100)

        } catch (e: SecurityException) {
            Log.e(TAG, "WRITE_SECURE_SETTINGS izni verilmemiş!", e)
        }
    }

    /**
     * Guard mekanizması: 500ms boyunca her 100ms'de değeri kontrol et.
     * Powerkeeper override ederse geri yaz.
     */
    private fun startGuard() {
        handler.removeCallbacks(guardRunnable)
        guardCount = 0
        handler.postDelayed(guardRunnable, GUARD_INTERVAL_MS)
    }

    // ═══════════════════════════════════════════════════════════════════
    // ContentObserver Kayıt
    // ═══════════════════════════════════════════════════════════════════

    private fun registerObservers() {
        val cr = contentResolver

        // miui_refresh_rate değişikliklerini izle
        try {
            val miuiUri = Settings.Secure.getUriFor(KEY_MIUI_REFRESH)
            cr.registerContentObserver(miuiUri, false, refreshRateObserver)
        } catch (e: Exception) {
            Log.w(TAG, "miui_refresh_rate observer kaydedilemedi", e)
        }

        // user_refresh_rate değişikliklerini izle
        try {
            val userUri = Settings.Secure.getUriFor(KEY_USER_REFRESH)
            cr.registerContentObserver(userUri, false, refreshRateObserver)
        } catch (e: Exception) {
            Log.w(TAG, "user_refresh_rate observer kaydedilemedi", e)
        }
    }

    private fun unregisterObservers() {
        try {
            contentResolver.unregisterContentObserver(refreshRateObserver)
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    // Dokunmatik Overlay Katmanı
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Niagara Launcher veya sistem arayüzlerinde olan dokunuşları
     * ACTION_OUTSIDE ile global olarak yakalar. Sıfır boyutlu gizli bir viewdir.
     */
    private fun setupTouchOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = View(this).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    handleUserInteraction()
                }
                false
            }
        }

        val params = WindowManager.LayoutParams(
            0, 0, // width 0, height 0
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Touch Overlay eklenemedi: \${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Yardımcı
    // ═══════════════════════════════════════════════════════════════════

    private fun readSecureInt(key: String, default: Int): Int {
        return try {
            Settings.Secure.getInt(contentResolver, key)
        } catch (e: Settings.SettingNotFoundException) {
            default
        }
    }

    private fun broadcastState() {
        MainActivity.instance?.runOnUiThread {
            MainActivity.instance?.updateStatus(currentHz)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Servis Kapanış
    // ═══════════════════════════════════════════════════════════════════

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterObservers()

        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Overlay silinemedi: \${e.message}")
        }

        // Kapanırken maxHz'e geri dön
        val maxHz = if (::settingsManager.isInitialized) settingsManager.maxHz else 120
        applyHz(maxHz)

        isRunning = false
        instance = null
        super.onDestroy()
    }
}
