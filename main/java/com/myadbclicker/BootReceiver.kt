package com.myadbclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import android.os.Handler
import android.os.Looper

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
        private const val STARTUP_DELAY = 10000L // 10 seconds delay after boot
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "BootReceiver tetiklendi: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                handleBootCompleted(context)
            }
            else -> {
                // Ignore other actions
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "Sistem baþlatma tamamlandý - Servisler baþlatýlýyor")
        
        // Delay startup to ensure system is fully ready
        Handler(Looper.getMainLooper()).postDelayed({
            startServices(context)
        }, STARTUP_DELAY)
    }

    private fun startServices(context: Context) {
        try {
            Log.d(TAG, "Servisler baþlatýlýyor...")
            
            // Start ServiceMonitor first
            val serviceMonitorIntent = Intent(context, ServiceMonitor::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceMonitorIntent)
            } else {
                context.startService(serviceMonitorIntent)
            }
            
            Log.d(TAG, "ServiceMonitor baþlatýldý")
            
            // ServiceMonitor will handle starting the accessibility service
            // and the autonomous recovery system
            
        } catch (e: Exception) {
            Log.e(TAG, "Servis baþlatma hatasý: ${e.message}")
            
            // Retry after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val retryIntent = Intent(context, ServiceMonitor::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(retryIntent)
                    } else {
                        context.startService(retryIntent)
                    }
                    Log.d(TAG, "Servis baþlatma yeniden denendi")
                } catch (retryException: Exception) {
                    Log.e(TAG, "Yeniden deneme hatasý: ${retryException.message}")
                }
            }, 5000L)
        }
    }
}
