package com.example.screenlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        TrustedDevices.migrateLegacyIfNeeded(context)

        if (TrustedDevices.hasSelection(context)) {
            LogStore.append(
                context,
                "Телефон загружен: запускаю мониторинг доверенных Bluetooth-устройств"
            )

            ContextCompat.startForegroundService(
                context,
                Intent(context, BtMonitorService::class.java)
            )
        } else {
            LogStore.append(
                context,
                "Телефон загружен: мониторинг не запущен, доверенные устройства не выбраны"
            )
        }
    }
}
