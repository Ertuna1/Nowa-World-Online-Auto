package com.myadbclicker

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager

class ServiceMonitor : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 5000L // 5 saniyede bir kontrol et
    private var isMonitoring = false
    
    // ADD THESE FOR MEMORY OPTIMIZATION
    private var accessibilityManager: AccessibilityManager? = null
    private var lastServiceState = false // Cache last known state

    companion object {
        private const val TAG = "ServiceMonitor"
        private var instance: ServiceMonitor? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // INITIALIZE ONCE INSTEAD OF EVERY CHECK
        accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        Log.d(TAG, "ServiceMonitor oluşturuldu")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ServiceMonitor başlatıldı")
        startForeground()
        startMonitoring()
        return START_STICKY // Servis çökerse otomatik olarak yeniden başlat
    }

    private fun startForeground() {
        // Foreground servis olarak çalıştır (Android 8.0+ için gerekli)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelId = "service_monitor_channel"
            val channelName = "Service Monitor"
            val channel = android.app.NotificationChannel(
                channelId,
                channelName,
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            
            val notification = android.app.Notification.Builder(this, channelId)
                .setContentTitle("My ADB Clicker")
                .setContentText("Servis izleme aktif")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
            
            startForeground(1, notification)
        }
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        Log.d(TAG, "Erişilebilirlik servisi izleme başlatıldı")
        
        handler.post(object : Runnable {
            override fun run() {
                checkAccessibilityService()
                if (isMonitoring) {
                    handler.postDelayed(this, checkInterval)
                }
            }
        })
    }

    private fun checkAccessibilityService() {
        // Use cached accessibilityManager instead of getting it every time
        val enabledServices = accessibilityManager?.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        ) ?: emptyList()
        
        val isServiceEnabled = enabledServices.any { service ->
            service.resolveInfo.serviceInfo.packageName == packageName &&
            service.resolveInfo.serviceInfo.name == ClickerAccessibilityService::class.java.name
        }
        
        val isServiceRunning = ClickerAccessibilityService.instance != null
        
        // ONLY LOG WHEN STATE CHANGES TO REDUCE LOG SPAM
        val currentState = isServiceEnabled && isServiceRunning
        if (currentState != lastServiceState) {
            Log.d(TAG, "Servis durumu değişti - Etkin: $isServiceEnabled, Çalışıyor: $isServiceRunning")
            lastServiceState = currentState
        }
        
        // Keep the original restart logic
        if (isServiceEnabled && !isServiceRunning) {
            Log.w(TAG, "Erişilebilirlik servisi çöktü! Yeniden başlatılıyor...")
            restartAccessibilityService()
        }
    }

    private fun restartAccessibilityService() {
        try {
            // Servisi yeniden başlatmak için Intent gönder
            val intent = Intent(this, ClickerAccessibilityService::class.java)
            startService(intent)
            Log.d(TAG, "Erişilebilirlik servisi yeniden başlatma isteği gönderildi")
        } catch (e: Exception) {
            Log.e(TAG, "Servis yeniden başlatma hatası: ${e.message}")
        }
    }
    
    // ADD PROPER CLEANUP ONLY ON DESTROY (MONITORING CONTINUES UNTIL THEN)
    private fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Monitoring stopped and handlers cleared")
    }

    // ONLY STOP MONITORING WHEN SERVICE IS ACTUALLY DESTROYED
    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring() // Only stop when service is destroyed
        accessibilityManager = null // Release reference
        instance = null
        Log.d(TAG, "ServiceMonitor yok edildi")
    }
    
    // DON'T STOP MONITORING ON TASK REMOVED - LET IT CONTINUE IN BACKGROUND
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed but monitoring continues")
        // DON'T call stopMonitoring() here - let it keep running
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 