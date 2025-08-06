package com.myadbclicker

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean
import android.content.SharedPreferences

class ServiceMonitor : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val isMonitoring = AtomicBoolean(false)
    private lateinit var autonomousRecovery: AutonomousRecoverySystem
    private lateinit var prefs: SharedPreferences
    
    // Lightweight monitoring
    private val lightCheckInterval = 30000L // 30 seconds
    private var serviceStartTime = System.currentTimeMillis()
    private var totalUptime = 0L

    companion object {
        private const val TAG = "ServiceMonitor"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "service_monitor_channel"
        
        var instance: ServiceMonitor? = null
            public set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences("monitor_prefs", Context.MODE_PRIVATE)
        serviceStartTime = System.currentTimeMillis()
        
        // Initialize autonomous recovery system
        autonomousRecovery = AutonomousRecoverySystem.getInstance(this)
        
        Log.d(TAG, "ServiceMonitor oluşturuldu (Otonom kurtarma ile)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ServiceMonitor başlatıldı")
        
        // Handle specific restart requests
        handleRestartRequest(intent)
        
        // Start services
        startForeground()
        startLightweightMonitoring()
        
        // Start autonomous recovery
        autonomousRecovery.startAutonomousRecovery()
        
        return START_STICKY
    }

    private fun handleRestartRequest(intent: Intent?) {
        when {
            intent?.getBooleanExtra("gentle_restart", false) == true -> {
                Log.d(TAG, "Gentle restart isteği alındı")
                performGentleRestart()
            }
            intent?.getBooleanExtra("restart_accessibility", false) == true -> {
                Log.d(TAG, "Accessibility restart isteği alındı")
                // Let autonomous recovery handle this
            }
        }
    }

    private fun performGentleRestart() {
        try {
            // Minimal intervention restart
            handler.postDelayed({
                val accessibilityIntent = Intent(this, ClickerAccessibilityService::class.java)
                startService(accessibilityIntent)
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Gentle restart hatası: ${e.message}")
        }
    }

    private fun startForeground() {
        try {
            createNotificationChannel()
            val notification = createSilentNotification()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Foreground service başlatma hatası: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ADB Clicker Service",
                NotificationManager.IMPORTANCE_MIN // Minimal importance for silent operation
            ).apply {
                description = "Background service monitoring"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createSilentNotification(): Notification {
        val uptime = (System.currentTimeMillis() - serviceStartTime) / 1000
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("ADB Clicker")
                .setContentText("Aktif (${uptime}s)")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("ADB Clicker")
                .setContentText("Aktif (${uptime}s)")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN)
                .build()
        }
    }

    private fun startLightweightMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            Log.d(TAG, "Hafif izleme başlatıldı (Otonom kurtarma aktif)")
            scheduleLightCheck()
        }
    }

    private fun scheduleLightCheck() {
        if (isMonitoring.get()) {
            handler.postDelayed({
                performLightCheck()
                scheduleLightCheck()
            }, lightCheckInterval)
        }
    }

    private fun performLightCheck() {
        try {
            val currentTime = System.currentTimeMillis()
            totalUptime = currentTime - serviceStartTime
            
            // Update notification periodically
            updateNotificationSilently()
            
            // Save uptime statistics
            saveUptimeStats()
            
            // Log minimal stats (only every 10 minutes)
            if (totalUptime % 600000L < lightCheckInterval) {
                val uptimeMinutes = totalUptime / 60000L
                Log.d(TAG, "Uptime: ${uptimeMinutes} dakika (Otonom kurtarma aktif)")
            }
            
        } catch (e: Exception) {
            // Even light checks can fail, but autonomous recovery will handle issues
            Log.w(TAG, "Light check exception (autonomous recovery handling): ${e.message}")
        }
    }

    private fun updateNotificationSilently() {
        try {
            val notification = createSilentNotification()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Ignore notification update failures
        }
    }

    private fun saveUptimeStats() {
        try {
            prefs.edit()
                .putLong("total_uptime", totalUptime)
                .putLong("last_update", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // Ignore save failures
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ServiceMonitor yok ediliyor...")
        
        // Stop monitoring
        isMonitoring.set(false)
        
        // Stop autonomous recovery
        autonomousRecovery.stopRecovery()
        
        // Clean up
        handler.removeCallbacksAndMessages(null)
        instance = null
        
        // Save final stats
        val finalUptime = System.currentTimeMillis() - serviceStartTime
        prefs.edit()
            .putLong("last_session_uptime", finalUptime)
            .putLong("session_end_time", System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "ServiceMonitor session ended (Uptime: ${finalUptime / 1000}s)")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Handle low memory situations gracefully
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory situation - autonomous recovery will handle")
        
        // Trigger garbage collection
        System.gc()
        
        // The autonomous recovery system will detect any resulting issues
        // and handle them appropriately
    }

    // Handle task removal (when user swipes away from recent apps)
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed - service continues running")
        
        // Service continues running in background
        // Autonomous recovery will restart if needed
        
        // Restart the service to ensure it continues
        val restartIntent = Intent(applicationContext, ServiceMonitor::class.java)
        startService(restartIntent)
    }
} 