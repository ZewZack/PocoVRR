package com.zewzack.pocovrr

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.zewzack.pocovrr.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        var instance: MainActivity? = null
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Tam ekran (Edge-to-Edge) ayarları
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        instance = this

        settingsManager = SettingsManager(this)

        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    // ═══════════════════════════════════════════════════════════════════
    // UI Kurulumu
    // ═══════════════════════════════════════════════════════════════════

    private fun setupUI() {
        // Idle timeout butonlarını ayarla
        selectIdleButton(settingsManager.idleTimeoutSeconds)

        // Min Hz butonlarını ayarla
        selectMinHzButton(settingsManager.minHz)

        // Max Hz butonlarını ayarla
        selectMaxHzButton(settingsManager.maxHz)

        // Deneysel algılama switch
        binding.switchExperimental30Hz.isChecked = settingsManager.experimental30HzEnabled
        binding.btnMin30.visibility = if (settingsManager.experimental30HzEnabled) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun setupListeners() {
        // Erişilebilirlik ayarları butonu
        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Otomatik Başlatma butonu
        binding.btnOpenAutoStart.setOnClickListener {
            try {
                val intent = Intent()
                intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    startActivity(intent)
                } catch (e2: Exception) {}
            }
        }

        // Arkaplan Kısıtlaması butonu
        binding.btnOpenBatteryOptimization.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.fromParts("package", packageName, null)
            startActivity(intent)
        }

        // Idle timeout butonları
        binding.btnIdle1.setOnClickListener { setIdleTimeout(1) }
        binding.btnIdle2.setOnClickListener { setIdleTimeout(2) }
        binding.btnIdle3.setOnClickListener { setIdleTimeout(3) }

        // Min Hz butonları
        binding.btnMin30.setOnClickListener { setMinHz(30) }
        binding.btnMin60.setOnClickListener { setMinHz(60) }
        binding.btnMin90.setOnClickListener { setMinHz(90) }
        binding.btnMin120.setOnClickListener { setMinHz(120) }

        // Max Hz butonları
        binding.btnMax60.setOnClickListener { setMaxHz(60) }
        binding.btnMax90.setOnClickListener { setMaxHz(90) }
        binding.btnMax120.setOnClickListener { setMaxHz(120) }

        // Deneysel ayar switch
        binding.switchExperimental30Hz.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.experimental30HzEnabled = isChecked
            binding.btnMin30.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            
            // Eğer kapatıldı ve minHz 30 idi ise ayarlayıcı onu 60 yaptı, UI'u da güncelleyelim.
            if (!isChecked && settingsManager.minHz == 60) {
                selectMinHzButton(60)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Ayar Değişiklik İşlemleri
    // ═══════════════════════════════════════════════════════════════════

    private fun setIdleTimeout(seconds: Int) {
        settingsManager.idleTimeoutSeconds = seconds
        selectIdleButton(seconds)
    }

    private fun setMinHz(hz: Int) {
        // min <= max kuralını kontrol et
        if (hz > settingsManager.maxHz) {
            // Uyarı: min Hz, max Hz'den büyük olamaz
            return
        }
        settingsManager.minHz = hz
        selectMinHzButton(hz)
    }

    private fun setMaxHz(hz: Int) {
        // max >= min kuralını kontrol et
        if (hz < settingsManager.minHz) {
            // Uyarı: max Hz, min Hz'den küçük olamaz
            return
        }
        settingsManager.maxHz = hz
        selectMaxHzButton(hz)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Buton Seçim Durumları
    // ═══════════════════════════════════════════════════════════════════

    private fun selectIdleButton(seconds: Int) {
        binding.btnIdle1.isSelected = seconds == 1
        binding.btnIdle2.isSelected = seconds == 2
        binding.btnIdle3.isSelected = seconds == 3
    }

    private fun selectMinHzButton(hz: Int) {
        binding.btnMin30.isSelected = hz == 30
        binding.btnMin60.isSelected = hz == 60
        binding.btnMin90.isSelected = hz == 90
        binding.btnMin120.isSelected = hz == 120
    }

    private fun selectMaxHzButton(hz: Int) {
        binding.btnMax60.isSelected = hz == 60
        binding.btnMax90.isSelected = hz == 90
        binding.btnMax120.isSelected = hz == 120
    }

    // ═══════════════════════════════════════════════════════════════════
    // Durum Güncelleme
    // ═══════════════════════════════════════════════════════════════════

    fun updateStatus(hz: Int) {
        binding.tvCurrentHz.text = "$hz Hz"
        val maxHz = settingsManager.maxHz
        binding.tvStatus.text = if (hz == maxHz) "⚡ Aktif (hareket algılandı)" else "💤 Beklemede"
    }

    private fun refreshUI() {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        ).any { it.id.contains(packageName) }

        if (enabled) {
            binding.tvServiceStatus.text = "✅ Servis Aktif"
            binding.tvCurrentHz.text     = "${RefreshRateService.currentHz} Hz"
        } else {
            binding.tvServiceStatus.text = "❌ Servis Kapalı — Aşağıdan Aç"
            binding.tvCurrentHz.text     = "— Hz"
        }

        // Ayarları güncelle
        setupUI()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
