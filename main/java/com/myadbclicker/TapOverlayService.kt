package com.myadbclicker

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.graphics.drawable.GradientDrawable
import android.os.Build

class TapOverlayService : Service() {
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    
    companion object {
        private const val TAG = "TapOverlayService"
        private const val OVERLAY_SIZE = 40 // dp
        var instance: TapOverlayService? = null
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Log.d(TAG, "TapOverlayService oluşturuldu")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        when (action) {
            "SHOW_OVERLAY" -> {
                val x = intent.getFloatExtra("x", 0f)
                val y = intent.getFloatExtra("y", 0f)
                showOverlay(x, y)
            }
            "HIDE_OVERLAY" -> {
                hideOverlay()
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun showOverlay(x: Float, y: Float) {
        if (overlayView != null) {
            hideOverlay()
        }
        
        try {
            // Overlay view oluştur
            overlayView = FrameLayout(this).apply {
                // Kırmızı daire background
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFFF0000.toInt()) // Tamamen kırmızı
                    // Yarı saydam yapma - tamamen opak
                }
                background = drawable
            }
            
            // Dp'yi pixel'e çevir
            val density = resources.displayMetrics.density
            val sizeInPixels = (OVERLAY_SIZE * density).toInt()
            
            // WindowManager parametreleri
            val params = WindowManager.LayoutParams().apply {
                // Android 8.0+ için TYPE_APPLICATION_OVERLAY kullan
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                
                format = PixelFormat.TRANSLUCENT
                width = sizeInPixels
                height = sizeInPixels
                gravity = Gravity.TOP or Gravity.START
                
                // Overlay'i tap pozisyonuna yerleştir (merkezle)
                this.x = (x - sizeInPixels / 2).toInt()
                this.y = (y - sizeInPixels / 2).toInt()
            }
            
            // Overlay'i ekle
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Overlay gösterildi: ($x, $y)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Overlay gösterme hatası: ${e.message}")
        }
    }
    
    private fun hideOverlay() {
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
                overlayView = null
                Log.d(TAG, "Overlay gizlendi")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Overlay gizleme hatası: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        instance = null
        Log.d(TAG, "TapOverlayService yok edildi")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
} 