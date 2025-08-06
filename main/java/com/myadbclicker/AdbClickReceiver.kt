package com.myadbclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

class AdbClickReceiver : BroadcastReceiver() {

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "ADB isteği alındı: ${intent.action}")

        val service = ClickerAccessibilityService.instance
        if (service != null) {
            service.handleActionIntent(intent)
            Log.d(TAG, "İstek doğrudan ClickerAccessibilityService'e iletildi.")
        } else {
            Log.e(TAG, "HATA: ClickerAccessibilityService aktif değil. Tıklama yapılamadı.")
        }
    }

    companion object {
        private const val TAG = "AdbClickReceiver"

        const val ADB_BASE_ACTION = "klickr"
        const val ACTION_PERFORM_TAP = "$ADB_BASE_ACTION.TAP"
        const val ACTION_PERFORM_DOUBLE_TAP = "$ADB_BASE_ACTION.DOUBLE_TAP"
        const val ACTION_PERFORM_SWIPE = "$ADB_BASE_ACTION.SWIPE"
        const val ACTION_PERFORM_LONG_PRESS = "$ADB_BASE_ACTION.LONG_PRESS"
        // --- YENİ EYLEM SABİTLERİ ---
        const val ACTION_START_RAPID_TAP = "$ADB_BASE_ACTION.START_RAPID_TAP"
        const val ACTION_STOP_RAPID_TAP = "$ADB_BASE_ACTION.STOP_RAPID_TAP"

        const val EXTRA_X_COORD = "x"
        const val EXTRA_Y_COORD = "y"
        const val EXTRA_X1_COORD = "x1"
        const val EXTRA_Y1_COORD = "y1"
        const val EXTRA_X2_COORD = "x2"
        const val EXTRA_Y2_COORD = "y2"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_DELAY = "delay"
        const val EXTRA_RANDOMNESS = "randomness"
        const val EXTRA_INTER_CLICK_DELAY = "inter_click_delay"
        // --- YENİ EKSTRA SABİTİ ---
        const val EXTRA_INTERVAL = "interval" // Sürekli tıklama için aralık (ms)
    }
}
