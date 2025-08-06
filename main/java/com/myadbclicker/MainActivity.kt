package com.myadbclicker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val enableServiceButton: Button = findViewById(R.id.enable_service_button)
        enableServiceButton.setOnClickListener {
            // Kullanıcıyı Erişilebilirlik Ayarları'na yönlendir
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Lütfen 'My ADB Clicker' hizmetini etkinleştirin.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Erişilebilirlik servisi etkinse ServiceMonitor'u başlat
        if (isAccessibilityServiceEnabled()) {
            startServiceMonitor()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        
        return enabledServices.any { service ->
            service.resolveInfo.serviceInfo.packageName == packageName &&
            service.resolveInfo.serviceInfo.name == ClickerAccessibilityService::class.java.name
        }
    }

    private fun startServiceMonitor() {
        val intent = Intent(this, ServiceMonitor::class.java)
        startService(intent)
    }
}
