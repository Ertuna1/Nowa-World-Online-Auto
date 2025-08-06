package com.myadbclicker

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.os.PowerManager
import android.content.SharedPreferences
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.os.SystemClock
import android.app.PendingIntent
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import java.io.File
import java.io.FileWriter
import android.os.Process

class AutonomousRecoverySystem(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("autonomous_recovery", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val isRecoveryActive = AtomicBoolean(false)
    
    // Recovery strategies (escalating approach)
    private var currentStrategy = RecoveryStrategy.GENTLE
    private var strategyAttempts = 0
    private val maxAttemptsPerStrategy = 3
    
    // Timing and backoff
    private var baseRecoveryInterval = 5000L // Start with 5 seconds
    private var currentRecoveryInterval = baseRecoveryInterval
    private val maxRecoveryInterval = 300000L // Max 5 minutes
    private val backoffMultiplier = 1.5
    
    // Health monitoring
    private var lastHealthyTime = System.currentTimeMillis()
    private var totalRecoveryAttempts = 0
    private var successfulRecoveries = 0
    
    // Silent operation flags
    private val isSilentMode = AtomicBoolean(true)
    private var isDeepRecoveryMode = AtomicBoolean(false)

    enum class RecoveryStrategy {
        GENTLE,         // Soft restart attempts
        MODERATE,       // Memory cleanup + restart
        AGGRESSIVE,     // Kill and restart
        NUCLEAR         // Full system reset
    }

    companion object {
        private const val TAG = "AutonomousRecovery"
        private const val HEALTH_CHECK_INTERVAL = 3000L // 3 seconds
        private const val DEEP_RECOVERY_THRESHOLD = 10 // attempts before going nuclear
        
        @Volatile
        private var instance: AutonomousRecoverySystem? = null
        
        fun getInstance(context: Context): AutonomousRecoverySystem {
            return instance ?: synchronized(this) {
                instance ?: AutonomousRecoverySystem(context).also { instance = it }
            }
        }
    }

    fun startAutonomousRecovery() {
        if (isRecoveryActive.compareAndSet(false, true)) {
            Log.d(TAG, "Otonom kurtarma sistemi başlatılıyor (Sessiz mod: $isSilentMode)")
            loadRecoveryState()
            startContinuousMonitoring()
            schedulePeriodicDeepCheck()
        }
    }

    private fun loadRecoveryState() {
        totalRecoveryAttempts = prefs.getInt("total_attempts", 0)
        successfulRecoveries = prefs.getInt("successful_recoveries", 0)
        lastHealthyTime = prefs.getLong("last_healthy_time", System.currentTimeMillis())
        
        // Determine initial strategy based on previous performance
        val successRate = if (totalRecoveryAttempts > 0) 
            (successfulRecoveries.toFloat() / totalRecoveryAttempts) else 1.0f
            
        currentStrategy = when {
            successRate > 0.8f -> RecoveryStrategy.GENTLE
            successRate > 0.5f -> RecoveryStrategy.MODERATE
            successRate > 0.2f -> RecoveryStrategy.AGGRESSIVE
            else -> RecoveryStrategy.NUCLEAR
        }
        
        if (totalRecoveryAttempts > 0) {
            Log.d(TAG, "Önceki performans: $successfulRecoveries/$totalRecoveryAttempts (${(successRate * 100).toInt()}%)")
        }
    }

    private fun startContinuousMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                if (isRecoveryActive.get()) {
                    performSilentHealthCheck()
                    handler.postDelayed(this, HEALTH_CHECK_INTERVAL)
                }
            }
        })
    }

    private fun performSilentHealthCheck() {
        try {
            val isServiceHealthy = checkServiceHealth()
            
            if (isServiceHealthy) {
                onServiceHealthy()
            } else {
                onServiceUnhealthy()
            }
        } catch (e: Exception) {
            // Even health checks can fail, handle silently
            Log.w(TAG, "Health check exception (handling silently): ${e.message}")
            triggerRecovery("health_check_exception")
        }
    }

    private fun checkServiceHealth(): Boolean {
        // Multi-layered health check
        val serviceRunning = isAccessibilityServiceEnabled()
        val serviceResponsive = isServiceResponsive()
        
        return serviceRunning && serviceResponsive
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC
            )
            
            enabledServices.any { service ->
                service.resolveInfo.serviceInfo.packageName == context.packageName &&
                service.resolveInfo.serviceInfo.name == ClickerAccessibilityService::class.java.name
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isServiceResponsive(): Boolean {
        // Test if service can handle a simple request
        return try {
            // Simple responsiveness test - check if accessibility service is enabled
            isAccessibilityServiceEnabled()
        } catch (e: Exception) {
            false
        }
    }

    private fun onServiceHealthy() {
        lastHealthyTime = System.currentTimeMillis()
        
        // Reset recovery parameters on sustained health
        if (currentRecoveryInterval > baseRecoveryInterval) {
            currentRecoveryInterval = (currentRecoveryInterval / backoffMultiplier).toLong()
            currentRecoveryInterval = maxOf(currentRecoveryInterval, baseRecoveryInterval)
        }
        
        if (strategyAttempts > 0) {
            strategyAttempts = 0
            if (currentStrategy != RecoveryStrategy.GENTLE) {
                // Gradually step down strategy
                currentStrategy = when (currentStrategy) {
                    RecoveryStrategy.NUCLEAR -> RecoveryStrategy.AGGRESSIVE
                    RecoveryStrategy.AGGRESSIVE -> RecoveryStrategy.MODERATE
                    RecoveryStrategy.MODERATE -> RecoveryStrategy.GENTLE
                    RecoveryStrategy.GENTLE -> RecoveryStrategy.GENTLE
                }
            }
        }
    }

    private fun onServiceUnhealthy() {
        val unhealthyDuration = System.currentTimeMillis() - lastHealthyTime
        
        // Immediate recovery for critical situations
        if (unhealthyDuration > 30000L) { // 30 seconds unhealthy
            triggerRecovery("prolonged_unhealthy_state")
        }
    }

    private fun triggerRecovery(reason: String) {
        if (!isRecoveryActive.get()) return
        
        totalRecoveryAttempts++
        strategyAttempts++
        
        Log.w(TAG, "Kurtarma tetiklendi: $reason (Strateji: $currentStrategy, Deneme: $strategyAttempts)")
        
        // Execute recovery strategy
        val recoverySuccess = executeRecoveryStrategy(reason)
        
        if (recoverySuccess) {
            onRecoverySuccess()
        } else {
            onRecoveryFailure()
        }
        
        // Save state
        saveRecoveryState()
        
        // Schedule next check with backoff
        scheduleNextRecoveryCheck()
    }

    private fun executeRecoveryStrategy(reason: String): Boolean {
        return try {
            when (currentStrategy) {
                RecoveryStrategy.GENTLE -> executeGentleRecovery()
                RecoveryStrategy.MODERATE -> executeModerateRecovery()
                RecoveryStrategy.AGGRESSIVE -> executeAggressiveRecovery()
                RecoveryStrategy.NUCLEAR -> executeNuclearRecovery()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recovery execution failed: ${e.message}")
            false
        }
    }

    private fun executeGentleRecovery(): Boolean {
        // Try to restart service gracefully
        try {
            val restartIntent = Intent(context, ServiceMonitor::class.java)
            restartIntent.putExtra("gentle_restart", true)
            context.startService(restartIntent)
            
            // Wait and check
            Thread.sleep(2000)
            return checkServiceHealth()
            
        } catch (e: Exception) {
            return false
        }
    }

    private fun executeModerateRecovery(): Boolean {
        try {
            // Force garbage collection
            System.gc()
            
            // Stop and restart ServiceMonitor
            val stopIntent = Intent(context, ServiceMonitor::class.java)
            context.stopService(stopIntent)
            
            Thread.sleep(1000)
            
            val startIntent = Intent(context, ServiceMonitor::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
            
            Thread.sleep(3000)
            return checkServiceHealth()
            
        } catch (e: Exception) {
            return false
        }
    }

    private fun executeAggressiveRecovery(): Boolean {
        try {
            // Force stop all related services
            try {
                context.stopService(Intent(context, ServiceMonitor::class.java))
                context.stopService(Intent(context, ClickerAccessibilityService::class.java))
            } catch (e: Exception) {
                // Ignore stop errors
            }
            
            // Clear memory
            System.gc()
            Runtime.getRuntime().gc()
            
            Thread.sleep(2000)
            
            // Restart everything
            val bootIntent = Intent(context, BootReceiver::class.java)
            bootIntent.action = Intent.ACTION_BOOT_COMPLETED
            context.sendBroadcast(bootIntent)
            
            Thread.sleep(5000)
            return checkServiceHealth()
            
        } catch (e: Exception) {
            return false
        }
    }

    private fun executeNuclearRecovery(): Boolean {
        try {
            Log.w(TAG, "Nükleer kurtarma modu - Tüm sistemi sıfırlıyor")
            
            // Deep clean everything
            isDeepRecoveryMode.set(true)
            
            // Force stop all services multiple times
            repeat(3) {
                try {
                    context.stopService(Intent(context, ServiceMonitor::class.java))
                    context.stopService(Intent(context, ClickerAccessibilityService::class.java))
                    Thread.sleep(500)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            // Aggressive memory cleanup
            System.gc()
            Runtime.getRuntime().gc()
            
            // Wait longer for system to settle
            Thread.sleep(5000)
            
            // Schedule delayed restart with alarm manager
            scheduleDelayedRestart()
            
            Thread.sleep(10000)
            val recovered = checkServiceHealth()
            
            isDeepRecoveryMode.set(false)
            return recovered
            
        } catch (e: Exception) {
            isDeepRecoveryMode.set(false)
            return false
        }
    }

    private fun scheduleDelayedRestart() {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DelayedRestartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                Random.nextInt(), 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            
            val triggerTime = SystemClock.elapsedRealtime() + 3000 // 3 seconds
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Delayed restart scheduling failed: ${e.message}")
        }
    }

    private fun onRecoverySuccess() {
        successfulRecoveries++
        strategyAttempts = 0
        currentRecoveryInterval = baseRecoveryInterval
        lastHealthyTime = System.currentTimeMillis()
        
        Log.i(TAG, "Kurtarma başarılı! (Toplam: $successfulRecoveries/$totalRecoveryAttempts)")
    }

    private fun onRecoveryFailure() {
        // Escalate strategy if max attempts reached
        if (strategyAttempts >= maxAttemptsPerStrategy) {
            escalateRecoveryStrategy()
            strategyAttempts = 0
        }
        
        // Increase recovery interval (backoff)
        currentRecoveryInterval = (currentRecoveryInterval * backoffMultiplier).toLong()
        currentRecoveryInterval = minOf(currentRecoveryInterval, maxRecoveryInterval)
        
        Log.w(TAG, "Kurtarma başarısız (Sonraki deneme: ${currentRecoveryInterval}ms)")
    }

    private fun escalateRecoveryStrategy() {
        currentStrategy = when (currentStrategy) {
            RecoveryStrategy.GENTLE -> RecoveryStrategy.MODERATE
            RecoveryStrategy.MODERATE -> RecoveryStrategy.AGGRESSIVE
            RecoveryStrategy.AGGRESSIVE -> RecoveryStrategy.NUCLEAR
            RecoveryStrategy.NUCLEAR -> RecoveryStrategy.NUCLEAR // Stay at maximum
        }
        
        Log.w(TAG, "Kurtarma stratejisi yükseltildi: $currentStrategy")
    }

    private fun scheduleNextRecoveryCheck() {
        handler.postDelayed({
            if (isRecoveryActive.get()) {
                performSilentHealthCheck()
            }
        }, currentRecoveryInterval)
    }

    private fun schedulePeriodicDeepCheck() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                performDeepSystemCheck()
                if (isRecoveryActive.get()) {
                    handler.postDelayed(this, 60000L) // Every minute
                }
            }
        }, 60000L)
    }

    private fun performDeepSystemCheck() {
        try {
            // Check for memory leaks, thread issues, etc.
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = (usedMemory.toFloat() / maxMemory * 100).toInt()
            
            if (memoryUsage > 85) {
                Log.w(TAG, "Yüksek bellek kullanımı tespit edildi: %$memoryUsage")
                System.gc()
            }
            
            // Check service age
            val currentTime = System.currentTimeMillis()
            val unhealthyDuration = currentTime - lastHealthyTime
            
            if (unhealthyDuration > 300000L) { // 5 minutes unhealthy
                Log.w(TAG, "Uzun süreli sağlıksız durum tespit edildi")
                triggerRecovery("deep_check_prolonged_unhealthy")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Deep check failed: ${e.message}")
        }
    }

    private fun saveRecoveryState() {
        prefs.edit()
            .putInt("total_attempts", totalRecoveryAttempts)
            .putInt("successful_recoveries", successfulRecoveries)
            .putLong("last_healthy_time", lastHealthyTime)
            .apply()
    }

    fun stopRecovery() {
        if (isRecoveryActive.compareAndSet(true, false)) {
            handler.removeCallbacksAndMessages(null)
            saveRecoveryState()
            Log.d(TAG, "Otonom kurtarma sistemi durduruldu")
        }
    }

    // Delayed restart receiver for nuclear recovery
    class DelayedRestartReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Delayed restart tetiklendi")
            
            try {
                val bootIntent = Intent(context, BootReceiver::class.java)
                bootIntent.action = Intent.ACTION_BOOT_COMPLETED
                context.sendBroadcast(bootIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Delayed restart failed: ${e.message}")
            }
        }
    }
} 