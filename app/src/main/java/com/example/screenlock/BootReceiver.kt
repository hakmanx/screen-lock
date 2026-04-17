package com.example.screenlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("bt_lock_prefs", Context.MODE_PRIVATE)
            val mac = prefs.getString("target_mac", null)
            if (mac != null) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BtMonitorService::class.java)
                )
            }
        }
    }
}
