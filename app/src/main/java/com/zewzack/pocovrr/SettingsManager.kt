package com.zewzack.pocovrr

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences ile kullanıcı ayarlarını yönetir.
 * Idle timeout, min/max Hz, medya algılama durumu.
 */
class SettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "pocovrr_settings"

        private const val KEY_IDLE_TIMEOUT  = "idle_timeout_seconds"
        private const val KEY_MIN_HZ        = "min_hz"
        private const val KEY_MAX_HZ        = "max_hz"
        private const val KEY_EXP_30HZ      = "experimental_30hz_enabled"

        // LTPS panelin kabul ettiği Hz değerleri
        val VALID_HZ_VALUES = intArrayOf(30, 60, 90, 120)
        val VALID_IDLE_VALUES = intArrayOf(1, 2, 3)

        const val DEFAULT_IDLE_TIMEOUT = 2
        const val DEFAULT_MIN_HZ      = 60
        const val DEFAULT_MAX_HZ      = 120
        const val DEFAULT_EXP_30HZ    = false
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Idle Timeout (saniye) ───────────────────────────────────

    var idleTimeoutSeconds: Int
        get() = prefs.getInt(KEY_IDLE_TIMEOUT, DEFAULT_IDLE_TIMEOUT)
        set(value) {
            if (value in VALID_IDLE_VALUES) {
                prefs.edit().putInt(KEY_IDLE_TIMEOUT, value).apply()
                onSettingsChanged?.invoke()
            }
        }

    val idleTimeoutMs: Long
        get() = idleTimeoutSeconds * 1000L

    // ─── Minimum Hz ─────────────────────────────────────────────

    var minHz: Int
        get() = prefs.getInt(KEY_MIN_HZ, DEFAULT_MIN_HZ)
        set(value) {
            if (value in VALID_HZ_VALUES && value <= maxHz) {
                prefs.edit().putInt(KEY_MIN_HZ, value).apply()
                onSettingsChanged?.invoke()
            }
        }

    // ─── Maximum Hz ─────────────────────────────────────────────

    var maxHz: Int
        get() = prefs.getInt(KEY_MAX_HZ, DEFAULT_MAX_HZ)
        set(value) {
            if (value in VALID_HZ_VALUES && value >= minHz) {
                prefs.edit().putInt(KEY_MAX_HZ, value).apply()
                onSettingsChanged?.invoke()
            }
        }

    // ─── Deneysel Ayarlar ───────────────────────────────────────

    var experimental30HzEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXP_30HZ, DEFAULT_EXP_30HZ)
        set(value) {
            prefs.edit().putBoolean(KEY_EXP_30HZ, value).apply()
            // Eğer deneysel özellik kapatılırsa ve minHz 30 ise, güvenli değere (60) döndür
            if (!value && minHz == 30) {
                minHz = 60
            }
            onSettingsChanged?.invoke()
        }

    // ─── Ayar Değişikliği Callback ──────────────────────────────

    var onSettingsChanged: (() -> Unit)? = null
}
