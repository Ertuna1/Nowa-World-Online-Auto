@file:Suppress("DEPRECATION")

package com.myadbclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import android.util.Log
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import kotlin.random.Random

class ClickerAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    // --- YENİ DEĞİŞKENLER (Sürekli Tıklama için) ---
    private val rapidTapHandler = Handler(Looper.getMainLooper())
    private var rapidTapRunnable: Runnable? = null
    // ---

    // ADD THESE REUSABLE OBJECTS - they work exactly the same, just reused
    private val reusablePath = Path()
    private val reusableDoubleTapPath1 = Path()
    private val reusableDoubleTapPath2 = Path()
    
    // SMART CLEANUP MECHANISM VARIABLES
    private var rapidTapCount = 0
    private val tapCountResetInterval = 100 // Every 100 taps
    private val cleanupDelayMs = 1000L // 1 second cleanup delay

    companion object {
        private const val TAG = "ClickerA11yService"
        var instance: ClickerAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "ClickerAccessibilityService bağlandı.")
        
        // ServiceMonitor'u başlat
        val intent = Intent(this, ServiceMonitor::class.java)
        startService(intent)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun handleActionIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val delay = intent.getLongExtra(AdbClickReceiver.EXTRA_DELAY, 0L)

        Log.d(TAG, "Doğrudan komut alındı: $action")

        handler.postDelayed({
            when (action) {
                // ... Diğer case'ler (TAP, DOUBLE_TAP vb.) aynı kalır ...
                AdbClickReceiver.ACTION_PERFORM_DOUBLE_TAP -> {
                    val x = intent.getIntExtra(AdbClickReceiver.EXTRA_X_COORD, -1)
                    val y = intent.getIntExtra(AdbClickReceiver.EXTRA_Y_COORD, -1)
                    val interClickDelay = intent.getLongExtra(AdbClickReceiver.EXTRA_INTER_CLICK_DELAY, 50L)
                    val randomness = intent.getIntExtra(AdbClickReceiver.EXTRA_RANDOMNESS, 0)
                    if (x != -1 && y != -1) {
                        performDoubleTap(x.toFloat(), y.toFloat(), interClickDelay, randomness)
                    }
                }

                // --- YENİ MANTIK ---
                AdbClickReceiver.ACTION_START_RAPID_TAP -> {
                    val x = intent.getIntExtra(AdbClickReceiver.EXTRA_X_COORD, -1)
                    val y = intent.getIntExtra(AdbClickReceiver.EXTRA_Y_COORD, -1)
                    val interval = intent.getLongExtra(AdbClickReceiver.EXTRA_INTERVAL, 25L) // Varsayılan 25ms
                    val randomness = intent.getIntExtra(AdbClickReceiver.EXTRA_RANDOMNESS, 5)
                    if (x != -1 && y != -1) {
                        startRapidTap(x.toFloat(), y.toFloat(), interval, randomness)
                    }
                }
                AdbClickReceiver.ACTION_STOP_RAPID_TAP -> {
                    stopRapidTap()
                }
                // ---
            }
        }, delay)
    }

    // --- YENİ METOTLAR ---
    @RequiresApi(Build.VERSION_CODES.N)
    private fun startRapidTap(x: Float, y: Float, interval: Long, randomness: Int) {
        stopRapidTap() // Stop any existing rapid tap
        rapidTapCount = 0 // Reset tap counter
        Log.d(TAG, "Sürekli tıklama başlatılıyor: ($x, $y), aralık: $interval ms")

        // Overlay'i göster
        showTapOverlay(x, y)

        rapidTapRunnable = object : Runnable {
            override fun run() {
                // Perform the tap
                performTap(x, y, randomness)
                rapidTapCount++
                
                // Smart cleanup mechanism
                if (rapidTapCount % tapCountResetInterval == 0) {
                    Log.d(TAG, "$tapCountResetInterval tap tamamlandı, 1 saniye bellek optimizasyonu...")
                    
                    // Give GC a significant pause to clean up by adding 1 second delay
                    rapidTapHandler.postDelayed(this, interval + cleanupDelayMs)
                    
                    // Reset counter to prevent integer overflow
                    rapidTapCount = 0
                } else {
                    // Normal rapid tap interval
                    rapidTapHandler.postDelayed(this, interval)
                }
            }
        }
        
        // Start the rapid tap loop immediately
        rapidTapHandler.post(rapidTapRunnable!!)
    }

    private fun stopRapidTap() {
        rapidTapRunnable?.let {
            rapidTapHandler.removeCallbacks(it)
            Log.d(TAG, "Sürekli tıklama durduruldu.")
            
            // Overlay'i gizle
            hideTapOverlay()
        }
        rapidTapRunnable = null
        rapidTapCount = 0 // Reset counter when stopping
        // ADD ONLY THIS LINE - extra safety cleanup, won't break functionality
        rapidTapHandler.removeCallbacksAndMessages(null)
    }
    // ---

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopRapidTap() // Servis kapanırken döngüyü durdur
        rapidTapHandler.removeCallbacksAndMessages(null) // ADD THIS LINE - only cleans on destroy
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "ClickerAccessibilityService yok edildi.")
        
        // ServiceMonitor'u durdur
        val intent = Intent(this, ServiceMonitor::class.java)
        stopService(intent)
    }

    // ... performTap, performDoubleTap gibi diğer metotlar aynı kalır ...

    @RequiresApi(Build.VERSION_CODES.N)
    private fun performTap(x: Float, y: Float, randomness: Int) {
        val randomizedX = getRandomizedCoordinate(x, randomness)
        val randomizedY = getRandomizedCoordinate(y, randomness)
        
        // REUSE PATH - same visual result, less memory allocation
        reusablePath.reset() // Clear previous path data
        reusablePath.moveTo(randomizedX, randomizedY) // Same coordinates as before
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(reusablePath, 0, 10L)) // Same 10ms duration
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                // Removed excessive logging during rapid tap to reduce memory pressure
                // Functionality is identical, just less log spam
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "ADB TAP hareketi iptal edildi.") // Keep error logging
            }
        }, handler)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun performDoubleTap(x: Float, y: Float, interClickDelay: Long, randomness: Int) {
        val randomizedX1 = getRandomizedCoordinate(x, randomness)
        val randomizedY1 = getRandomizedCoordinate(y, randomness)
        val randomizedX2 = getRandomizedCoordinate(x, randomness)
        val randomizedY2 = getRandomizedCoordinate(y, randomness)
        val clickDuration = 10L // Same timing as before

        // REUSE PATHS - same coordinates and timing, less memory allocation
        reusableDoubleTapPath1.reset()
        reusableDoubleTapPath1.moveTo(randomizedX1, randomizedY1) // Same first tap
        
        reusableDoubleTapPath2.reset()
        reusableDoubleTapPath2.moveTo(randomizedX2, randomizedY2) // Same second tap
        
        // Same stroke timing and duration as original
        val stroke1 = GestureDescription.StrokeDescription(reusableDoubleTapPath1, 0, clickDuration)
        val stroke2 = GestureDescription.StrokeDescription(reusableDoubleTapPath2, clickDuration + interClickDelay, clickDuration)

        val gesture = GestureDescription.Builder().addStroke(stroke1).addStroke(stroke2).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "ADB DOUBLE_TAP hareketi tamamlandı: ($x,$y)") // Keep success logging
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "ADB DOUBLE_TAP hareketi iptal edildi.") // Keep error logging
            }
        }, handler)
    }

    private fun getRandomizedCoordinate(coord: Float, randomness: Int): Float {
        if (randomness <= 0) return coord
        val offset = Random.nextInt(-randomness, randomness + 1)
        return coord + offset
    }

    private fun showTapOverlay(x: Float, y: Float) {
        try {
            val intent = Intent(this, TapOverlayService::class.java).apply {
                action = "SHOW_OVERLAY"
                putExtra("x", x)
                putExtra("y", y)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Overlay gösterme hatası: ${e.message}")
        }
    }

    private fun hideTapOverlay() {
        try {
            val intent = Intent(this, TapOverlayService::class.java).apply {
                action = "HIDE_OVERLAY"
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Overlay gizleme hatası: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
